package io.github.grassified.wristprompt.ui

import androidx.lifecycle.ViewModelStore
import io.github.grassified.wristprompt.openai.*
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.WatchAuthState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShellInteractionTest {
    private lateinit var vm: InferenceViewModel
    private lateinit var navigation: ShellNavigationViewModel
    private lateinit var input: ComposerInteraction
    private lateinit var shell: ShellInteraction
    private val store = ViewModelStore()
    private var launches = 0
    private var discoveries = 0
    private var generations = 0
    private var cancellations = 0
    private var hold = false
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val lease = CredentialLease("synthetic", 0, "", Long.MAX_VALUE)
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> {
                discoveries++; return listOf(AiModel("technical-slug", "Authoritative name"))
            }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Chat only")
            override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
                generations++
                onEvent(ResponseEvent.Delta("Partial"))
                if (hold) try { awaitCancellation() } finally { cancellations++ }
                onEvent(ResponseEvent.Completed("Answer"))
            }
        }
        vm = InferenceViewModel(MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true)),
            InferenceSession({ lease }, provider) { true })
        navigation = ShellNavigationViewModel(); store.put("vm", vm); store.put("nav", navigation)
        input = ComposerInteraction(vm)
        shell = ShellInteraction(vm, navigation) { if (input.begin()) launches++ }
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun askLaunchesExistingPlatformInteractionAndResultNeedsExplicitSend(): Unit = runTest {
        runCurrent(); shell.ask()
        assertEquals(Surface.COMPOSER, navigation.surface.value); assertEquals(1, launches)
        input.result("Reviewed question"); runCurrent()
        assertEquals("Reviewed question", vm.chat.value.draft); assertEquals(0, generations)
        shell.send(); runCurrent()
        assertEquals(1, generations); assertEquals(Surface.CHAT, navigation.surface.value)
        assertEquals(ChatStage.COMPLETED, vm.chat.value.stage)
    }
    @Test fun askReturnsToRetainedDraftWithoutLaunchingReplacement(): Unit = runTest {
        runCurrent(); vm.draft("Keep me"); shell.ask()
        assertEquals(0, launches); assertEquals("Keep me", vm.chat.value.draft)
        assertEquals(Surface.COMPOSER, navigation.surface.value)
    }
    @Test fun destinationsAndBackDoNotReloadSendOrClearCompletedConversation(): Unit = runTest {
        runCurrent(); vm.draft("Question"); vm.send(); runCurrent()
        val chat = vm.chat.value
        for (route in listOf(Surface.MODELS, Surface.MENU, Surface.CONNECTION, Surface.SETTINGS, Surface.DETAILS, Surface.PROOF)) {
            navigation.open(route); navigation.back(); navigation.open(Surface.CHAT)
            assertEquals(chat, vm.chat.value)
        }
        assertEquals(1, discoveries); assertEquals(1, generations)
    }
    @Test fun menuNewChatUsesExistingResetAndRejectsPendingInput(): Unit = runTest {
        runCurrent(); vm.draft("Question"); vm.send(); runCurrent(); input.begin()
        navigation.open(Surface.MENU); shell.newChat(); input.result("Stale")
        assertEquals(Surface.CHAT, navigation.surface.value); assertEquals(ChatState(), vm.chat.value)
        assertEquals(1, generations); assertEquals(1, discoveries)
    }
    @Test fun stopFromSecondaryRouteCancelsGenerationAndExcludesAttempt(): Unit = runTest {
        runCurrent(); hold = true; vm.draft("Question"); vm.send(); runCurrent()
        navigation.open(Surface.MENU); navigation.open(Surface.SETTINGS)
        assertEquals(PrimaryAction.STOP, primaryAction(navigation.surface.value, vm.chat.value.busy))
        shell.stop(); runCurrent()
        assertEquals(1, cancellations); assertEquals(ChatStage.CANCELLED, vm.chat.value.stage)
        assertEquals(ChatMessageStatus.EXCLUDED, vm.chat.value.messages.single().status)
        assertEquals("Partial", vm.chat.value.partial)
    }
    @Test fun askCannotLaunchDuringGeneration(): Unit = runTest {
        runCurrent(); hold = true; vm.draft("Question"); vm.send(); runCurrent(); shell.ask()
        assertEquals(0, launches); assertEquals(Surface.CHAT, navigation.surface.value)
    }
    @Test fun emptySendDoesNotGenerate(): Unit = runTest {
        runCurrent(); shell.send(); runCurrent(); assertEquals(0, generations)
    }
    @Test fun primaryActionIsExclusiveAndStopWinsOnEverySurface() {
        for (surface in Surface.entries) {
            assertEquals(PrimaryAction.STOP, primaryAction(surface, true))
            assertEquals(PrimaryAction.STOP, primaryAction(surface, true, welcome = true))
            assertNull(primaryAction(surface, false, welcome = true))
        }
        assertEquals(PrimaryAction.ASK, primaryAction(Surface.CHAT, false))
        assertEquals(PrimaryAction.SEND, primaryAction(Surface.COMPOSER, false))
        assertNull(primaryAction(Surface.MENU, false))
    }
    @Test fun billingAndAuthoritativeModelArePresentationOnly(): Unit = runTest {
        runCurrent()
        val accounts = WatchAccountState(connected = true, planEnabled = true)
        val plan = shellPresentation(accounts, ApiKeyState(loaded = true), vm.state.value, WatchAuthState.IDLE, false)
        assertEquals("Authoritative name", plan.model)
        assertEquals("Using ChatGPT plan", billingDisclosure(plan.billing))
        val api = shellPresentation(accounts, ApiKeyState(loaded = true, mode = CredentialMode.OPENAI_API_KEY, configured = true),
            vm.state.value.copy(mode = CredentialMode.OPENAI_API_KEY), WatchAuthState.IDLE, false)
        assertEquals("OpenAI API · API billing", billingDisclosure(api.billing))
        assertEquals(0, generations); assertEquals(1, discoveries)
    }
}
