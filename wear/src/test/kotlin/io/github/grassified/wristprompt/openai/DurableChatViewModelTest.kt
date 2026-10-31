package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DurableChatViewModelTest {
    private var vm: InferenceViewModel? = null
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { vm?.viewModelScope?.cancel(); Dispatchers.resetMain() }
    private class Store : ConversationStore {
        val owner = UUID.randomUUID().toString()
        val writes = mutableListOf<ConversationEntry>()
        var failAt = -1
        override suspend fun owner(identityHash: String) = owner
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (writes.size == failAt) throw ConversationStorageException()
            return entry.copy(revision = entry.revision + 1).also { writes += it }
        }
        override suspend fun load(id: String, owner: String): ConversationEntry? = null
        override suspend fun list(owner: String) = emptyList<ConversationSummary>()
        override suspend fun delete(id: String, owner: String, revision: Long) = error("unused")
        override suspend fun rename(id: String, owner: String, revision: Long, title: String) = error("unused")
    }
    private class Provider : AiProvider {
        var requests = 0
        var action: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { emit ->
            repeat(50) { emit(ResponseEvent.Delta("x")) }; emit(ResponseEvent.Completed("done"))
        }
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("synthetic", "Synthetic"))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("not proof")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            requests++; action(onEvent)
        }
    }
    private fun create(store: Store, provider: Provider): InferenceViewModel {
        val access = MutableStateFlow(InferenceAccessState(client = "synthetic-client", usable = true))
        val lease = CredentialLease("synthetic-client", 0, "synthetic-token", Long.MAX_VALUE)
        return InferenceViewModel(access, InferenceSession({ lease }, provider) { true },
            conversationStore = store, conversationIdentity = { "a".repeat(64) }).also { vm = it }
    }
    @Test fun completedTurnWritesBeginAndCompletionOnlyDespiteFiftyDeltas() = runTest {
        val store = Store(); val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("question"); model.send(); runCurrent()
        assertEquals(2, store.writes.size)
        assertTrue(store.writes.first().snapshot.replay.isEmpty())
        assertEquals(2, store.writes.last().snapshot.replay.size)
        assertEquals(ChatStage.COMPLETED, model.chat.value.stage)
    }
    @Test fun beginStorageFailureDoesNotSendInference() = runTest {
        val store = Store().apply { failAt = 0 }; val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("question"); model.send(); runCurrent()
        assertEquals(0, provider.requests); assertEquals(ApiProblem.CHAT_STORAGE, model.chat.value.problem)
    }
    @Test fun completionStorageFailureLeavesDurableReplayEmptyAndShowsFailure() = runTest {
        val store = Store().apply { failAt = 1 }; val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("question"); model.send(); runCurrent()
        assertEquals(1, provider.requests); assertEquals(1, store.writes.size)
        assertTrue(store.writes.single().snapshot.replay.isEmpty())
        assertEquals(ApiProblem.CHAT_STORAGE, model.chat.value.problem)
        assertFalse(model.chat.value.stage == ChatStage.COMPLETED)
    }
    @Test fun stopStoresExcludedPartialAndNeverReplay() = runTest {
        val store = Store(); val provider = Provider().apply { action = { emit -> emit(ResponseEvent.Delta("partial")); awaitCancellation() } }
        val model = create(store, provider); runCurrent(); model.draft("question"); model.send(); runCurrent(); model.stop(); runCurrent()
        assertEquals(ChatStage.CANCELLED, model.chat.value.stage)
        assertEquals("partial", store.writes.last().snapshot.partial)
        assertTrue(store.writes.last().snapshot.replay.isEmpty())
        assertEquals("EXCLUDED", store.writes.last().snapshot.messages.single().status)
    }
}
