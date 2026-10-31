package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.storage.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ConversationPersistenceTest {
    private fun result(text: String, items: List<JsonObject>? = null) = StreamResult().apply {
        accept(ResponseEvent.Completed(text, items)); end()
    }
    private fun completed() = ChatConversation().apply { draft("user text"); begin(); complete(result("answer")) }
    private fun entry(snapshot: ConversationSnapshot) = ConversationEntry(owner = UUID.randomUUID().toString(),
        route = "CHATGPT_PLAN", model = "synthetic", title = "Title", updated = 1, snapshot = snapshot)
    private fun roundtrip(c: ChatConversation): ConversationSnapshot {
        val (display, replay) = ConversationCodec.encode(entry(c.snapshot()))
        return ConversationCodec.decode(display, replay)
    }
    @Test fun completedExchangeSurvivesSerializationAndContinuesExactly() {
        val restored = ChatConversation().apply { restore(roundtrip(completed())) }
        assertEquals(listOf("user text", "answer"), restored.state.messages.map { it.text })
        assertEquals("", restored.state.draft); assertFalse(restored.state.busy)
        restored.draft("next")
        assertEquals(listOf("user text", "answer", "next"), restored.begin()!!.map { it["content"]!!.jsonPrimitive.content })
    }
    @Test fun interruptedRequestRestoresAsExcludedWithoutResendingOrReplayingIt() {
        val c = completed(); c.draft("interrupted"); c.begin(); c.progress("uncommitted streaming")
        val snapshot = roundtrip(c)
        assertEquals("", snapshot.partial)
        val restored = ChatConversation().apply { restore(snapshot) }
        assertFalse(restored.state.busy); assertEquals(ChatMessageStatus.EXCLUDED, restored.state.messages.last().status)
        assertEquals(ApiProblem.INCOMPLETE, restored.state.problem)
        restored.draft("next"); assertEquals(3, restored.begin()!!.size)
    }
    @Test fun stoppedPartialIsDisplayOnlyAfterRestart() {
        val c = completed(); c.draft("stopped"); c.begin(); c.progress("partial"); c.fail(null, true)
        val restored = ChatConversation().apply { restore(roundtrip(c)) }
        assertEquals("partial", restored.state.partial)
        restored.draft("next"); assertEquals(3, restored.begin()!!.size)
    }
    @Test fun opaqueReasoningItemsSurviveWithoutFlattening() {
        val opaque = Json.parseToJsonElement("""{"type":"reasoning","encrypted_content":"synthetic-opaque"}""").jsonObject
        val assistant = buildJsonObject { put("role", "assistant"); put("content", "answer") }
        val c = ChatConversation().apply { draft("question"); begin(); complete(result("answer", listOf(opaque, assistant))) }
        val restored = ChatConversation().apply { restore(roundtrip(c)); draft("next") }
        assertEquals(opaque, restored.begin()!![1])
    }
    @Test fun unknownVersionMalformedReplayAndMismatchedUserAreRejected() {
        val c = completed(); val (body, replay) = ConversationCodec.encode(entry(c.snapshot()))
        assertThrows(Exception::class.java) { ConversationCodec.decode(body.replace("\"version\":1", "\"version\":99"), replay) }
        assertThrows(Exception::class.java) { ConversationCodec.decode(body, "[]") }
        assertThrows(Exception::class.java) { ConversationCodec.decode(body, replay.replace("user text", "wrong user")) }
        assertThrows(Exception::class.java) { ConversationCodec.decode("{broken", replay) }
        assertThrows(Exception::class.java) { ConversationCodec.decode(body, "[".repeat(100) + "]".repeat(100)) }
        assertThrows(Exception::class.java) { ConversationCodec.decode(body, "x".repeat(ConversationCodec.MAX_ENTRY_BYTES + 1)) }
    }
    @Test fun unsupportedToolReplayAndDuplicateIdsFailClosed() {
        val snapshot = completed().snapshot()
        assertThrows(Exception::class.java) { snapshot.copy(nextId = Long.MAX_VALUE).validate() }
        assertThrows(Exception::class.java) { snapshot.copy(messages = snapshot.messages + snapshot.messages.first()).validate() }
        assertThrows(Exception::class.java) { snapshot.copy(replay = snapshot.replay + buildJsonObject { put("type", "function_call") }).validate() }
    }
    @Test fun draftAndTokenProgressDoNotProducePersistentContent() {
        val c = ChatConversation(); c.draft("draft")
        assertTrue(c.snapshot().messages.isEmpty())
        c.begin(); val begin = c.snapshot()
        repeat(100) { c.progress("token $it") }
        assertEquals(begin.copy(terminal = "RECEIVING"), c.snapshot())
    }
    @Test fun restoredAttemptLimitAndIdsArePreserved() {
        val c = ChatConversation()
        repeat(12) { c.draft("q$it"); c.begin(); c.fail(null, true) }
        val restored = ChatConversation().apply { restore(roundtrip(c)); draft("no more") }
        assertNull(restored.begin()); assertTrue(restored.state.limitReached)
    }
    @Test fun dtoStringFormsNeverExposeConversationContent() {
        val snapshot = completed().snapshot()
        listOf(snapshot.toString(), snapshot.messages.first().toString(), snapshot.messages.first().parts.first().toString(), entry(snapshot).toString())
            .forEach { assertFalse(it.contains("user text")); assertFalse(it.contains("answer")) }
    }
    @Test fun writerUsesRevisionsAndDoesNotRollBackCommittedReplayAfterLateStop() = runTest {
        val fake = MemoryStore(); val c = ChatConversation(); c.draft("q"); c.begin()
        val pending = c.snapshot(); val writer = ConversationWriter(fake, entry(pending))
        writer.write(pending); c.complete(result("a")); writer.write(c.snapshot())
        writer.write(pending.copy(terminal = "CANCELLED"))
        assertEquals(2, fake.saved.size); assertEquals(2L, writer.entry.revision)
        assertEquals(2, fake.saved.last().snapshot.replay.size)
    }
    @Test fun failedCommitDoesNotAdvanceWriterRevision() = runTest {
        val fake = MemoryStore(); val snapshot = completed().snapshot(); val writer = ConversationWriter(fake, entry(snapshot))
        fake.fail = true
        try { writer.write(snapshot); fail("Expected storage error") } catch (_: ConversationStorageException) { }
        assertEquals(0L, writer.entry.revision)
    }
    private class MemoryStore : ConversationStore {
        val saved = mutableListOf<ConversationEntry>(); var fail = false
        override suspend fun owner(identityHash: String) = error("unused")
        override suspend fun save(entry: ConversationEntry): ConversationEntry {
            if (fail) throw ConversationStorageException()
            require(entry.revision == saved.size.toLong())
            return entry.copy(revision = entry.revision + 1).also { saved.add(it) }
        }
        override suspend fun load(id: String, owner: String): ConversationEntry? = null
        override suspend fun list(owner: String) = emptyList<ConversationSummary>()
        override suspend fun delete(id: String, owner: String, revision: Long) = error("unused")
        override suspend fun rename(id: String, owner: String, revision: Long, title: String) = error("unused")
    }
}
