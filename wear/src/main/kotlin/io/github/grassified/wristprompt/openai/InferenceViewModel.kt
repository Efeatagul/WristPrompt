package io.github.grassified.wristprompt.openai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.storage.SecureStorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ProofStage { IDLE, LOADING, READY, STREAMING, SUCCEEDED, OUTPUT_MISMATCH, FAILED, CANCELLED }
data class ProofState(
    val stage: ProofStage = ProofStage.IDLE, val models: List<AiModel> = emptyList(),
    val selectedSlug: String? = null, val output: String = "", val completedObserved: Boolean = false,
    val problem: ApiProblem? = null, val usageHint: Boolean = false,
    val protocolDetail: ProtocolDetail? = null, val httpStatus: Int? = null,
    val errorCode: String? = null, val requestId: String? = null, val errorShape: ErrorShape? = null,
    val mode: CredentialMode = CredentialMode.CHATGPT_PLAN,
) {
    val busy: Boolean get() = stage == ProofStage.LOADING || stage == ProofStage.STREAMING
}

class InferenceViewModel(
    accounts: StateFlow<InferenceAccessState>,
    private val session: InferenceSession,
    private val selectedModel: suspend (CredentialLease) -> String? = { null },
    private val saveModel: suspend (CredentialLease, String) -> Unit = { _, _ -> },
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProofState())
    val state = mutableState.asStateFlow()
    private val conversation = ChatConversation()
    private var inputRevision = 0L
    private var contextModel: String? = null
    private val mutableChat = MutableStateFlow(conversation.state)
    val chat = mutableChat.asStateFlow()
    private var prepared: PreparedCatalog? = null
    private var operation: Job? = null
    @Volatile private var epoch = 0L
    init {
        viewModelScope.launch {
            accounts.distinctUntilChanged { old, next -> old == next }.collect { account ->
                cancelOperation()
                prepared = null
                clearChat()
                mutableState.value = ProofState(problem = account.problem, mode = account.mode)
                if (account.usable) loadModels()
            }
        }
    }
    fun select(slug: String) {
        if (operation?.isActive == true || state.value.busy || state.value.models.none { it.slug == slug }) return
        val catalog = prepared ?: return
        val run = epoch
        launchOperation {
            try {
                saveModel(catalog.credential, slug)
                if (run == epoch) {
                    if (state.value.selectedSlug != slug) clearChat()
                    mutableState.value = state.value.copy(selectedSlug = slug, output = "", completedObserved = false,
                        stage = if (state.value.stage == ProofStage.FAILED) ProofStage.FAILED else ProofStage.READY)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun loadModels() {
        if (operation?.isActive == true) return
        val priorModel = contextModel ?: state.value.selectedSlug
        val run = ++epoch
        mutableState.value = ProofState(stage = ProofStage.LOADING, mode = state.value.mode)
        launchOperation {
            try {
                val catalog = session.catalog()
                if (run != epoch) return@launchOperation
                val saved = selectedModel(catalog.credential)
                if (run != epoch) return@launchOperation
                prepared = catalog
                val restored = catalog.models.firstOrNull { it.slug == saved }?.slug ?: catalog.models.first().slug
                if (priorModel != null && priorModel != restored) clearChat()
                mutableState.value = ProofState(ProofStage.READY, catalog.models,
                    restored, mode = catalog.credential.mode)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun test() {
        if (operation?.isActive == true) return
        val catalog = prepared ?: return
        val selected = state.value.selectedSlug ?: return
        val run = ++epoch
        mutableState.value = state.value.copy(stage = ProofStage.LOADING, output = "", completedObserved = false, problem = null, protocolDetail = null, httpStatus = null, errorCode = null, requestId = null, errorShape = null)
        launchOperation {
            try {
                val result = session.test(catalog, selected, onCatalog = { fresh ->
                    if (run == epoch) {
                        prepared = fresh
                        mutableState.value = state.value.copy(models = fresh.models)
                    }
                }, onProgress = { result ->
                    if (run == epoch) mutableState.update { it.copy(stage = ProofStage.STREAMING,
                        output = result.output, completedObserved = result.completed) }
                })
                if (run == epoch) mutableState.update { it.copy(
                    stage = if (result.proofPassed) ProofStage.SUCCEEDED else ProofStage.OUTPUT_MISMATCH,
                    output = result.output, completedObserved = result.completed,
                ) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun stop() {
        val wasChatBusy = conversation.state.busy
        if (!wasChatBusy && !state.value.busy) return
        cancelOperation()
        if (wasChatBusy) { conversation.fail(null, cancelled = true); publishChat() }
        if (state.value.busy) mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
    // Input results are accepted only for the context that launched the platform composer.
    fun inputToken(): Long? = inputRevision.takeIf { !state.value.busy && !chat.value.busy }
    fun draft(text: String, token: Long = inputRevision) {
        if (token != inputRevision || state.value.busy || chat.value.busy) return
        if (contextModel == null) contextModel = state.value.selectedSlug
        conversation.draft(text); publishChat()
    }
    fun newChat() {
        cancelOperation()
        clearChat()
        if (state.value.busy) mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
    fun send() {
        if (operation?.isActive == true || state.value.busy) return
        val catalog = prepared ?: return
        val selected = state.value.selectedSlug ?: return
        contextModel = selected
        val input = conversation.begin()
        publishChat()
        if (input == null) return
        val run = ++epoch
        inputRevision++
        launchOperation {
            try {
                val result = session.chat(catalog, selected, input) { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        if (run == epoch) { conversation.progress(progress.output); publishChat() }
                    }
                }
                if (run == epoch) { conversation.complete(result); publishChat() }
            } catch (cancelled: CancellationException) {
                if (run == epoch) { conversation.fail(null, cancelled = true); publishChat() }
                throw cancelled
            } catch (failure: Exception) {
                if (run == epoch) {
                    conversation.fail(when (failure) {
                        is ApiFailure -> failure.problem
                        is SecureStorageException -> ApiProblem.STORAGE
                        else -> ApiProblem.PROTOCOL
                    })
                    publishChat()
                }
            }
        }
    }
    private fun publishChat() { mutableChat.value = conversation.state }
    private fun clearChat() { inputRevision++; contextModel = null; conversation.reset(); publishChat() }
    // Future phone-browser hook; never substitute an API-key billing path.
    fun manageUsage() { mutableState.update { it.copy(usageHint = true) } }
    // Publish ownership before executing a Main.immediate coroutine that may finish/re-enter synchronously.
    private fun launchOperation(block: suspend () -> Unit) {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { block() }
        operation = job
        job.start()
    }
    private fun cancelOperation() { epoch++; operation?.cancel(); operation = null }
    private fun failed(run: Long, failure: Exception) {
        if (run != epoch) return
        val problem = when (failure) {
            is ApiFailure -> failure.problem
            is SecureStorageException -> ApiProblem.STORAGE
            else -> ApiProblem.PROTOCOL
        }
        mutableState.update { it.copy(stage = ProofStage.FAILED, problem = problem,
            protocolDetail = (failure as? ApiFailure)?.protocolDetail
                ?: ProtocolDetail.CLIENT_RESPONSE.takeIf { problem == ApiProblem.PROTOCOL },
            httpStatus = (failure as? ApiFailure)?.status?.takeIf { status -> status in 100..599 },
            errorCode = (failure as? ApiFailure)?.code, requestId = (failure as? ApiFailure)?.requestId,
            errorShape = (failure as? ApiFailure)?.shape) }
    }
}
