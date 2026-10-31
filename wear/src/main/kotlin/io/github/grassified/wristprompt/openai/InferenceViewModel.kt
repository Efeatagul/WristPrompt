package io.github.grassified.wristprompt.openai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.storage.SecureStorageException
import io.github.grassified.wristprompt.storage.*
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
    val attemptId: Long? = null,
) {
    val busy: Boolean get() = stage == ProofStage.LOADING || stage == ProofStage.STREAMING
    override fun toString() = io.github.grassified.wristprompt.bridge.SafeDiagnostics.REDACTED
}

class InferenceViewModel(
    private val accounts: StateFlow<InferenceAccessState>,
    private val session: InferenceSession,
    private val selectedModel: suspend (CredentialLease) -> String? = { null },
    private val saveModel: suspend (CredentialLease, String) -> Unit = { _, _ -> },
    private val conversationStore: ConversationStore? = null,
    private val conversationIdentity: suspend (CredentialLease) -> String = { throw SecureStorageException() },
    private val localConversationIdentity: (suspend (InferenceAccessState) -> String)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProofState())
    val state = mutableState.asStateFlow()
    private val conversation = ChatConversation()
    private var inputRevision = 0L
    private var observedAccess = accounts.value
    private var contextModel: String? = null
    private val mutableChat = MutableStateFlow(conversation.state)
    val chat = mutableChat.asStateFlow()
    private var prepared: PreparedCatalog? = null
    private var operation: Job? = null
    @Volatile private var epoch = 0L
    private var cleared = false
    private var ownerScope: String? = null
    private var writer: ConversationWriter? = null
    private val mutableHistory = MutableStateFlow(ConversationHistory())
    val history = mutableHistory.asStateFlow()
    private var restoreOnStartup = true
    init {
        viewModelScope.launch {
            accounts.distinctUntilChanged { old, next -> old == next }.collect { account ->
                observedAccess = account
                cancelOperation()
                prepared = null
                ownerScope = null
                mutableHistory.value = ConversationHistory()
                clearChat()
                mutableState.value = ProofState(problem = account.problem, mode = account.mode)
                if (account.usable) loadModels()
            }
        }
    }
    fun select(slug: String) {
        if (operation?.isActive == true || state.value.busy || state.value.models.none { it.slug == slug }) return
        val catalog = prepared?.takeIf(::compatible) ?: return
        val run = epoch
        launchOperation {
            try {
                saveModel(catalog.credential, slug)
                if (run == epoch) {
                    if (state.value.selectedSlug != slug) clearChat()
                    mutableState.value = state.value.copy(selectedSlug = slug, output = "", completedObserved = false, attemptId = null,
                        stage = if (state.value.stage == ProofStage.FAILED) ProofStage.FAILED else ProofStage.READY)
                }
            } catch (cancelled: CancellationException) { cancelled(run); throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun loadModels() {
        if (operation?.isActive == true || !accounts.value.usable) return
        val priorModel = contextModel ?: state.value.selectedSlug
        val run = ++epoch
        prepared = null
        mutableState.value = ProofState(stage = ProofStage.LOADING, mode = state.value.mode)
        launchOperation {
            try {
                if (conversationStore != null && localConversationIdentity != null) {
                    val access = accounts.value
                    val localOwner = conversationStore.owner(localConversationIdentity.invoke(access))
                    if (run != epoch || accounts.value != access) return@launchOperation
                    resolveHistory(localOwner, run)
                }
                val catalog = session.catalog()
                if (run != epoch) return@launchOperation
                val saved = selectedModel(catalog.credential)
                if (run != epoch) return@launchOperation
                session.validate(catalog)
                if (run != epoch || !compatible(catalog)) return@launchOperation
                prepared = catalog
                val restored = catalog.models.firstOrNull { it.slug == saved }?.slug ?: catalog.models.first().slug
                val owner = conversationStore?.owner(conversationIdentity(catalog.credential))
                if (run != epoch || !compatible(catalog)) return@launchOperation
                ownerScope = owner
                if (owner != null) resolveHistory(owner, run)
                if (priorModel != null && priorModel != restored) clearChat()
                mutableState.value = ProofState(ProofStage.READY, catalog.models,
                    restored, mode = catalog.credential.mode)
                writer?.entry?.let { entry ->
                    conversation.readOnly(if (entry.owner == owner && entry.model == restored && entry.route == catalog.credential.mode.name) null
                        else "Read-only chat. Select its original model and reopen it, or start a new chat.")
                    publishChat()
                }
            } catch (cancelled: CancellationException) { cancelled(run); throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun test() {
        if (operation?.isActive == true) return
        val catalog = prepared?.takeIf(::compatible) ?: return
        val selected = state.value.selectedSlug?.takeIf { slug -> catalog.models.any { it.slug == slug } } ?: return
        val run = ++epoch
        mutableState.value = state.value.copy(stage = ProofStage.LOADING, output = "", completedObserved = false, attemptId = run, problem = null, protocolDetail = null, httpStatus = null, errorCode = null, requestId = null, errorShape = null)
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
            } catch (cancelled: CancellationException) { cancelled(run); throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun stop() {
        val wasChatBusy = conversation.state.busy
        if (!wasChatBusy && !state.value.busy) return
        cancelOperation()
        if (wasChatBusy) { conversation.fail(null, cancelled = true); publishChat() }
        if (wasChatBusy) persistTerminal()
        if (state.value.busy) mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
    // Input results are accepted only for the context that launched the platform composer.
    fun inputToken(): Long? = inputRevision.takeIf { !cleared && accounts.value == observedAccess && !state.value.busy && !chat.value.busy }
    fun draft(text: String, token: Long = inputRevision) {
        if (cleared || token != inputRevision || state.value.busy || chat.value.busy) return
        if (contextModel == null) contextModel = state.value.selectedSlug
        conversation.draft(text); publishChat()
    }
    fun inputResult(text: String, token: Long) {
        if (cleared || token != inputRevision || accounts.value != observedAccess) return
        draft(text, token)
        inputRevision++ // Consume once even if an external result is delivered again.
    }
    fun newChat() {
        restoreOnStartup = false
        mutableHistory.value = history.value.copy(loading = false, error = null)
        cancelOperation()
        clearChat()
        if (state.value.busy) mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
    private suspend fun resolveHistory(owner: String, run: Long) {
        val store = conversationStore ?: return
        writer?.awaitIdle()
        val entries = store.list(owner)
        if (run != epoch) return
        ownerScope = owner
        mutableHistory.value = ConversationHistory(entries)
        if (restoreOnStartup) {
            restoreOnStartup = false
            val latest = entries.firstOrNull()?.let { store.load(it.id, owner) }
            if (run != epoch || ownerScope != owner) return
            if (latest != null) {
                conversation.restore(latest.snapshot)
                conversation.readOnly("Stored chat. Load its original model to continue, or start a new chat.")
                writer = ConversationWriter(store, latest)
                publishChat()
            }
        }
    }
    fun refreshHistory() {
        if (chat.value.busy || operation?.isActive == true) {
            mutableHistory.value = history.value.copy(error = "Finish or stop the current operation first."); return
        }
        val owner = ownerScope ?: return
        val run = epoch
        mutableHistory.value = history.value.copy(loading = true, error = null)
        launchOperation {
            try { resolveHistory(owner, run) }
            catch (_: Exception) { if (run == epoch) mutableHistory.value = history.value.copy(loading = false, error = "Chats could not be loaded. Saved data has not been reset.") }
        }
    }
    fun openChat(id: String, onOpened: () -> Unit = {}) {
        if (chat.value.busy || operation?.isActive == true || cleared) return
        val store = conversationStore ?: return
        val owner = ownerScope ?: return
        val run = ++epoch
        inputRevision++; restoreOnStartup = false
        mutableHistory.value = history.value.copy(loading = true, error = null)
        launchOperation {
            try {
                writer?.awaitIdle()
                val entry = store.load(id, owner) ?: throw ConversationStorageException()
                if (run != epoch || ownerScope != owner || accounts.value != observedAccess) return@launchOperation
                conversation.restore(entry.snapshot)
                val current = prepared?.takeIf(::compatible)
                val canContinue = current != null && entry.route == current.credential.mode.name && entry.model == state.value.selectedSlug
                conversation.readOnly(if (canContinue) null else "Read-only chat. Select its original model and reopen it, or start a new chat.")
                writer = ConversationWriter(store, entry); contextModel = entry.model
                publishChat(); mutableHistory.value = history.value.copy(loading = false, error = null)
                onOpened()
            } catch (_: Exception) { if (run == epoch) mutableHistory.value = history.value.copy(loading = false, error = "Chat could not be opened. Saved data has not been reset.") }
        }
    }
    fun renameChat(id: String, title: String) = editHistory(id, title)
    fun deleteChat(id: String) = editHistory(id, null)
    private fun editHistory(id: String, title: String?) {
        if (chat.value.busy || operation?.isActive == true || cleared) return
        val store = conversationStore ?: return
        val owner = ownerScope ?: return
        val run = ++epoch
        mutableHistory.value = history.value.copy(loading = true, error = null)
        launchOperation {
            try {
                writer?.awaitIdle()
                val entry = store.load(id, owner) ?: throw ConversationStorageException()
                if (run != epoch || ownerScope != owner || accounts.value != observedAccess) return@launchOperation
                if (title == null) {
                    store.delete(id, owner, entry.revision)
                    if (run == epoch && writer?.entry?.id == id) { clearChat(); restoreOnStartup = false }
                } else {
                    require(title.isNotBlank() && title.length <= 120)
                    store.rename(id, owner, entry.revision, title)
                    if (run == epoch && writer?.entry?.id == id) writer = ConversationWriter(store, checkNotNull(store.load(id, owner)))
                }
                if (run == epoch) resolveHistory(owner, run)
            } catch (_: Exception) { if (run == epoch) mutableHistory.value = history.value.copy(loading = false,
                error = if (title == null) "Chat could not be deleted. Saved data has not been reset."
                    else "Name could not be saved. Use a non-empty name up to 120 characters, or try again.") }
        }
    }
    fun send() {
        if (operation?.isActive == true || state.value.busy) return
        val catalog = prepared?.takeIf(::compatible) ?: return
        val selected = state.value.selectedSlug?.takeIf { slug -> catalog.models.any { it.slug == slug } } ?: return
        contextModel = selected
        if (!conversation.state.canSend) return
        val title = conversation.state.draft.take(120).ifBlank { "Chat" }
        if (conversationStore != null && writer == null) {
            val owner = ownerScope ?: return
            writer = ConversationWriter(conversationStore, ConversationEntry(owner = owner, route = catalog.credential.mode.name,
                model = selected, title = title, updated = System.currentTimeMillis(), snapshot = conversation.snapshot()))
        }
        val input = conversation.begin()
        publishChat()
        if (input == null) return
        val run = ++epoch
        val activeWriter = writer
        val started = conversation.snapshot()
        inputRevision++
        launchOperation {
            try {
                activeWriter?.write(started)
                currentCoroutineContext().ensureActive()
                if (run != epoch || !compatible(catalog)) return@launchOperation
                val result = session.chat(catalog, selected, input) { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        if (run == epoch) { conversation.progress(progress.output); publishChat() }
                    }
                }
                if (run == epoch) {
                    // Keep visible streaming text for in-memory rollback only; busy durable snapshots omit it.
                    val beforeCompletion = conversation.snapshot().copy(partial = conversation.state.partial)
                    // The completed-event boundary wins over a later Stop. Publish only after durable commit.
                    conversation.complete(result)
                    try { activeWriter?.write(conversation.snapshot()) }
                    catch (failure: Exception) {
                        if (run == epoch) { conversation.restore(beforeCompletion); conversation.fail(ApiProblem.CHAT_STORAGE); publishChat() }
                        throw failure
                    }
                    if (run == epoch) publishChat()
                }
            } catch (cancelled: CancellationException) {
                if (run == epoch) { conversation.fail(null, cancelled = true); publishChat(); persistTerminal() }
                throw cancelled
            } catch (failure: Exception) {
                if (run == epoch) {
                    val problem = when (failure) {
                        is ApiFailure -> failure.problem
                        is SecureStorageException -> ApiProblem.STORAGE
                        is ConversationStorageException -> ApiProblem.CHAT_STORAGE
                        else -> ApiProblem.PROTOCOL
                    }
                    invalidateModel(problem)
                    conversation.fail(problem)
                    publishChat()
                    if (problem != ApiProblem.CHAT_STORAGE) persistTerminal()
                }
            }
        }
    }
    // The StateFlow may change before its collector runs. Never dispatch against that old snapshot.
    private fun compatible(catalog: PreparedCatalog): Boolean = accounts.value.let { access ->
        access.usable && access.client == catalog.credential.clientId &&
            access.generation == catalog.credential.generation && access.mode == catalog.credential.mode &&
            access.modeRevision == catalog.credential.modeRevision
    }
    private fun cancelled(run: Long) {
        if (run == epoch && state.value.busy) mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
    private fun invalidateModel(problem: ApiProblem) {
        if (problem in setOf(ApiProblem.NO_MODELS, ApiProblem.STALE_ACCOUNT, ApiProblem.REAUTHORIZE,
                ApiProblem.REAUTHORIZING, ApiProblem.API_KEY_MISSING, ApiProblem.STORAGE)) {
            if (problem != ApiProblem.NO_MODELS) clearChat()
            prepared = null
            mutableState.value = ProofState(stage = ProofStage.FAILED, problem = problem, mode = state.value.mode)
        }
    }
    private fun publishChat() { mutableChat.value = conversation.state }
    private fun clearChat() { inputRevision++; contextModel = null; writer = null; conversation.reset(); publishChat() }
    private fun persistTerminal() {
        val activeWriter = writer ?: return
        val snapshot = conversation.snapshot()
        val run = epoch
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { activeWriter.write(snapshot) }
            catch (_: Exception) { if (run == epoch) mutableChat.value = mutableChat.value.copy(problem = ApiProblem.CHAT_STORAGE) }
        }
    }
    override fun onCleared() {
        // Final owner destruction is distinct from stopping UI observation or recreation.
        // Invalidate callbacks synchronously, before cancelled IO children finish unwinding.
        cleared = true
        cancelOperation()
        inputRevision++
        if (conversation.state.busy) { conversation.fail(null, cancelled = true); publishChat(); persistTerminal() }
        if (state.value.busy) mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
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
            is ConversationStorageException -> ApiProblem.CHAT_STORAGE
            else -> ApiProblem.PROTOCOL
        }
        invalidateModel(problem)
        mutableState.update { it.copy(stage = ProofStage.FAILED, problem = problem,
            protocolDetail = (failure as? ApiFailure)?.protocolDetail
                ?: ProtocolDetail.CLIENT_RESPONSE.takeIf { problem == ApiProblem.PROTOCOL },
            httpStatus = (failure as? ApiFailure)?.status?.takeIf { status -> status in 100..599 },
            errorCode = (failure as? ApiFailure)?.code, requestId = (failure as? ApiFailure)?.requestId,
            errorShape = (failure as? ApiFailure)?.shape) }
    }
}
