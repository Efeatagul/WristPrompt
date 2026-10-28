package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.WatchAccountState
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
    private fun accounts() = MutableStateFlow(WatchAccountState(activeClientId = "client", connected = true, planEnabled = true))
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
        assertTrue(account.value.planEnabled)
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
        assertEquals("client", accounts.value.activeClientId); assertTrue(accounts.value.planEnabled)
    }
    @Test fun accountChangeAndStopPreventLateOutputOrFalseSuccess() = runTest {
        val accounts = accounts(); val provider = Provider()
        provider.stream = { emit ->
            emit(ResponseEvent.Delta("partial"))
            withContext(NonCancellable) { delay(1000); emit(ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT)) }
        }
        val vm = InferenceViewModel(accounts, InferenceSession({ CredentialLease(accounts.value.activeClientId!!, accounts.value.generation, "token", 999) }, provider) { true }).also { model = it }
        runCurrent(); vm.test(); runCurrent()
        assertEquals("partial", vm.state.value.output)
        accounts.value = accounts.value.copy(activeClientId = "other", generation = 1)
        runCurrent(); advanceTimeBy(1001); runCurrent()
        assertEquals("", vm.state.value.output); assertFalse(vm.state.value.completedObserved)
        vm.test(); runCurrent(); vm.stop(); advanceTimeBy(1001); runCurrent()
        assertEquals(ProofStage.CANCELLED, vm.state.value.stage); assertFalse(vm.state.value.completedObserved)
    }
}
