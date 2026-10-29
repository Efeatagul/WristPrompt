package io.github.grassified.wristprompt.openai

import androidx.lifecycle.*
import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Connectivity changes alter the provider, never the action/credential owner. No radio observer. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityTransitionsTest {
    private val fixtures = mutableListOf<Fixture>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { fixtures.forEach { it.vm.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private class Fixture(mode: CredentialMode) {
        val access = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true, mode = mode))
        val inputs = mutableListOf<List<JsonObject>>()
        val leases = mutableListOf<CredentialLease>()
        var discoveries = 0
        var writes = 0
        var saved = "terra"
        var callback: (suspend (ResponseEvent) -> Unit)? = null
        var answer: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { it(ResponseEvent.Completed("Done")) }
        var catalog: suspend () -> List<AiModel> = { listOf(AiModel("sol", "Sol"), AiModel("terra", "Terra")) }
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> { discoveries++; return catalog() }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat only")
            override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
                inputs += input; leases += credential; callback = onEvent; answer(onEvent)
            }
        }
        val vm = InferenceViewModel(access, InferenceSession({ access.value.let {
            CredentialLease(it.client!!, it.generation, "synthetic", Long.MAX_VALUE, it.mode, it.modeRevision)
        } }, provider) { lease -> access.value.let {
            it.client == lease.clientId && it.generation == lease.generation && it.mode == lease.mode && it.modeRevision == lease.modeRevision
        } }, { saved }, { _, slug -> writes++; saved = slug })
        fun send(text: String) { vm.draft(text); vm.send() }
    }
    private fun fixture(mode: CredentialMode = CredentialMode.CHATGPT_PLAN) = Fixture(mode).also { fixtures += it }
    private fun contents(input: List<JsonObject>) = input.map { it["content"]!!.jsonPrimitive.content }

    @Test fun preDispatchFailurePreservesCompletedHistoryAndRecoveryIsExplicitInBothModes(): Unit = runTest {
        for (mode in CredentialMode.entries) {
            val f = fixture(mode); runCurrent(); f.send("Previous"); runCurrent()
            val access = f.access.value
            f.answer = { throw ApiFailure(ApiProblem.NETWORK, requestNotSent = true) }
            f.send("A"); runCurrent()
            val failure = f.vm.chat.value
            assertEquals(ChatStage.FAILED, failure.stage); assertEquals(ApiProblem.NETWORK, failure.problem)
            assertEquals("", failure.partial); assertFalse(failure.busy)
            assertEquals(listOf("Previous", "Done", "A"), failure.messages.map { it.text })
            assertEquals(ChatMessageStatus.EXCLUDED, failure.messages.last().status)
            assertEquals(1, failure.messages.count { it.role == ChatRole.ASSISTANT })
            f.answer = { it(ResponseEvent.Completed("Recovered")) }; advanceUntilIdle()
            assertEquals(failure, f.vm.chat.value); assertEquals(2, f.inputs.size); assertEquals(1, f.discoveries)
            assertEquals(access, f.access.value); assertEquals(0, f.writes)
            f.send("B"); f.vm.send(); runCurrent()
            assertEquals(listOf("Previous", "Done", "B"), contents(f.inputs.last()))
            assertEquals(3, f.inputs.size); assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage)
            assertTrue(f.leases.all { it.mode == mode && it.accessToken == "synthetic" })
        }
    }

    @Test fun interruptedPartialRejectsLateCallbackAndDoesNotPoisonExplicitNextRequest(): Unit = runTest {
        val f = fixture(); runCurrent(); f.send("Previous"); runCurrent()
        f.answer = { it(ResponseEvent.Delta("Partial")); throw ApiFailure(ApiProblem.INTERRUPTED) }
        f.send("A"); runCurrent(); val late = f.callback!!; val failed = f.vm.chat.value
        assertEquals("Partial", failed.partial); assertEquals(ApiProblem.INTERRUPTED, failed.problem)
        assertEquals(ChatMessageStatus.EXCLUDED, failed.messages.last().status); assertFalse(failed.busy)
        f.answer = { it(ResponseEvent.Completed("B answer")) }; advanceUntilIdle()
        assertEquals(2, f.inputs.size); assertEquals(failed, f.vm.chat.value)
        f.send("B"); runCurrent(); val recovered = f.vm.chat.value
        try { late(ResponseEvent.Completed("Late A")); fail("Old owner must reject callback") }
        catch (_: CancellationException) { }
        assertEquals(recovered, f.vm.chat.value)
        assertEquals(listOf("Previous", "Done", "B"), contents(f.inputs.last()))
        assertEquals(3, f.inputs.size); assertFalse(recovered.busy)
    }

    @Test fun networkFailureUnwindingCannotPublishAfterAccountGenerationOrBillingRevisionChanges(): Unit = runTest {
        for (change in 0..2) {
            val f = fixture(); runCurrent()
            val failed = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.answer = { emit ->
                emit(ResponseEvent.Delta("Old partial")); failed.complete(Unit)
                withContext(NonCancellable) { release.await(); throw ApiFailure(ApiProblem.INTERRUPTED) }
            }
            f.send("A"); runCurrent(); assertTrue(failed.isCompleted)
            f.access.value = when (change) {
                0 -> f.access.value.copy(generation = 1)
                1 -> f.access.value.copy(modeRevision = 1)
                else -> f.access.value.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = 1)
            }
            runCurrent(); f.answer = { it(ResponseEvent.Completed("New answer")) }
            f.send("B"); runCurrent(); release.complete(Unit); runCurrent()
            assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage)
            assertEquals(listOf("B", "New answer"), f.vm.chat.value.messages.map { it.text })
            assertNull(f.vm.chat.value.problem); assertEquals(1, f.inputs.last().size)
        }
    }

    @Test fun failedDiscoveryRequiresExplicitReloadRestoresPreferenceAndDoesNotWriteFallback(): Unit = runTest {
        val f = fixture(); runCurrent(); f.send("Previous"); runCurrent()
        val access = f.access.value
        f.catalog = { throw ApiFailure(ApiProblem.NETWORK) }; f.vm.loadModels(); runCurrent()
        val failure = f.vm.state.value
        assertEquals(ProofStage.FAILED, failure.stage); assertEquals(ApiProblem.NETWORK, failure.problem)
        assertTrue(failure.models.isEmpty()); assertNull(failure.selectedSlug)
        f.catalog = { listOf(AiModel("terra", "Terra"), AiModel("sol", "Sol")) }; advanceUntilIdle()
        assertEquals(failure, f.vm.state.value); assertEquals(2, f.discoveries)
        assertEquals(access, f.access.value); assertEquals(0, f.writes)
        f.vm.loadModels(); runCurrent()
        assertEquals("terra", f.vm.state.value.selectedSlug); assertEquals(3, f.discoveries)
        assertEquals(0, f.writes); assertEquals(2, f.vm.chat.value.messages.size)
        f.catalog = { listOf(AiModel("sol", "Sol")) }; f.vm.loadModels(); runCurrent()
        assertEquals("sol", f.vm.state.value.selectedSlug); assertEquals("terra", f.saved)
        assertEquals(0, f.writes); assertEquals(ChatState(), f.vm.chat.value)
    }

    @Test fun lateFailedDiscoveryCannotOverwriteExplicitRecoveredReload(): Unit = runTest {
        val f = fixture(); runCurrent()
        val release = CompletableDeferred<Unit>()
        f.catalog = { withContext(NonCancellable) { release.await(); throw ApiFailure(ApiProblem.NETWORK) } }
        f.vm.loadModels(); runCurrent(); f.vm.stop(); runCurrent()
        f.catalog = { listOf(AiModel("terra", "Recovered")) }; f.vm.loadModels(); runCurrent()
        val recovered = f.vm.state.value; assertEquals(ProofStage.READY, recovered.stage)
        release.complete(Unit); runCurrent()
        assertEquals(recovered, f.vm.state.value); assertEquals(3, f.discoveries); assertEquals(0, f.inputs.size)
    }

    @Test fun lateDiscoveryErrorCannotOverwriteNewAccountOrBillingCatalog(): Unit = runTest {
        for (billing in listOf(false, true)) {
            val f = fixture(); runCurrent(); val release = CompletableDeferred<Unit>()
            f.catalog = { withContext(NonCancellable) { release.await(); throw ApiFailure(ApiProblem.NETWORK) } }
            f.vm.loadModels(); runCurrent()
            f.catalog = { listOf(AiModel("terra", "Current")) }
            f.access.value = if (billing) f.access.value.copy(modeRevision = 1) else f.access.value.copy(generation = 1)
            runCurrent(); val current = f.vm.state.value
            release.complete(Unit); runCurrent(); assertEquals(current, f.vm.state.value)
            assertEquals(ProofStage.READY, current.stage); assertNull(current.problem)
        }
    }

    @Test fun backgroundNetworkFailureResumesLatestStateInRetainedOwnerWithoutReplay(): Unit = runTest {
        val f = fixture(); runCurrent(); val fail = CompletableDeferred<Unit>()
        f.answer = { it(ResponseEvent.Delta("Partial")); fail.await(); throw ApiFailure(ApiProblem.INTERRUPTED) }
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        val rendered = mutableListOf<ChatState>()
        val observer = backgroundScope.launch { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            f.vm.chat.collect { rendered += it }
        } }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED; f.send("A"); runCurrent()
        owner.lifecycle.currentState = Lifecycle.State.CREATED; runCurrent(); val previous = rendered.last()
        fail.complete(Unit); runCurrent(); assertEquals(previous, rendered.last())
        owner.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
        assertEquals(f.vm.chat.value, rendered.last()); assertEquals("Partial", rendered.last().partial)
        assertEquals(ApiProblem.INTERRUPTED, rendered.last().problem); assertEquals(1, f.inputs.size)
        assertEquals(1, f.discoveries); observer.cancel()
    }
}
