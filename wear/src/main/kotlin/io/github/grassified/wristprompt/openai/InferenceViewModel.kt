package io.github.grassified.wristprompt.openai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.WatchAccountState
import io.github.grassified.wristprompt.storage.SecureStorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ProofStage { IDLE, LOADING, READY, STREAMING, SUCCEEDED, OUTPUT_MISMATCH, FAILED, CANCELLED }
data class ProofState(
    val stage: ProofStage = ProofStage.IDLE, val models: List<AiModel> = emptyList(),
    val selectedSlug: String? = null, val output: String = "", val completedObserved: Boolean = false,
    val problem: ApiProblem? = null, val usageHint: Boolean = false,
    val protocolDetail: ProtocolDetail? = null, val httpStatus: Int? = null,
) {
    val busy: Boolean get() = stage == ProofStage.LOADING || stage == ProofStage.STREAMING
}

private data class ActiveAccount(val client: String?, val generation: Long, val plan: Boolean, val pending: Boolean, val storageError: Boolean)

class InferenceViewModel(
    accounts: StateFlow<WatchAccountState>,
    private val session: InferenceSession,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProofState())
    val state = mutableState.asStateFlow()
    private var prepared: PreparedCatalog? = null
    private var operation: Job? = null
    @Volatile private var epoch = 0L
    init {
        viewModelScope.launch {
            accounts.map { ActiveAccount(it.activeClientId, it.generation, it.planEnabled,
                it.pendingExpiresAt?.let { expiry -> expiry >= System.currentTimeMillis() } == true, it.storageError) }
                .distinctUntilChanged().collect { account ->
                    cancelOperation()
                    prepared = null
                    mutableState.value = ProofState(problem = when {
                        account.storageError -> ApiProblem.STORAGE
                        accounts.value.reauthorizationRequired -> ApiProblem.REAUTHORIZE
                        account.pending -> ApiProblem.REAUTHORIZING
                        account.client != null && !account.plan -> ApiProblem.PLAN_DISABLED
                        else -> null
                    })
                    if (account.client != null && account.plan && !account.pending && !account.storageError) loadModels()
                }
        }
    }
    fun select(slug: String) {
        if (!state.value.busy && state.value.models.any { it.slug == slug }) {
            mutableState.value = state.value.copy(selectedSlug = slug, output = "", completedObserved = false,
                stage = if (state.value.stage == ProofStage.FAILED) ProofStage.FAILED else ProofStage.READY)
        }
    }
    fun loadModels() {
        if (operation?.isActive == true) return
        val run = ++epoch
        mutableState.value = ProofState(stage = ProofStage.LOADING)
        operation = viewModelScope.launch {
            try {
                val catalog = session.catalog()
                if (run != epoch) return@launch
                prepared = catalog
                mutableState.value = ProofState(ProofStage.READY, catalog.models, catalog.models.first().slug)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { failed(run, failure) }
        }
    }
    fun test() {
        if (operation?.isActive == true) return
        val catalog = prepared ?: return
        val selected = state.value.selectedSlug ?: return
        val run = ++epoch
        mutableState.value = state.value.copy(stage = ProofStage.LOADING, output = "", completedObserved = false, problem = null, protocolDetail = null, httpStatus = null)
        operation = viewModelScope.launch {
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
        cancelOperation()
        mutableState.value = state.value.copy(stage = ProofStage.CANCELLED)
    }
    // Future phone-browser hook; never substitute an API-key billing path.
    fun manageUsage() { mutableState.update { it.copy(usageHint = true) } }
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
            httpStatus = (failure as? ApiFailure)?.status?.takeIf { status -> status in 100..599 }) }
    }
}
