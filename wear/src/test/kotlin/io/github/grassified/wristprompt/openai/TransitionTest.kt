package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransitionTest {
    private val access = MutableStateFlow(InferenceAccessState(client = "client", usable = true))
    private val owners = mutableListOf<InferenceViewModel>()
    private var token = "synthetic-old"
    private var current = true
    private var posts = 0
    private var catalog: suspend () -> List<AiModel> = { listOf(AiModel("terra", "Terra")) }
    private var answer: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { it(ResponseEvent.Completed("Done")) }
    private lateinit var session: InferenceSession
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { owners.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun create(preference: suspend (CredentialLease) -> String? = { "terra" }): InferenceViewModel {
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = catalog()
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) { posts++; answer(onEvent) }
            override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) = stream(credential, model, onEvent)
        }
        session = InferenceSession({ CredentialLease(access.value.client!!, access.value.generation, token, Long.MAX_VALUE,
            access.value.mode, access.value.modeRevision) }, provider) { current }
        return InferenceViewModel(access, session, preference).also { owners += it }
    }
    private fun send(vm: InferenceViewModel) { vm.draft("Synthetic question"); vm.send() }

    @Test fun sharedCancellationTerminatesModelLoadingAndAllowsRecovery() = runTest {
        val entered = CompletableDeferred<Unit>()
        catalog = { entered.complete(Unit); awaitCancellation() }
        val vm = create(); runCurrent(); entered.await(); session.cancelAll(); runCurrent()
        assertEquals(ProofStage.CANCELLED, vm.state.value.stage); assertFalse(vm.state.value.busy)
        catalog = { listOf(AiModel("terra", "Terra")) }; vm.loadModels(); runCurrent()
        assertEquals(ProofStage.READY, vm.state.value.stage)
    }
    @Test fun sharedCancellationTerminatesProofAndAllowsRecovery() = runTest {
        answer = { it(ResponseEvent.Delta("Partial")); awaitCancellation() }
        val vm = create(); runCurrent(); vm.test(); runCurrent(); session.cancelAll(); runCurrent()
        assertEquals(ProofStage.CANCELLED, vm.state.value.stage); assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.problem); assertEquals("Partial", vm.state.value.output)
        answer = { it(ResponseEvent.Completed("Done")) }; send(vm); runCurrent()
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage)
    }
    @Test fun refreshedCatalogMissingModelBlocksFurtherSendUntilReload() = runTest {
        val vm = create(); runCurrent(); token = "synthetic-new"
        catalog = { listOf(AiModel("sol", "Sol")) }; send(vm); runCurrent()
        assertEquals(ApiProblem.NO_MODELS, vm.chat.value.problem); assertNull(vm.state.value.selectedSlug)
        assertEquals(ProofStage.FAILED, vm.state.value.stage); assertEquals(ApiProblem.NO_MODELS, vm.state.value.problem)
        send(vm); runCurrent(); assertEquals(0, posts)
        vm.loadModels(); runCurrent(); assertEquals("sol", vm.state.value.selectedSlug)
        assertEquals(ChatState(), vm.chat.value); send(vm); runCurrent(); assertEquals(1, posts)
    }
    @Test fun discoveryPreferenceWaitRevalidatesLeaseBeforePublishing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = create { gate.await(); "terra" }; runCurrent(); current = false; gate.complete(Unit); runCurrent()
        assertEquals(ProofStage.FAILED, vm.state.value.stage)
        assertEquals(ApiProblem.STALE_ACCOUNT, vm.state.value.problem); assertNull(vm.state.value.selectedSlug)
        send(vm); runCurrent(); assertEquals(0, posts)
    }
    @Test fun accessChangeBeforeCollectorRunsCannotSubmitOldConversation() = runTest {
        for (change in listOf<(InferenceAccessState) -> InferenceAccessState>(
            { it.copy(usable = false, problem = ApiProblem.REAUTHORIZE) },
            { it.copy(client = "other") }, { it.copy(generation = it.generation + 1) },
            { it.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = it.modeRevision + 1) },
            { it.copy(modeRevision = it.modeRevision + 1) })) {
            access.value = InferenceAccessState(client = "client", usable = true)
            val vm = create(); runCurrent(); vm.draft("Old context")
            access.value = change(access.value); vm.send()
            assertTrue(vm.chat.value.messages.isEmpty()); runCurrent(); assertEquals(0, posts)
            vm.viewModelScope.cancel()
        }
    }
    @Test fun reauthFailureClearsReplayAndInvalidatesModelWithoutFallback() = runTest {
        val vm = create(); runCurrent(); send(vm); runCurrent(); assertEquals(2, vm.chat.value.messages.size)
        answer = { throw ApiFailure(ApiProblem.REAUTHORIZE) }; send(vm); runCurrent()
        assertEquals(ChatStage.FAILED, vm.chat.value.stage); assertTrue(vm.chat.value.messages.isEmpty())
        assertNull(vm.state.value.selectedSlug); assertEquals(CredentialMode.CHATGPT_PLAN, vm.state.value.mode)
    }
    @Test fun ordinaryFailuresPreserveCompletedConversationAndPermitExplicitRecovery() = runTest {
        for (problem in listOf(ApiProblem.NETWORK, ApiProblem.TEMPORARY, ApiProblem.PROTOCOL, ApiProblem.USAGE_LIMIT)) {
            val vm = create(); answer = { it(ResponseEvent.Completed("Done")) }; runCurrent(); send(vm); runCurrent()
            answer = { throw ApiFailure(problem) }; send(vm); runCurrent()
            assertEquals(problem, vm.chat.value.problem); assertEquals(3, vm.chat.value.messages.size)
            assertEquals("terra", vm.state.value.selectedSlug); assertTrue(access.value.usable)
            answer = { it(ResponseEvent.Completed("Recovered")) }; send(vm); runCurrent()
            assertEquals(ChatStage.COMPLETED, vm.chat.value.stage); assertEquals(5, vm.chat.value.messages.size)
            vm.viewModelScope.cancel()
        }
    }
    @Test fun cancelledOwnerReconstructionCannotResurrectRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        answer = { emit -> withContext(NonCancellable) { gate.await(); emit(ResponseEvent.Completed("Old")) } }
        val old = create(); runCurrent(); send(old); runCurrent(); old.stop(); old.viewModelScope.cancel()
        val restored = create(); runCurrent(); gate.complete(Unit); runCurrent()
        assertEquals(ChatState(), restored.chat.value); assertFalse(restored.state.value.busy); assertEquals(1, posts)
    }
    @Test fun composingAfterErrorClearsStaleErrorWithoutCreatingRequest() = runTest {
        answer = { throw ApiFailure(ApiProblem.NETWORK) }; val vm = create(); runCurrent(); send(vm); runCurrent()
        vm.draft("Review first"); assertEquals(ChatStage.COMPOSING, vm.chat.value.stage)
        assertNull(vm.chat.value.problem); assertEquals(1, posts)
    }
    @Test fun modelNotFoundIsSafeAndModeIndependent() {
        for (mode in CredentialMode.entries) {
            val failure = ApiErrors.forMode(ApiErrors.parse(404, """{"error":{"code":"model_not_found","message":"private"}}"""), mode)
            assertEquals(ApiProblem.NO_MODELS, failure.problem); assertNull(failure.cause)
            assertFalse(failure.message.orEmpty().contains("private"))
        }
    }
    @Test fun activeCredentialInvalidationRejectsLateOutputAndClearsConversation() = runTest {
        for (change in listOf<(InferenceAccessState) -> InferenceAccessState>(
            { it.copy(usable = false, problem = ApiProblem.REAUTHORIZE) },
            { it.copy(generation = it.generation + 1) },
            { it.copy(modeRevision = it.modeRevision + 1) })) {
            access.value = InferenceAccessState(client = "client", usable = true)
            val gate = CompletableDeferred<Unit>()
            answer = { emit ->
                emit(ResponseEvent.Delta("Old partial"))
                withContext(NonCancellable) { gate.await(); emit(ResponseEvent.Completed("Old completion")) }
            }
            val vm = create(); runCurrent(); send(vm); runCurrent()
            access.value = change(access.value); runCurrent(); gate.complete(Unit); runCurrent()
            assertEquals(ChatState(), vm.chat.value); assertFalse(vm.state.value.busy)
            vm.viewModelScope.cancel()
        }
    }
    @Test fun sharedCancellationOfChatIsStoppedAndNextSendIsExplicit() = runTest {
        answer = { it(ResponseEvent.Delta("Partial")); awaitCancellation() }
        val vm = create(); runCurrent(); send(vm); runCurrent(); session.cancelAll(); runCurrent()
        assertEquals(ChatStage.CANCELLED, vm.chat.value.stage); assertNull(vm.chat.value.problem)
        assertEquals("Partial", vm.chat.value.partial); assertFalse(vm.chat.value.busy)
        answer = { it(ResponseEvent.Completed("Recovered")) }; send(vm); runCurrent()
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage); assertEquals(2, posts)
    }

}
