package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingRaceTest {
    private val access = MutableStateFlow(InferenceAccessState(client = "client", usable = true))
    private val lease = CredentialLease("client", 0, "synthetic", Long.MAX_VALUE)
    private val models = mutableListOf<InferenceViewModel>()
    private class Provider : AiProvider {
        val inputs = mutableListOf<List<JsonObject>>()
        var active = 0
        var peak = 0
        var cancelled = 0
        var script: suspend (Int, suspend (ResponseEvent) -> Unit) -> Unit = { _, emit -> emit(ResponseEvent.Completed("Answer")) }
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("model", "Model"))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat only")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            inputs += input; active++; peak = maxOf(peak, active)
            try { script(inputs.size, onEvent) }
            catch (failure: CancellationException) { cancelled++; throw failure }
            finally { active-- }
        }
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun session(p: Provider) = InferenceSession({ lease }, p) { true }
    private fun model(p: Provider, s: InferenceSession = session(p)) = InferenceViewModel(access, s).also { models += it }
    private fun send(vm: InferenceViewModel, text: String) { vm.draft(text); vm.send() }

    @Test fun stopWhileIdleIsNoOpIncludingAfterCompletion(): Unit = runTest {
        val vm = model(Provider()); runCurrent()
        val state = vm.state.value; val chat = vm.chat.value; val input = vm.inputToken()
        vm.stop(); assertEquals(state, vm.state.value); assertEquals(chat, vm.chat.value); assertEquals(input, vm.inputToken())
        send(vm, "A"); runCurrent(); val done = vm.chat.value
        vm.stop(); assertEquals(done, vm.chat.value); assertEquals(state, vm.state.value)
    }
    @Test fun stopThenImmediateSendWaitsForOldProviderExitAndExcludesCancelledExchange(): Unit = runTest {
        val release = CompletableDeferred<Unit>()
        val p = Provider().apply { script = { number, emit ->
            if (number == 1) {
                emit(ResponseEvent.Delta("A partial"))
                withContext(NonCancellable) { release.await() }
            } else emit(ResponseEvent.Completed("B answer"))
        } }
        val vm = model(p); runCurrent(); send(vm, "A"); runCurrent(); vm.stop()
        assertEquals(ChatStage.CANCELLED, vm.chat.value.stage); assertEquals("A partial", vm.chat.value.partial)
        assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
        send(vm, "B"); runCurrent()
        assertEquals(1, p.inputs.size) // B cannot enter provider until A has exited.
        assertEquals(ChatStage.STARTING, vm.chat.value.stage)
        release.complete(Unit); runCurrent()
        assertEquals(1, p.peak); assertEquals(2, p.inputs.size)
        assertEquals("B", p.inputs.last().single()["content"]!!.jsonPrimitive.content)
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage)
        assertEquals(listOf("A", "B", "B answer"), vm.chat.value.messages.map { it.text })
    }
    @Test fun lateDeltaCompletionAndFailureAfterStopCannotTouchStoppedOrNextState(): Unit = runTest {
        for (late in listOf<ResponseEvent>(ResponseEvent.Delta("late"), ResponseEvent.Completed("late"), ResponseEvent.Failed(ApiFailure(ApiProblem.TEMPORARY)))) {
            val release = CompletableDeferred<Unit>()
            val p = Provider().apply { script = { number, emit ->
                if (number == 1) {
                    emit(ResponseEvent.Delta("partial"))
                    withContext(NonCancellable) { release.await(); emit(late) }
                } else emit(ResponseEvent.Completed("fresh answer"))
            } }
            val vm = model(p); runCurrent(); send(vm, "old"); runCurrent(); vm.stop()
            val stopped = vm.chat.value
            release.complete(Unit); runCurrent(); assertEquals(stopped, vm.chat.value)
            send(vm, "fresh"); runCurrent(); assertEquals("fresh answer", vm.chat.value.messages.last().text)
            assertEquals(1, p.inputs.last().size); vm.viewModelScope.cancel()
        }
    }
    @Test fun oldErrorCannotReplaceNewSuccess(): Unit = runTest {
        val release = CompletableDeferred<Unit>()
        val p = Provider().apply { script = { number, emit ->
            if (number == 1) withContext(NonCancellable) { release.await(); throw ApiFailure(ApiProblem.NETWORK) }
            else emit(ResponseEvent.Completed("new success"))
        } }
        val vm = model(p); runCurrent(); send(vm, "A"); runCurrent(); vm.stop(); send(vm, "B"); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage); assertNull(vm.chat.value.problem)
        assertEquals("new success", vm.chat.value.messages.last().text)
    }
    @Test fun sessionSerializesGenerationsEvenAcrossViewModelOwners(): Unit = runTest {
        val release = CompletableDeferred<Unit>()
        val p = Provider().apply { script = { number, emit -> if (number == 1) release.await(); emit(ResponseEvent.Completed("Answer $number")) } }
        val shared = session(p); val first = model(p, shared); val second = model(p, shared); runCurrent()
        send(first, "First"); send(second, "Second"); runCurrent(); assertEquals(1, p.inputs.size)
        release.complete(Unit); runCurrent(); assertEquals(1, p.peak)
        assertEquals(ChatStage.COMPLETED, second.chat.value.stage)
    }
    @Test fun duplicateSendAndStopBeforeDispatchCannotStartExtraHttpWork(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val p = Provider().apply { script = { _, _ -> awaitCancellation() } }
        val vm = model(p); runCurrent(); send(vm, "A"); vm.send(); vm.stop(); runCurrent()
        assertEquals(0, p.inputs.size); assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
        send(vm, "B"); vm.send(); runCurrent(); assertEquals(1, p.inputs.size)
        vm.stop(); runCurrent(); assertEquals(1, p.cancelled)
    }
    @Test fun completedTerminalFinalizesOnceAndIgnoresLaterProviderEvents(): Unit = runTest {
        val p = Provider().apply { script = { _, emit ->
            emit(ResponseEvent.Delta("Answer")); emit(ResponseEvent.Completed("Answer"))
            emit(ResponseEvent.Completed("Duplicate")); emit(ResponseEvent.Delta("late"))
            emit(ResponseEvent.Failed(ApiFailure(ApiProblem.TEMPORARY)))
        } }
        val vm = model(p); runCurrent(); send(vm, "First"); runCurrent()
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage)
        assertEquals(listOf("First", "Answer"), vm.chat.value.messages.map { it.text })
        send(vm, "Follow-up"); runCurrent()
        assertEquals(listOf("First", "Answer", "Follow-up"), p.inputs.last().map { it["content"]!!.jsonPrimitive.content })
    }
    @Test fun partialFailuresIncompleteEofAndProtocolDoNotEnterContext(): Unit = runTest {
        for (problem in listOf(ApiProblem.NETWORK, ApiProblem.TEMPORARY, ApiProblem.INCOMPLETE, ApiProblem.INTERRUPTED, ApiProblem.PROTOCOL)) {
            val p = Provider().apply { script = { number, emit ->
                if (number == 1) { emit(ResponseEvent.Delta("partial")); throw ApiFailure(problem) }
                else emit(ResponseEvent.Completed("fresh"))
            } }
            val vm = model(p); runCurrent(); send(vm, "bad"); runCurrent()
            assertEquals(ChatStage.FAILED, vm.chat.value.stage); assertEquals("partial", vm.chat.value.partial)
            assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
            send(vm, "good"); runCurrent(); assertEquals(1, p.inputs.last().size); vm.viewModelScope.cancel()
        }
    }
    @Test fun manyOrderedDeltasPreserveRepeatedTextAndDoNotCopyCompletedHistory(): Unit = runTest {
        val release = CompletableDeferred<Unit>(); val expected = buildString { repeat(2000) { append("ha") } }
        val p = Provider().apply { script = { number, emit ->
            if (number == 1) emit(ResponseEvent.Completed("Previous"))
            else { repeat(2000) { emit(ResponseEvent.Delta("ha")) }; release.await(); emit(ResponseEvent.Completed(expected)) }
        } }
        val vm = model(p); runCurrent(); send(vm, "first"); runCurrent()
        var observed: List<ChatMessage>? = null
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.chat.collect { state ->
                if (state.stage == ChatStage.RECEIVING) {
                    observed?.let { assertSame(it, state.messages) }
                    observed = state.messages
                }
            }
        }
        send(vm, "second"); runCurrent()
        val messages = vm.chat.value.messages
        assertEquals(expected, vm.chat.value.partial); assertEquals(3, messages.size)
        assertEquals(ChatMessageStatus.PENDING, messages.last().status)
        release.complete(Unit); runCurrent(); assertEquals(expected, vm.chat.value.messages.last().text)
        assertEquals(4, vm.chat.value.messages.size); observer.cancel()
    }
    @Test fun newChatWhileReceivingRejectsOldGenerationAndCanStartFresh(): Unit = runTest {
        val release = CompletableDeferred<Unit>()
        val p = Provider().apply { script = { number, emit ->
            if (number == 1) withContext(NonCancellable) { release.await(); emit(ResponseEvent.Completed("old")) }
            else emit(ResponseEvent.Completed("new"))
        } }
        val vm = model(p); runCurrent(); send(vm, "old"); runCurrent(); vm.newChat(); send(vm, "new"); runCurrent()
        release.complete(Unit); runCurrent(); assertEquals(listOf("new", "new"), vm.chat.value.messages.map { it.text })
    }
    @Test fun cancelledSessionJobRejectsCallbacksEvenInsideNonCancellableProvider(): Unit = runTest {
        val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>(); var callbacks = 0
        val p = Provider().apply { script = { _, emit ->
            entered.complete(Unit)
            withContext(NonCancellable) { release.await(); emit(ResponseEvent.Delta("stale")) }
        } }
        val s = session(p); val catalog = s.catalog()
        val job = launch { s.chat(catalog, "model", listOf(buildJsonObject { put("role", "user"); put("content", "A") })) { callbacks++ } }
        runCurrent(); entered.await(); s.cancel("client"); release.complete(Unit); job.join()
        assertTrue(job.isCancelled); assertEquals(0, callbacks)
    }
    @Test fun sessionCancellationAlsoCancelsQueuedGeneration(): Unit = runTest {
        val p = Provider().apply { script = { _, _ -> awaitCancellation() } }
        val s = session(p); val catalog = s.catalog()
        val input = listOf(buildJsonObject { put("role", "user"); put("content", "A") })
        val first = launch { s.chat(catalog, "model", input) {} }
        val queued = launch { s.chat(catalog, "model", input) {} }
        runCurrent(); assertEquals(1, p.inputs.size)
        s.cancelAll(); first.join(); queued.join()
        assertTrue(first.isCancelled); assertTrue(queued.isCancelled); assertEquals(1, p.inputs.size)
    }
    @Test fun viewModelDisposalCancelsGenerationWithoutCompletingHistory(): Unit = runTest {
        val p = Provider().apply { script = { _, emit -> emit(ResponseEvent.Delta("partial")); awaitCancellation() } }
        val vm = model(p); runCurrent(); send(vm, "A"); runCurrent(); vm.viewModelScope.cancel(); runCurrent()
        assertEquals(1, p.cancelled); assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
        assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
    }
}
