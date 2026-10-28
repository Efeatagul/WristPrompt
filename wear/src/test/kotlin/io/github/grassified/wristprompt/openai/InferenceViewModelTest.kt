package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceViewModelTest {
    private var model: InferenceViewModel? = null
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { model?.viewModelScope?.cancel(); Dispatchers.resetMain() }
    private fun accounts() = MutableStateFlow(InferenceAccessState(client = "client", usable = true))
    private class Provider : AiProvider {
        var stream: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { emit -> emit(ResponseEvent.Failed(ApiFailure(ApiProblem.USAGE_LIMIT))) }
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("slug", "Model"))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = stream(onEvent)
    }
    @Test fun catalogFailureCategoryReachesUiWithoutPayload() = runTest {
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> =
                throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = ProtocolDetail.CATALOG_MODELS)
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val vm = InferenceViewModel(accounts(), InferenceSession({ CredentialLease("client", 0, "synthetic", 999) }, provider) { true }).also { model = it }
        runCurrent()
        assertEquals(ProofStage.FAILED, vm.state.value.stage)
        assertEquals(ProtocolDetail.CATALOG_MODELS, vm.state.value.protocolDetail)
        assertNull(vm.state.value.selectedSlug)
    }
    @Test fun streamFailureShowsSafeCategoryAndStatusWithoutChangingAccount() = runTest {
        val provider = Provider()
        provider.stream = { throw ApiFailure(ApiProblem.PROTOCOL, status = 200, protocolDetail = ProtocolDetail.RESPONSE_JSON) }
        val account = accounts()
        val vm = InferenceViewModel(account, InferenceSession({ CredentialLease("client", 0, "synthetic", 999) }, provider) { true }).also { model = it }
        runCurrent(); vm.test(); runCurrent()
        assertEquals(ProofStage.FAILED, vm.state.value.stage)
        assertEquals(ProtocolDetail.RESPONSE_JSON, vm.state.value.protocolDetail)
        assertEquals(200, vm.state.value.httpStatus)
        assertFalse(vm.state.value.completedObserved)
        assertTrue(account.value.usable)
        vm.loadModels(); runCurrent()
        assertNull(vm.state.value.httpStatus); assertNull(vm.state.value.protocolDetail)
    }
    @Test fun usageLimitKeepsExplicitPlanAndExposesManageUsageHook() = runTest {
        val accounts = accounts(); val provider = Provider()
        val vm = InferenceViewModel(accounts, InferenceSession({ CredentialLease("client", 0, "token", 999) }, provider) { true }).also { model = it }
        runCurrent(); assertEquals(ProofStage.READY, vm.state.value.stage)
        vm.test(); runCurrent()
        assertEquals(ProofStage.FAILED, vm.state.value.stage); assertEquals(ApiProblem.USAGE_LIMIT, vm.state.value.problem)
        vm.select("slug"); assertEquals(ProofStage.FAILED, vm.state.value.stage)
        vm.manageUsage(); assertTrue(vm.state.value.usageHint)
        assertEquals("client", accounts.value.client); assertTrue(accounts.value.usable)
    }
    @Test fun accountChangeAndStopPreventLateOutputOrFalseSuccess() = runTest {
        val accounts = accounts(); val provider = Provider()
        provider.stream = { emit ->
            emit(ResponseEvent.Delta("partial"))
            withContext(NonCancellable) { delay(1000); emit(ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT)) }
        }
        val vm = InferenceViewModel(accounts, InferenceSession({ CredentialLease(accounts.value.client!!, accounts.value.generation, "token", 999) }, provider) { true }).also { model = it }
        runCurrent(); vm.test(); runCurrent()
        assertEquals("partial", vm.state.value.output)
        accounts.value = accounts.value.copy(client = "other", generation = 1)
        runCurrent(); advanceTimeBy(1001); runCurrent()
        assertEquals("", vm.state.value.output); assertFalse(vm.state.value.completedObserved)
        vm.test(); runCurrent(); vm.stop(); advanceTimeBy(1001); runCurrent()
        assertEquals(ProofStage.CANCELLED, vm.state.value.stage); assertFalse(vm.state.value.completedObserved)
    }
    @Test fun recreatedViewModelRestoresOnlyModelStillInCurrentCatalog() = runTest {
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("first", "First"), AiModel("second", "Second"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val session = InferenceSession({ CredentialLease("client", 0, "synthetic", 999) }, provider) { true }
        var saved: String? = null
        fun create() = InferenceViewModel(accounts(), session, { lease -> assertEquals("client", lease.clientId); saved },
            { lease, slug -> assertEquals("client", lease.clientId); assertEquals(0L, lease.generation); saved = slug }).also { model = it }
        val first = create(); runCurrent(); first.select("second"); runCurrent()
        assertEquals("second", saved)
        first.viewModelScope.cancel()
        val restored = create(); runCurrent(); assertEquals("second", restored.state.value.selectedSlug)
        restored.viewModelScope.cancel(); saved = "removed-model"
        val changedCatalog = create(); runCurrent(); assertEquals("first", changedCatalog.state.value.selectedSlug)
        changedCatalog.select("not-in-catalog"); runCurrent(); assertEquals("removed-model", saved)
    }
    @Test fun storageFailureCannotPublishAnUnsavedSelection() = runTest {
        val provider = Provider()
        val vm = InferenceViewModel(accounts(), InferenceSession({ CredentialLease("client", 0, "synthetic", 999) }, provider) { true },
            saveModel = { _, _ -> throw io.github.grassified.wristprompt.storage.SecureStorageException() }).also { model = it }
        runCurrent(); vm.select("slug"); runCurrent()
        assertEquals(ProofStage.FAILED, vm.state.value.stage); assertEquals(ApiProblem.STORAGE, vm.state.value.problem)
    }
    @Test fun restoredUncertainRefreshDoesNotStartDiscovery() = runTest {
        var requests = 0
        val account = accounts().apply { value = value.copy(usable = false, problem = ApiProblem.REAUTHORIZE) }
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> { requests++; return listOf(AiModel("slug", "Model")) }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val vm = InferenceViewModel(account, InferenceSession({ CredentialLease("client", 0, "synthetic", 999) }, provider) { true }).also { model = it }
        runCurrent(); assertEquals(0, requests); assertEquals(ApiProblem.REAUTHORIZE, vm.state.value.problem)
    }

    @Test fun switchingBillingModesRevalidatesIndependentModelPreferences() = runTest {
        val plan = io.github.grassified.wristprompt.auth.CredentialMode.CHATGPT_PLAN
        val api = io.github.grassified.wristprompt.auth.CredentialMode.OPENAI_API_KEY
        val access = accounts()
        val saved = mutableMapOf(plan to "plan-only", api to "plan-only") // stale API preference
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) =
                if (credential.mode == plan) listOf(AiModel("plan-only", "Plan"))
                else listOf(AiModel("api-first", "API first"), AiModel("api-second", "API second"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val session = InferenceSession({ CredentialLease("client", 0, "synthetic", 999,
            access.value.mode, access.value.modeRevision) }, provider) { it.mode == access.value.mode && it.modeRevision == access.value.modeRevision }
        val vm = InferenceViewModel(access, session, { saved[it.mode] }, { lease, slug -> saved[lease.mode] = slug }).also { model = it }
        runCurrent(); assertEquals("plan-only", vm.state.value.selectedSlug)
        access.value = access.value.copy(mode = api, modeRevision = 1); runCurrent()
        assertEquals(api, vm.state.value.mode)
        assertEquals("api-first", vm.state.value.selectedSlug)
        assertFalse(vm.state.value.models.any { it.slug == "plan-only" })
        vm.select("plan-only"); runCurrent(); assertEquals("api-first", vm.state.value.selectedSlug)
        vm.select("api-second"); runCurrent()
        access.value = access.value.copy(mode = plan, modeRevision = 2); runCurrent()
        assertEquals("plan-only", vm.state.value.selectedSlug)
        access.value = access.value.copy(mode = api, modeRevision = 3); runCurrent()
        assertEquals("api-second", vm.state.value.selectedSlug)
    }

    @Test fun lateDiscoveryCannotReplaceCatalogAfterAccountOrBillingSwitch() = runTest {
        for (modeSwitch in listOf(false, true)) {
            val access = accounts()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val provider = object : AiProvider {
                override suspend fun models(credential: CredentialLease): List<AiModel> {
                    if (credential.modeRevision == 0L && credential.generation == 0L) {
                        entered.complete(Unit)
                        withContext(NonCancellable) { release.await() }
                        return listOf(AiModel("old-model", "Old"))
                    }
                    return listOf(AiModel("current-model", "Current"))
                }
                override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
            }
            val session = InferenceSession({ CredentialLease(access.value.client!!, access.value.generation, "synthetic", 999,
                access.value.mode, access.value.modeRevision) }, provider) { lease ->
                lease.clientId == access.value.client && lease.generation == access.value.generation && lease.modeRevision == access.value.modeRevision
            }
            val vm = InferenceViewModel(access, session).also { model = it }
            runCurrent(); entered.await()
            access.value = if (modeSwitch) access.value.copy(mode = io.github.grassified.wristprompt.auth.CredentialMode.OPENAI_API_KEY, modeRevision = 1)
                else access.value.copy(client = "other-client", generation = 1)
            runCurrent(); assertEquals("current-model", vm.state.value.selectedSlug)
            release.complete(Unit); runCurrent()
            assertEquals(listOf("current-model"), vm.state.value.models.map { it.slug })
            assertEquals(access.value.mode, vm.state.value.mode)
            vm.viewModelScope.cancel()
        }
    }

    @Test fun cancellationKeepsCredentialsAndAllowsExplicitCatalogRetry() = runTest {
        val access = accounts(); val provider = Provider()
        provider.stream = { emit ->
            emit(ResponseEvent.Delta("partial"))
            withContext(NonCancellable) { delay(1000); emit(ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT)) }
        }
        val vm = InferenceViewModel(access, InferenceSession({ CredentialLease("client", 0, "synthetic", 999) }, provider) { true }).also { model = it }
        runCurrent(); vm.test(); runCurrent(); vm.stop()
        advanceTimeBy(1001); runCurrent()
        assertEquals(ProofStage.CANCELLED, vm.state.value.stage); assertEquals("partial", vm.state.value.output)
        assertFalse(vm.state.value.completedObserved); assertTrue(access.value.usable)
        vm.loadModels(); runCurrent(); assertEquals(ProofStage.READY, vm.state.value.stage)
    }

}
