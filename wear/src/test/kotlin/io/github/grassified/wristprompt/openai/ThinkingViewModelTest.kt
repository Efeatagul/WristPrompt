package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ThinkingViewModelTest {
    private val vms = mutableListOf<InferenceViewModel>()
    private val access = MutableStateFlow(InferenceAccessState(client = "synthetic", usable = true))
    private val cap = ControlCapability("thinking", "openai", "a", "CHATGPT_PLAN", listOf("none", "medium", "ultra-Future"), "synthetic fixture")
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { vms.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private class Store : ConversationStore {
        val owner = UUID.randomUUID().toString(); val entries = linkedMapOf<String, ConversationEntry>()
        var fail = false; var writes = 0
        override suspend fun owner(identityHash: String) = owner
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (fail) throw ConversationStorageException()
            require(entry.revision == (entries[entry.id]?.revision ?: 0))
            val (body, replay) = ConversationCodec.encode(entry)
            return entry.copy(revision = entry.revision + 1, snapshot = ConversationCodec.decode(body, replay))
                .also { entries[it.id] = it; writes++ }
        }
        override suspend fun load(id: String, owner: String) = entries[id]?.takeIf { it.owner == owner }
        override suspend fun list(owner: String) = entries.values.filter { it.owner == owner }.sortedByDescending { it.updated }
            .map { ConversationSummary(it.id, it.title, it.model, it.route, it.updated, it.revision) }
        override suspend fun delete(id: String, owner: String, revision: Long) = error("unused")
        override suspend fun rename(id: String, owner: String, revision: Long, title: String) = error("unused")
    }
    private inner class Provider : AiProvider {
        var models = listOf(AiModel("a", "A", cap), AiModel("b", "B", cap.copy(model = "b", values = listOf("none"))), AiModel("c", "C"))
        val inputs = mutableListOf<List<JsonObject>>(); val configurations = mutableListOf<InferenceConfiguration>()
        override suspend fun models(credential: CredentialLease) = models
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("unused")
        override suspend fun stream(credential: CredentialLease, model: AiModel, input: List<JsonObject>, configuration: InferenceConfiguration,
            onEvent: suspend (ResponseEvent) -> Unit) {
            inputs += input; configurations += configuration
            repeat(25) { onEvent(ResponseEvent.Delta("x")) }; onEvent(ResponseEvent.Completed("answer"))
        }
    }
    private fun create(store: Store, provider: Provider): InferenceViewModel {
        val lease = CredentialLease("synthetic", 0, "synthetic-token", Long.MAX_VALUE)
        return InferenceViewModel(access, InferenceSession({ lease }, provider) { true }, { "a" },
            conversationStore = store, conversationIdentity = { "a".repeat(64) }).also { vms += it }
    }
    @Test fun selectionsStartupAndReopenAreInertRestartRestoresAndLaterTurnKeepsReplay() = runTest {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); runCurrent()
        vm.selectThinking("ultra-Future"); runCurrent()
        assertEquals("ultra-Future", vm.state.value.configuration.controls.single().value)
        assertEquals(1, store.writes); assertTrue(provider.inputs.isEmpty())
        vm.draft("first"); vm.send(); runCurrent()
        assertEquals(3, store.writes) // Selection + begin + completion; no token writes.
        val prior = store.entries.values.single().snapshot.replay
        val id = store.entries.keys.single()
        vm.selectThinking("none"); runCurrent()
        assertEquals(prior, store.entries[id]!!.snapshot.replay)
        assertEquals(1, provider.inputs.size)
        val restarted = create(store, provider); runCurrent()
        assertEquals("none", restarted.state.value.configuration.controls.single().value)
        assertEquals(1, provider.inputs.size)
        restarted.newChat(); restarted.openChat(id); runCurrent()
        assertEquals("none", restarted.state.value.configuration.controls.single().value)
        assertEquals(1, provider.inputs.size)
        restarted.selectThinking(null); runCurrent()
        assertEquals(InferenceConfiguration(), restarted.state.value.configuration)
        restarted.draft("second"); restarted.send(); runCurrent()
        assertEquals(prior, provider.inputs.last().dropLast(1))
        assertEquals(InferenceConfiguration(), provider.configurations.last())
        assertEquals(ChatStage.COMPLETED, restarted.chat.value.stage)
    }
    @Test fun modelChangeRebindsOnlyValidNativeValueOtherwiseVisiblyResetsToDefault() = runTest {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); runCurrent()
        vm.selectThinking("none"); runCurrent(); vm.select("b"); runCurrent()
        assertEquals("none", vm.state.value.configuration.controls.single().value)
        assertEquals("b", vm.state.value.configuration.controls.single().model)
        assertEquals(listOf("none"), vm.state.value.models.first { it.slug == "b" }.thinking!!.values)
        vm.select("a"); runCurrent(); vm.selectThinking("ultra-Future"); runCurrent(); vm.select("b"); runCurrent()
        assertEquals(InferenceConfiguration(), vm.state.value.configuration)
        assertTrue(vm.state.value.thinkingNotice!!.contains("reset to Default"))
        vm.select("c"); runCurrent()
        assertNull(vm.state.value.models.first { it.slug == "c" }.thinking)
        vm.selectThinking("high"); runCurrent()
        assertEquals(InferenceConfiguration(), vm.state.value.configuration)
        assertTrue(vm.state.value.thinkingNotice!!.contains("unsupported"))
        assertTrue(provider.inputs.isEmpty())
    }
    @Test fun staleRestoredValueRemainsVisibleAndBlocksSendUntilExplicitReselection() = runTest {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); runCurrent()
        vm.selectThinking("ultra-Future"); runCurrent()
        provider.models = listOf(AiModel("a", "A", cap.copy(values = listOf("none"))))
        val restarted = create(store, provider); runCurrent()
        assertEquals("ultra-Future", restarted.state.value.configuration.controls.single().value)
        assertTrue(restarted.state.value.thinkingNotice!!.contains("unsupported"))
        restarted.draft("question"); restarted.send(); runCurrent()
        assertTrue(provider.inputs.isEmpty()); assertEquals("question", restarted.chat.value.draft)
        assertEquals(ApiProblem.THINKING_UNSUPPORTED, restarted.chat.value.problem)
        restarted.selectThinking("none"); runCurrent(); restarted.send(); runCurrent()
        assertEquals(1, provider.inputs.size)
    }
    @Test fun failedControlCheckpointRetainsPriorSelectionAndReplay() = runTest {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); runCurrent()
        vm.selectThinking("none"); runCurrent(); vm.draft("question"); vm.send(); runCurrent()
        val saved = store.entries.values.single()
        store.fail = true; vm.selectThinking("medium"); runCurrent()
        assertEquals("none", vm.state.value.configuration.controls.single().value)
        assertEquals(saved, store.entries.values.single())
        assertTrue(vm.state.value.thinkingNotice!!.contains("could not be saved"))
        assertEquals(1, provider.inputs.size)
    }
    @Test fun catalogRefreshRevalidatesWithoutInferenceOrSilentSubstitution() = runTest {
        val store = Store(); val provider = Provider(); val vm = create(store, provider); runCurrent()
        vm.selectThinking("ultra-Future"); runCurrent()
        provider.models = listOf(AiModel("a", "A")); vm.loadModels(); runCurrent()
        assertTrue(vm.state.value.thinkingNotice!!.contains("unsupported"))
        assertEquals("ultra-Future", vm.state.value.configuration.controls.single().value)
        vm.draft("question"); vm.send(); runCurrent(); assertTrue(provider.inputs.isEmpty())
        vm.selectThinking(null); runCurrent(); vm.send(); runCurrent(); assertEquals(1, provider.inputs.size)
    }
}
