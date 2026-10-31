package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationHistoryTest {
    private val models = mutableListOf<InferenceViewModel>()
    private val access = MutableStateFlow(InferenceAccessState(client = "a", usable = true))
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private class Store : ConversationStore {
        val owners = mutableMapOf<String, String>(); val entries = mutableMapOf<String, ConversationEntry>()
        val deleted = mutableSetOf<String>(); var gate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun owner(identityHash: String) = owners.getOrPut(identityHash) { UUID.randomUUID().toString() }
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            saveGate?.let { withContext(NonCancellable) { it.await() } }
            require(entry.id !in deleted)
            require(entry.revision == (entries[entry.id]?.revision ?: 0))
            return entry.copy(revision = entry.revision + 1).also { entries[it.id] = it }
        }
        override suspend fun load(id: String, owner: String): ConversationEntry? {
            gate?.let { withContext(NonCancellable) { it.await() } }
            return entries[id]?.takeIf { it.owner == owner }
        }
        override suspend fun list(owner: String) = entries.values.filter { it.owner == owner }.sortedByDescending { it.updated }
            .map { ConversationSummary(it.id, it.title, it.model, it.route, it.updated, it.revision) }
        override suspend fun rename(id: String, owner: String, revision: Long, title: String) {
            val e = checkNotNull(entries[id]); require(e.owner == owner && e.revision == revision)
            require(title.isNotBlank() && title.length <= 120)
            entries[id] = e.copy(title = title, revision = revision + 1)
        }
        override suspend fun delete(id: String, owner: String, revision: Long) {
            val e = checkNotNull(entries[id]); require(e.owner == owner && e.revision == revision)
            entries.remove(id); deleted.add(id)
        }
    }
    private class Provider : AiProvider {
        var offline = false; val inputs = mutableListOf<List<JsonObject>>()
        override suspend fun models(credential: CredentialLease): List<AiModel> {
            if (offline) throw ApiFailure(ApiProblem.NETWORK)
            return listOf(AiModel("sol", "Sol"), AiModel("terra", "Terra"))
        }
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("not proof")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            inputs.add(input); onEvent(ResponseEvent.Completed("next answer"))
        }
    }
    private fun create(store: Store, provider: Provider = Provider(), local: Boolean = false): InferenceViewModel {
        val session = InferenceSession({ CredentialLease(access.value.client!!, access.value.generation, "synthetic", Long.MAX_VALUE,
            access.value.mode, access.value.modeRevision) }, provider) { it.clientId == access.value.client && it.generation == access.value.generation }
        return InferenceViewModel(access, session, { "sol" }, conversationStore = store,
            conversationIdentity = { it.clientId.repeat(64) },
            localConversationIdentity = if (local) { a -> a.client!!.repeat(64) } else null)
            .also { models += it }
    }
    private suspend fun initial(store: Store, model: String = "sol", route: String = "CHATGPT_PLAN"): ConversationEntry {
        val c = ChatConversation().apply { draft("existing question"); begin(); complete(StreamResult().apply { accept(ResponseEvent.Completed("existing answer")); end() }) }
        return store.save(ConversationEntry(owner = store.owner("a".repeat(64)), route = route, model = model,
            title = "Old chat", updated = 1, snapshot = c.snapshot()))
    }
    @Test fun startupReopensCompletedChatWithoutSendingAndExplicitSendContinues() = runTest {
        val store = Store(); initial(store); val provider = Provider(); val model = create(store, provider); runCurrent()
        assertEquals(listOf("existing question", "existing answer"), model.chat.value.messages.map { it.text })
        assertTrue(provider.inputs.isEmpty()); assertFalse(model.chat.value.busy)
        model.draft("follow up"); model.send(); runCurrent()
        assertEquals(listOf("existing question", "existing answer", "follow up"), provider.inputs.single().map { it["content"]!!.jsonPrimitive.content })
    }
    @Test fun renameSurvivesRecreationAndDeleteCannotBeRestored() = runTest {
        val store = Store(); val old = initial(store); val model = create(store); runCurrent()
        model.renameChat(old.id, "Renamed"); runCurrent()
        assertEquals("Renamed", model.history.value.entries.single().title)
        val recreated = create(store); runCurrent(); assertEquals("Renamed", recreated.history.value.entries.single().title)
        recreated.deleteChat(old.id); runCurrent()
        assertTrue(recreated.history.value.entries.isEmpty()); assertTrue(recreated.chat.value.messages.isEmpty())
        val afterDelete = create(store); runCurrent(); assertTrue(afterDelete.chat.value.messages.isEmpty())
    }
    @Test fun wrongModelIsReadOnlyUntilExplicitSelectionAndReopen() = runTest {
        val store = Store(); val old = initial(store, "terra"); val provider = Provider(); val model = create(store, provider); runCurrent()
        assertNotNull(model.chat.value.readOnlyReason)
        model.draft("must not send"); model.send(); runCurrent(); assertTrue(provider.inputs.isEmpty())
        model.select("terra"); runCurrent(); model.openChat(old.id); runCurrent()
        assertNull(model.chat.value.readOnlyReason)
        model.draft("explicit continuation"); model.send(); runCurrent(); assertEquals(1, provider.inputs.size)
    }
    @Test fun billingMismatchNeverSendsOrSwitchesRoute() = runTest {
        val store = Store(); initial(store, route = "OPENAI_API_KEY"); val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("must not send"); model.send(); runCurrent()
        assertNotNull(model.chat.value.readOnlyReason); assertTrue(provider.inputs.isEmpty())
        assertEquals(io.github.grassified.wristprompt.auth.CredentialMode.CHATGPT_PLAN, model.state.value.mode)
    }
    @Test fun localHistoryRemainsReadableWhenCatalogIsOffline() = runTest {
        val store = Store(); initial(store); val provider = Provider().apply { offline = true }; val model = create(store, provider, local = true); runCurrent()
        assertEquals(2, model.chat.value.messages.size); assertNotNull(model.chat.value.readOnlyReason)
        assertEquals(ApiProblem.NETWORK, model.state.value.problem); assertTrue(provider.inputs.isEmpty())
    }
    @Test fun newChatAndRefreshingHistoryNeverAutoReopenOrSend() = runTest {
        val store = Store(); initial(store); val provider = Provider(); val model = create(store, provider); runCurrent()
        model.newChat(); model.refreshHistory(); runCurrent()
        assertTrue(model.chat.value.messages.isEmpty()); assertEquals(1, model.history.value.entries.size)
        assertTrue(provider.inputs.isEmpty())
    }
    @Test fun accountChangeHidesOtherOwnerAndRejectsOldRenameResult() = runTest {
        val store = Store(); val old = initial(store); val model = create(store); runCurrent()
        access.value = access.value.copy(client = "b", generation = 1); runCurrent()
        assertTrue(model.history.value.entries.isEmpty()); assertTrue(model.chat.value.messages.isEmpty())
        model.renameChat(old.id, "wrong owner"); runCurrent()
        assertEquals("Old chat", store.entries[old.id]!!.title)
    }
    @Test fun lateOpenCannotReplaceNewChatOrNavigateAfterCancellation() = runTest {
        val store = Store(); val old = initial(store); val model = create(store); runCurrent()
        store.gate = CompletableDeferred(); var opened = false
        model.openChat(old.id) { opened = true }; runCurrent(); model.newChat()
        store.gate!!.complete(Unit); runCurrent()
        assertFalse(opened); assertTrue(model.chat.value.messages.isEmpty())
        assertFalse(model.history.value.loading)
    }
    @Test fun oversizedRenamePreservesSavedTitleAndReportsError() = runTest {
        val store = Store(); val old = initial(store); val model = create(store); runCurrent()
        model.renameChat(old.id, "x".repeat(121)); runCurrent()
        assertEquals("Old chat", store.entries[old.id]!!.title); assertNotNull(model.history.value.error)
    }
    @Test fun openingImmediatelyAfterStopWaitsForQueuedDurableCheckpoint() = runTest {
        val store = Store(); val old = initial(store); val provider = Provider(); val model = create(store, provider); runCurrent()
        store.saveGate = CompletableDeferred()
        model.draft("stopped before dispatch"); model.send(); runCurrent(); model.stop()
        model.openChat(old.id); runCurrent()
        assertTrue(model.history.value.loading)
        store.saveGate!!.complete(Unit); runCurrent()
        assertFalse(model.history.value.loading); assertNull(model.history.value.error)
        assertEquals(ChatMessageStatus.EXCLUDED, model.chat.value.messages.last().status)
        assertTrue(provider.inputs.isEmpty())
    }
}
