package io.github.grassified.wristprompt.openai

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.*
import org.junit.Assert.*

/** Real stores and lifecycle collection; Activity recreation itself is exercised by the debug host. */
@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleOwnershipTest {
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private class Host(override val viewModelStore: ViewModelStore) : ViewModelStoreOwner, LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
        init { lifecycle.currentState = Lifecycle.State.CREATED }
    }
    private class Fixture {
        val access = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true))
        val events = Channel<ResponseEvent>(Channel.UNLIMITED)
        var generations = 0
        var discoveries = 0
        val leases = mutableListOf<CredentialLease>()
        var discoveryGate: CompletableDeferred<Unit>? = null
        var lateGate: CompletableDeferred<Unit>? = null
        var lateError = false
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> {
                discoveries++
                if (discoveries == 1) discoveryGate?.let { withContext(NonCancellable) { it.await() } }
                return listOf(AiModel("model-${credential.modeRevision}", "Synthetic"))
            }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat only")
            override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
                generations++; leases += credential
                onEvent(ResponseEvent.Delta("one"))
                lateGate?.let { gate ->
                    withContext(NonCancellable) {
                        gate.await()
                        if (lateError) throw ApiFailure(ApiProblem.NETWORK)
                        onEvent(ResponseEvent.Completed("stale"))
                    }
                    return
                }
                for (event in events) { onEvent(event); if (event is ResponseEvent.Completed) return }
            }
        }
        val session = InferenceSession({
            access.value.let { CredentialLease(it.client!!, it.generation, "", Long.MAX_VALUE, it.mode, it.modeRevision) }
        }, provider) { lease -> access.value.let { it.client == lease.clientId && it.generation == lease.generation && it.mode == lease.mode && it.modeRevision == lease.modeRevision } }
        fun vm(host: Host) = ViewModelProvider.create(host, viewModelFactory { initializer { InferenceViewModel(access, session) } })[InferenceViewModel::class]
    }
    private fun host() = Host(ViewModelStore().also { stores += it })
    private fun send(vm: InferenceViewModel) { vm.draft("Question"); vm.send() }
    private fun TestScope.observe(host: Host, vm: InferenceViewModel, rendered: MutableList<ChatState>) = backgroundScope.launch {
        host.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.chat.collect { rendered += it } }
    }

    @Test fun retainedStoreContinuesSingleStreamAndCompletesOnce(): Unit = runTest {
        val f = Fixture(); val old = host(); val vm = f.vm(old); runCurrent(); send(vm); runCurrent()
        val before = vm.chat.value
        old.lifecycle.currentState = Lifecycle.State.DESTROYED
        val next = Host(old.viewModelStore)
        assertSame(vm, f.vm(next)); assertEquals(before, vm.chat.value)
        f.events.send(ResponseEvent.Delta(" two")); runCurrent()
        assertEquals("one two", vm.chat.value.partial)
        f.events.send(ResponseEvent.Completed("one two")); runCurrent()
        vm.send(); runCurrent()
        assertEquals(1, f.generations); assertEquals(1, f.discoveries)
        assertEquals(listOf("Question", "one two"), vm.chat.value.messages.map { it.text })
        assertEquals(1, vm.chat.value.messages.count { it.role == ChatRole.ASSISTANT })
    }
    @Test fun stopAfterRetainedOwnerReplacementExcludesIncompleteExchange(): Unit = runTest {
        val f = Fixture(); val old = host(); val vm = f.vm(old); runCurrent(); send(vm); runCurrent()
        val retained = f.vm(Host(old.viewModelStore)); retained.stop(); runCurrent()
        assertSame(vm, retained); assertEquals(ChatStage.CANCELLED, retained.chat.value.stage)
        assertEquals(ChatMessageStatus.EXCLUDED, retained.chat.value.messages.single().status)
        assertEquals(1, f.generations)
    }
    @Test fun pausedLifecycleCollectionResumesLatestWithoutResendReloadOrCredentialChange(): Unit = runTest {
        val f = Fixture(); val h = host(); val vm = f.vm(h); val rendered = mutableListOf<ChatState>()
        observe(h, vm, rendered); h.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent(); send(vm); runCurrent(); val observed = rendered.last()
        h.lifecycle.currentState = Lifecycle.State.CREATED; runCurrent()
        f.events.send(ResponseEvent.Delta(" two")); runCurrent()
        assertEquals(observed, rendered.last()); assertEquals("one two", vm.chat.value.partial)
        h.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
        assertEquals("one two", rendered.last().partial)
        f.events.send(ResponseEvent.Completed("one two")); runCurrent()
        repeat(3) { h.lifecycle.currentState = Lifecycle.State.CREATED; runCurrent(); h.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent() }
        assertEquals(vm.chat.value, rendered.last()); assertEquals(1, f.generations); assertEquals(1, f.discoveries)
        assertEquals(listOf(CredentialMode.CHATGPT_PLAN), f.leases.map { it.mode })
        assertEquals(2, rendered.last().messages.size)
    }
    @Test fun idleAndRepeatedObserversCreateNoWork(): Unit = runTest {
        val f = Fixture(); val h = host(); val vm = f.vm(h); runCurrent()
        repeat(5) {
            val rendered = mutableListOf<ChatState>(); val observer = observe(h, vm, rendered)
            h.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
            h.lifecycle.currentState = Lifecycle.State.CREATED; runCurrent(); observer.cancel()
            assertSame(vm, f.vm(Host(h.viewModelStore)))
        }
        assertEquals(ChatState(), vm.chat.value); assertEquals(0, f.generations); assertEquals(1, f.discoveries)
    }
    @Test fun completedAndCancelledStatesAreStableAcrossRetainedOwners(): Unit = runTest {
        for (cancel in listOf(false, true)) {
            val f = Fixture(); val h = host(); val vm = f.vm(h); runCurrent(); send(vm); runCurrent()
            if (cancel) vm.stop() else f.events.send(ResponseEvent.Completed("one"))
            runCurrent(); val terminal = vm.chat.value
            repeat(3) { assertEquals(terminal, f.vm(Host(h.viewModelStore)).chat.value) }
            assertEquals(1, f.generations)
        }
    }
    @Test fun finalStoreClearInvalidatesBeforeAnyCancellationCallbackPublishes(): Unit = runTest {
        val f = Fixture().apply { lateGate = CompletableDeferred() }; val h = host(); val vm = f.vm(h)
        runCurrent(); send(vm); runCurrent()
        h.viewModelStore.clear()
        val finalState = vm.chat.value
        assertEquals(ChatStage.CANCELLED, finalState.stage)
        assertEquals(ChatMessageStatus.EXCLUDED, finalState.messages.single().status)
        f.lateGate!!.complete(Unit); runCurrent()
        assertEquals(finalState, vm.chat.value); assertNull(vm.inputToken())
        assertFalse(vm.chat.value.messages.any { it.role == ChatRole.ASSISTANT })
        assertFalse(vm.viewModelScope.coroutineContext[Job]!!.isActive)
    }
    @Test fun backgroundAccountGenerationAndBillingChangesRejectLateCompletionAndError(): Unit = runTest {
        for (change in 0..2) for (error in listOf(false, true)) {
            val f = Fixture().apply { lateGate = CompletableDeferred(); lateError = error }
            val h = host(); val vm = f.vm(h); val rendered = mutableListOf<ChatState>(); observe(h, vm, rendered)
            h.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent(); send(vm); runCurrent()
            h.lifecycle.currentState = Lifecycle.State.CREATED; runCurrent()
            f.access.value = when (change) {
                0 -> f.access.value.copy(client = "other")
                1 -> f.access.value.copy(generation = 1)
                else -> f.access.value.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = 1)
            }
            runCurrent(); f.lateGate!!.complete(Unit); runCurrent()
            h.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
            assertEquals(ChatState(), rendered.last()); assertEquals(1, f.generations)
            assertNull(vm.state.value.problem)
        }
    }
    @Test fun backgroundDiscoveryCannotPublishOldAccountOrBillingCatalog(): Unit = runTest {
        for (billing in listOf(false, true)) {
            val f = Fixture().apply { discoveryGate = CompletableDeferred() }; val h = host(); val vm = f.vm(h)
            runCurrent(); h.lifecycle.currentState = Lifecycle.State.CREATED
            f.access.value = if (billing) f.access.value.copy(mode = CredentialMode.OPENAI_API_KEY, modeRevision = 1)
                else f.access.value.copy(client = "other", generation = 1, modeRevision = 2)
            runCurrent(); val current = vm.state.value
            f.discoveryGate!!.complete(Unit); runCurrent(); h.lifecycle.currentState = Lifecycle.State.RESUMED; runCurrent()
            assertEquals(current, vm.state.value); assertEquals("model-${f.access.value.modeRevision}", current.selectedSlug)
            assertEquals(0, f.generations)
        }
    }
}
