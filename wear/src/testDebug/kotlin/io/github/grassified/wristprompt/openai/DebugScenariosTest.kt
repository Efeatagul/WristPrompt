package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DebugScenariosTest {
    private val fixtures = mutableListOf<DebugScenarios>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { fixtures.forEach { it.vm.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun fixture(s: DebugScenario) = DebugScenarios(s).also { fixtures += it }
    @Test fun namesAreUniqueAndRoundTrip() {
        assertEquals(27, DebugScenario.entries.size)
        DebugScenario.entries.forEach { assertSame(it, DebugScenario.parse(it.name)) }
    }
    @Test fun unknownFailsClosed() { listOf(null, "", "streaming_long", "../SIGNED_OUT").forEach { assertNull(DebugScenario.parse(it)) } }
    @Test fun everyScenarioPreparesWithoutNetworkOrStores(): Unit = runTest {
        DebugScenario.entries.forEach { scenario ->
            val f = fixture(scenario); val job = launch { f.prepare() }; runCurrent()
            assertTrue("${scenario.name} ready", job.isCompleted)
            f.vm.viewModelScope.cancel()
        }
    }
    @Test fun errorsUseProductionMappingAndKeepBillingDistinct() {
        val plan = fixture(DebugScenario.ERROR_401_REAUTH)
        val key = fixture(DebugScenario.ERROR_401_API_KEY)
        assertEquals(ApiProblem.PERMISSION, plan.failure()!!.problem)
        assertEquals(ApiProblem.INVALID_API_KEY, key.failure()!!.problem)
        assertEquals(CredentialMode.OPENAI_API_KEY, key.mode)
        assertEquals(ApiProblem.USAGE_LIMIT, fixture(DebugScenario.ERROR_429).failure()!!.problem)
        assertEquals(ApiProblem.TEMPORARY, fixture(DebugScenario.ERROR_503).failure()!!.problem)
        assertEquals(ApiProblem.NETWORK, fixture(DebugScenario.ERROR_OFFLINE).failure()!!.problem)
    }
    @Test fun unavailableCannotSend(): Unit = runTest {
        val f = fixture(DebugScenario.MODEL_UNAVAILABLE); launch { f.prepare() }; runCurrent()
        assertNull(f.vm.state.value.selectedSlug); f.vm.send(); runCurrent(); assertEquals(0, f.generations)
        f.vm.loadModels(); runCurrent(); assertNotNull(f.vm.state.value.selectedSlug)
        f.vm.send(); runCurrent(); assertEquals(1, f.generations)
    }
    @Test fun stoppedPartialExcludedAndNextSendWorks(): Unit = runTest {
        val f = fixture(DebugScenario.STOPPED_PARTIAL); launch { f.prepare() }; runCurrent()
        assertEquals(ChatStage.CANCELLED, f.vm.chat.value.stage)
        assertFalse(f.vm.chat.value.busy); assertTrue(f.vm.chat.value.partial.isNotEmpty())
        assertEquals(ChatMessageStatus.EXCLUDED, f.vm.chat.value.messages.single().status)
        f.vm.draft("Next"); f.vm.send(); runCurrent()
        assertEquals(1, f.inputs.last().size); assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage)
    }
    @Test fun streamingUsesRealSessionAndStop(): Unit = runTest {
        val f = fixture(DebugScenario.STREAMING_LONG); launch { f.prepare() }; runCurrent()
        assertEquals(ChatStage.RECEIVING, f.vm.chat.value.stage); assertEquals(1, f.generations)
        advanceTimeBy(1200); runCurrent(); assertTrue(f.vm.chat.value.partial.length > 60)
        f.vm.stop(); advanceUntilIdle(); assertEquals(ChatStage.CANCELLED, f.vm.chat.value.stage)
        assertFalse(f.vm.chat.value.messages.any { it.role == ChatRole.ASSISTANT })
    }
    @Test fun completedUsesChatConversationHistory(): Unit = runTest {
        val f = fixture(DebugScenario.MULTI_TURN_CHAT); launch { f.prepare() }; runCurrent()
        assertEquals(8, f.vm.chat.value.messages.size)
        assertEquals(listOf(1, 3, 5, 7), f.inputs.map { it.size })
        assertTrue(f.vm.chat.value.messages.all { it.status == ChatMessageStatus.COMPLETED })
    }
    @Test fun lifecycleScenariosAdvanceOnlyAtExplicitGatesAndNeverReplay(): Unit = runTest {
        for (s in listOf(DebugScenario.ACTIVE_STREAM_RECREATION, DebugScenario.BACKGROUNDED_STREAM,
            DebugScenario.SCREEN_OFF_STYLE_RESUME)) {
            val f = fixture(s); val owner = ScenarioOwner(f); runCurrent()
            assertTrue(owner.ready); assertEquals(1, f.generations); assertEquals(1, f.discoveries)
            assertEquals("Lifecycle chunk 1. ", f.vm.chat.value.partial)
            owner.recreated = true; runCurrent()
            f.advance(); runCurrent()
            assertEquals("Lifecycle chunk 1. Lifecycle chunk 2. ", f.vm.chat.value.partial)
            f.advance(complete = true); runCurrent(); f.vm.send(); runCurrent()
            assertEquals(1, f.generations); assertEquals(1, f.completions)
            assertEquals(2, f.vm.chat.value.messages.size)
            owner.viewModelScope.cancel()
        }
    }
    @Test fun retainedOwnerDoesNotPrepareAgain(): Unit = runTest {
        for (s in listOf(DebugScenario.RECREATED_COMPLETED_STATE, DebugScenario.RECREATED_CANCELLED_STATE)) {
            val owner = ScenarioOwner(fixture(s)); runCurrent()
            assertTrue(owner.ready); val before = owner.fixture.vm.chat.value
            owner.recreated = true; runCurrent()
            assertEquals(1, owner.fixture.generations); assertEquals(before, owner.fixture.vm.chat.value)
            assertFalse(owner.fixture.vm.chat.value.busy); owner.viewModelScope.cancel()
        }
    }
}
