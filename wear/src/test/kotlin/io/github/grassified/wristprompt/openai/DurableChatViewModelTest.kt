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
        var completing: (suspend () -> Unit)? = null
        override suspend fun owner(identityHash: String) = owner
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (entry.snapshot.terminal == "COMPLETED") completing?.invoke()
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
        val inputs = mutableListOf<List<JsonObject>>()
        var action: suspend (suspend (ResponseEvent) -> Unit) -> Unit = { emit ->
            repeat(50) { emit(ResponseEvent.Delta("x")) }; emit(ResponseEvent.Completed("x".repeat(50)))
        }
        override suspend fun models(credential: CredentialLease) = listOf(AiModel("synthetic", "Synthetic"))
        override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("not proof")
        override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
            requests++; inputs += input; action(onEvent)
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
        assertEquals(ChatStage.FAILED, model.chat.value.stage)
        assertEquals("x".repeat(50), model.chat.value.partial)
        assertEquals(ChatMessageStatus.EXCLUDED, model.chat.value.messages.single().status)
        assertTrue(model.chat.value.messages.none { it.role == ChatRole.ASSISTANT })
        advanceUntilIdle()
        assertEquals(1, provider.requests) // Neither failure nor recovery observation retries inference.
        store.failAt = -1
        model.draft("explicit next question"); model.send(); runCurrent()
        assertEquals(2, provider.requests)
        assertEquals(1, provider.inputs.last().size) // Failed turn never entered in-memory replay.
        assertEquals(ChatStage.COMPLETED, model.chat.value.stage)
    }
    @Test fun completionStorageFailurePreservesPreviouslyCommittedReplay() = runTest {
        val store = Store(); val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("committed question"); model.send(); runCurrent()
        val committed = store.writes.last().snapshot.replay
        store.failAt = 3 // Second turn's begin succeeds; its completion fails.
        model.draft("unsaved question"); model.send(); runCurrent()
        assertEquals(2, provider.requests)
        assertEquals(3, store.writes.size)
        assertEquals(committed, store.writes.last().snapshot.replay)
        assertEquals(ApiProblem.CHAT_STORAGE, model.chat.value.problem)
        assertEquals("x".repeat(50), model.chat.value.partial)
        assertEquals(1, model.chat.value.messages.count { it.role == ChatRole.ASSISTANT })
        assertEquals(ChatMessageStatus.EXCLUDED, model.chat.value.messages.last().status)
        advanceUntilIdle()
        assertEquals(2, provider.requests)
        store.failAt = -1
        model.draft("explicit later question"); model.send(); runCurrent()
        assertEquals(committed, provider.inputs.last().dropLast(1))
        assertEquals(ChatStage.COMPLETED, model.chat.value.stage)
    }
    @Test fun stopStoresExcludedPartialAndNeverReplay() = runTest {
        val store = Store(); val provider = Provider().apply { action = { emit -> emit(ResponseEvent.Delta("partial")); awaitCancellation() } }
        val model = create(store, provider); runCurrent(); model.draft("question"); model.send(); runCurrent(); model.stop(); runCurrent()
        assertEquals(ChatStage.CANCELLED, model.chat.value.stage)
        assertEquals("partial", store.writes.last().snapshot.partial)
        assertTrue(store.writes.last().snapshot.replay.isEmpty())
        assertEquals("EXCLUDED", store.writes.last().snapshot.messages.single().status)
    }
    @Test fun terminalReceivedButSessionNotReturnedStopStillWins() = runTest {
        val terminal = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = Store(); val provider = Provider().apply { action = { emit ->
            emit(ResponseEvent.Completed("candidate")); terminal.complete(Unit)
            withContext(NonCancellable) { release.await() }
        } }
        val model = create(store, provider); runCurrent(); model.draft("question"); model.send()
        terminal.await(); model.stop(); release.complete(Unit); runCurrent()
        assertEquals(ChatStage.CANCELLED, model.chat.value.stage)
        assertTrue(store.writes.all { it.snapshot.replay.isEmpty() })
        assertEquals("EXCLUDED", store.writes.last().snapshot.messages.single().status)
    }
    @Test fun stopAfterCompletionOwnershipCannotEraseCompletionOrItsPublication() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = Store().apply { completing = { entered.complete(Unit); release.await() } }
        val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("question"); model.send(); entered.await(); model.stop()
        release.complete(Unit); runCurrent()
        assertEquals(ChatStage.COMPLETED, model.chat.value.stage)
        assertEquals(2, store.writes.last().snapshot.replay.size)
        assertEquals(2, store.writes.size); assertEquals(1, provider.requests)
    }
    @Test fun stopDuringOwnedCommitCannotSuppressSaveFailureRollback() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = Store().apply { completing = { entered.complete(Unit); release.await(); throw ConversationStorageException() } }
        val provider = Provider(); val model = create(store, provider); runCurrent()
        model.draft("question"); model.send(); entered.await(); model.stop()
        release.complete(Unit); runCurrent()
        assertEquals(ChatStage.FAILED, model.chat.value.stage)
        assertEquals(ApiProblem.CHAT_STORAGE, model.chat.value.problem)
        assertTrue(store.writes.single().snapshot.replay.isEmpty())
        assertEquals(ChatMessageStatus.EXCLUDED, model.chat.value.messages.single().status)
        store.completing = null
        model.draft("explicit next"); model.send(); runCurrent()
        assertEquals(1, provider.inputs.last().size)
        assertEquals(2, provider.requests)
    }
}
