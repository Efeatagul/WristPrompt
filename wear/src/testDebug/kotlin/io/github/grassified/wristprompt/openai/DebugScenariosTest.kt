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
        assertEquals(84, DebugScenario.entries.size)
        DebugScenario.entries.forEach { assertSame(it, DebugScenario.parse(it.name)) }
    }
    @Test fun historyFixtureSupportsLocalActionsWithoutGenerating(): Unit = runTest {
        val f = fixture(DebugScenario.SAVED_CHATS_HISTORY); launch { f.prepare() }; runCurrent()
        assertEquals(io.github.grassified.wristprompt.ui.Surface.HISTORY, f.surface)
        assertEquals(25, f.vm.history.value.entries.size); assertEquals(0, f.generations)
        val id = f.vm.history.value.entries.first().id
        f.vm.openChat(id); runCurrent(); assertEquals(2, f.vm.chat.value.messages.size)
        f.vm.renameChat(id, "Renamed synthetic chat"); runCurrent()
        assertEquals("Renamed synthetic chat", f.vm.history.value.entries.first().title)
        f.vm.deleteChat(id); runCurrent(); assertEquals(24, f.vm.history.value.entries.size)
        assertEquals(0, f.generations)
    }
    @Test fun largeAndLongCatalogsKeepExactNamesAndOrder(): Unit = runTest {
        for ((scenario, count) in listOf(DebugScenario.SINGLE_MODEL to 1, DebugScenario.MANY_MODELS to 40, DebugScenario.THOUSAND_MODELS to 1000,
            DebugScenario.LONG_MODEL_NAMES to 3)) {
            val f = fixture(scenario); launch { f.prepare() }; runCurrent()
            val state = f.vm.state.value
            assertEquals(count, state.models.size)
            assertEquals((1..count).map { "debug-$it" }, state.models.map { it.slug })
            assertEquals("debug-1", state.selectedSlug)
            assertEquals(1, f.discoveries)
            if (scenario == DebugScenario.LONG_MODEL_NAMES) assertTrue(state.models.all { it.displayName.length > 60 })
        }
    }
    @Test fun unknownFailsClosed() { listOf(null, "", "streaming_long", "../SIGNED_OUT").forEach { assertNull(DebugScenario.parse(it)) } }
    @Test fun codeStressIsBoundedAndKeepsSurroundingProse(): Unit = runTest {
        val f = fixture(DebugScenario.MARKDOWN_CODE_STRESS); launch { f.prepare() }; runCurrent()
        val source = f.vm.chat.value.messages.last().text
        assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage)
        assertTrue(source.startsWith("Before the code."))
        assertTrue(source.endsWith("After the code."))
        assertTrue(source.length < ResponsesProtocol.MAX_TEXT_CHARS)
        assertEquals(2000, Regex("  code line\\n").findAll(source).count())
    }
    @Test fun everyScenarioPreparesWithoutNetworkOrStores(): Unit = runTest {
        DebugScenario.entries.forEach { scenario ->
            val f = fixture(scenario); val job = launch { f.prepare() }; runCurrent()
            if (scenario == DebugScenario.DIAGNOSTIC_PROOF_CURRENT_ATTEMPT) { advanceTimeBy(250); runCurrent() }
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
    @Test fun freshProcessFixtureNeverSeedsItselfOrRestoresOldWork(): Unit = runTest {
        for (scenario in DebugScenario.entries.filter { it.name.startsWith("PROCESS_RESTART_") }) {
            val old = fixture(scenario); launch { old.prepare() }; runCurrent()
            assertEquals(0, old.generations)
            launch { old.seedProcess(); old.seedProcess() }; runCurrent()
            val oldGenerations = if (scenario == DebugScenario.PROCESS_RESTART_IDLE) 0 else 1
            assertEquals(oldGenerations, old.generations)
            val fresh = fixture(scenario); launch { fresh.prepare() }; runCurrent()
            assertNotSame(old.vm, fresh.vm); assertEquals(ChatState(), fresh.vm.chat.value)
            assertEquals(0, fresh.generations); assertEquals(1, fresh.discoveries)
            assertFalse(fresh.vm.chat.value.busy); assertFalse(fresh.vm.state.value.busy)
            assertEquals("debug-1", fresh.vm.state.value.selectedSlug)
            old.vm.viewModelScope.cancel(); fresh.vm.viewModelScope.cancel()
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
    @Test fun connectivityRestorationNeverReplaysAndExplicitRecoveryExcludesFailedAttempt(): Unit = runTest {
        for (s in listOf(DebugScenario.NETWORK_LOST_BEFORE_SEND, DebugScenario.NETWORK_LOST_DURING_STREAM,
            DebugScenario.NETWORK_RECOVERED, DebugScenario.STOP_NETWORK_RACE)) {
            val f = fixture(s); launch { f.prepare() }; runCurrent()
            if (s == DebugScenario.STOP_NETWORK_RACE) { f.failAndStop(); runCurrent() }
            val failed = f.vm.chat.value
            assertTrue(failed.stage in setOf(ChatStage.FAILED, ChatStage.CANCELLED))
            assertEquals(ChatMessageStatus.EXCLUDED, failed.messages.single().status)
            f.restoreNetwork(); advanceUntilIdle()
            assertEquals(failed, f.vm.chat.value); assertEquals(1, f.generations); assertEquals(1, f.discoveries)
            f.nextRequest(); runCurrent()
            assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage); assertEquals(2, f.generations)
            assertEquals(1, f.completions); assertEquals(1, f.inputs.last().size)
        }
    }
    @Test fun modelConnectivityRestorationRequiresExplicitReload(): Unit = runTest {
        for (s in listOf(DebugScenario.MODEL_DISCOVERY_OFFLINE, DebugScenario.MODEL_DISCOVERY_RECOVERED)) {
            val f = fixture(s); launch { f.prepare() }; runCurrent()
            assertEquals(ApiProblem.NETWORK, f.vm.state.value.problem); assertTrue(f.vm.state.value.models.isEmpty())
            f.restoreNetwork(); advanceUntilIdle()
            assertEquals(1, f.discoveries); assertEquals(ProofStage.FAILED, f.vm.state.value.stage)
            f.vm.loadModels(); runCurrent()
            assertEquals(ProofStage.READY, f.vm.state.value.stage); assertEquals("debug-1", f.vm.state.value.selectedSlug)
            assertEquals(2, f.discoveries); assertEquals(0, f.generations)
        }
    }

    @Test fun phoneAbsentFixturesKeepNormalRoutesAvailableAndBridgeUntouched(): Unit = runTest {
        for (s in listOf(DebugScenario.PHONE_ABSENT, DebugScenario.WATCH_ONLY_AUTHENTICATED,
            DebugScenario.WATCH_ONLY_CHAT, DebugScenario.WATCH_ONLY_REFRESHED, DebugScenario.API_KEY_WATCH_ONLY)) {
            val f = fixture(s); launch { f.prepare() }; runCurrent()
            assertEquals(ProofStage.READY, f.vm.state.value.stage)
            val shell = io.github.grassified.wristprompt.ui.shellPresentation(f.accounts, f.key, f.vm.state.value, f.authState, false)
            assertTrue(shell.proofAvailable); assertFalse(shell.status.contains("Phone"))
            assertEquals(0, f.bridgeLookups); assertEquals(0, f.bridgeRequests)
            assertEquals(if (s == DebugScenario.WATCH_ONLY_REFRESHED) 1 else 0, f.refreshes)
            if (s == DebugScenario.WATCH_ONLY_CHAT) assertEquals(listOf(1, 3), f.inputs.map { it.size })
            f.vm.newChat(); f.vm.loadModels(); runCurrent(); f.vm.draft("Explicit watch request"); f.vm.send(); runCurrent()
            assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage)
            assertEquals(0, f.bridgeLookups); assertEquals(0, f.bridgeRequests)
        }
    }
    @Test fun reauthAbsentPhoneFixtureCannotInferOrChangeBilling(): Unit = runTest {
        val f = fixture(DebugScenario.REAUTH_REQUIRED_PHONE_ABSENT); launch { f.prepare() }; runCurrent()
        assertEquals(ApiProblem.REAUTHORIZE, f.vm.state.value.problem)
        f.vm.draft("Blocked"); f.vm.send(); f.vm.loadModels(); runCurrent()
        assertEquals(0, f.generations); assertEquals(0, f.discoveries)
        assertEquals(CredentialMode.CHATGPT_PLAN, f.mode)
        val shell = io.github.grassified.wristprompt.ui.shellPresentation(f.accounts, f.key, f.vm.state.value, f.authState, false)
        assertEquals("Sign in again to continue", shell.status); assertTrue(shell.showContinue); assertFalse(shell.proofAvailable)
        assertEquals(0, f.bridgeLookups); assertEquals(0, f.bridgeRequests)
    }
    @Test fun debugCompanionSpyActuallyFailsAndCountsLookup(): Unit = runTest {
        val f = fixture(DebugScenario.WATCH_ONLY_AUTHENTICATED)
        try { f.authBridge.request(); fail("Expected fail-if-touched") } catch (_: IllegalStateException) { }
        assertEquals(1, f.bridgeLookups); assertEquals(0, f.bridgeRequests)
    }

    @Test fun remediationFixturesReallyCompleteWithoutRepeatedOutputAndDiagnosticUsesSecondAttempt() = runTest {
        for (scenario in listOf(DebugScenario.STREAM_TO_COMPLETED_PLAIN, DebugScenario.STREAM_TO_COMPLETED_MARKDOWN,
            DebugScenario.STREAM_TO_COMPLETED_LONG)) {
            val f = fixture(scenario); launch { f.prepare() }; runCurrent()
            assertEquals(ChatStage.RECEIVING, f.vm.chat.value.stage)
            advanceTimeBy(10000); runCurrent()
            assertEquals(ChatStage.COMPLETED, f.vm.chat.value.stage)
            assertEquals(markdownFixture(scenario), f.vm.chat.value.messages.single { it.role == ChatRole.ASSISTANT }.text)
            assertEquals(1, f.generations)
            f.vm.draft("Next"); f.vm.send(); advanceTimeBy(10000); runCurrent()
            assertEquals(3, f.inputs.last().size)
            assertEquals(2, f.generations)
        }
        val f = fixture(DebugScenario.DIAGNOSTIC_PROOF_CURRENT_ATTEMPT); launch { f.prepare() }; advanceTimeBy(250); runCurrent()
        assertEquals(2, f.generations)
        assertEquals(ProofStage.SUCCEEDED, f.vm.state.value.stage)
        assertEquals("WristPrompt works.", f.vm.state.value.output)
        assertNotNull(f.vm.state.value.attemptId)
    }

}
