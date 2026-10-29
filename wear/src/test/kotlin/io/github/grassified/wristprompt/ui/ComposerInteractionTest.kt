package io.github.grassified.wristprompt.ui

import androidx.lifecycle.ViewModelStore
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerInteractionTest {
    private lateinit var vm: InferenceViewModel
    private var generations = 0
    private val store = ViewModelStore()
    private val access = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true))
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("model", "Model"), AiModel("other", "Other"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) { generations++ }
            override suspend fun stream(credential: CredentialLease, model: String, input: List<kotlinx.serialization.json.JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
                generations++; onEvent(ResponseEvent.Completed("Answer"))
            }
        }
        vm = InferenceViewModel(access,
            InferenceSession({ CredentialLease("synthetic", 0, "", Long.MAX_VALUE) }, provider) { true })
        store.put("vm", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    @Test fun recreationDiscardsOldAndRestoredCallbacksWithoutSending(): Unit = runTest {
        runCurrent(); val old = ComposerInteraction(vm); assertTrue(old.begin())
        old.discard() // DisposableEffect on old composition
        val recreated = ComposerInteraction(vm)
        old.result("Old callback"); recreated.result("Restored result")
        assertEquals("", vm.chat.value.draft); assertFalse(recreated.pending); assertEquals(0, generations)
        assertTrue(recreated.begin()); recreated.result("Reviewed draft")
        assertEquals("Reviewed draft", vm.chat.value.draft); assertEquals(0, generations)
    }
    @Test fun resultIsSingleUseAndNeverAutomaticallySends(): Unit = runTest {
        runCurrent(); val input = ComposerInteraction(vm); assertTrue(input.begin()); assertFalse(input.begin())
        input.result("First"); input.result("Duplicate")
        assertEquals("First", vm.chat.value.draft); assertEquals(0, generations)
        vm.send(); vm.send(); runCurrent(); assertEquals(1, generations)
    }
    @Test fun newChatModelAndFinalClearRejectPendingResults(): Unit = runTest {
        runCurrent()
        val input = ComposerInteraction(vm); input.begin(); vm.newChat(); input.result("Stale")
        assertEquals("", vm.chat.value.draft)
        input.begin(); vm.select("other"); runCurrent(); input.result("Wrong model")
        assertEquals("", vm.chat.value.draft)
        input.begin(); store.clear(); input.result("Cleared")
        assertEquals("", vm.chat.value.draft); assertFalse(input.begin()); assertEquals(0, generations)
    }
    @Test fun accountOrBillingSnapshotChangeRejectsResultBeforeCollectorRuns(): Unit = runTest {
        runCurrent(); val input = ComposerInteraction(vm); input.begin()
        access.value = access.value.copy(generation = 1)
        input.result("Stale account")
        assertEquals("", vm.chat.value.draft); assertEquals(0, generations)
        runCurrent()
    }
    @Test fun signedOutLocalDraftRemainsAvailableButCannotSend(): Unit = runTest {
        access.value = access.value.copy(client = null, usable = false, generation = 1)
        runCurrent(); val input = ComposerInteraction(vm)
        assertTrue(input.begin()); input.result("Local draft")
        assertEquals("Local draft", vm.chat.value.draft)
        vm.send(); runCurrent(); assertEquals(0, generations)
    }
    @Test fun cancelledResultIsConsumed(): Unit = runTest {
        runCurrent(); val input = ComposerInteraction(vm); input.begin(); input.result(null); input.result("Late")
        assertFalse(input.pending); assertEquals("", vm.chat.value.draft)
    }
}
