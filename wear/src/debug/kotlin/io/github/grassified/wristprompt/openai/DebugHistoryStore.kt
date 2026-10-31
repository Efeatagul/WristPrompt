package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.storage.*
import java.util.UUID

/** Synthetic memory store for visible history interaction; no Android files or credentials. */
internal class DebugHistoryStore : ConversationStore {
    private val owner = UUID(0, 1).toString()
    private val entries = linkedMapOf<String, ConversationEntry>()
    private val deleted = mutableSetOf<String>()
    init {
        repeat(25) { index ->
            val chat = ChatConversation().apply {
                draft("Synthetic saved question ${index + 1}"); begin()
                complete(StreamResult().apply { accept(ResponseEvent.Completed("# Saved answer ${index + 1}\n\nThis is synthetic saved conversation text.")); end() })
            }
            val id = UUID(0, index.toLong() + 10).toString()
            entries[id] = ConversationEntry(id, owner, "CHATGPT_PLAN", "debug-1",
                "Chat ${index + 1}: School summary and coding notes", index.toLong(), 1, chat.snapshot())
        }
    }
    override suspend fun owner(identityHash: String) = owner
    override suspend fun save(entry: ConversationEntry): ConversationEntry {
        require(entry.owner == owner && entry.id !in deleted && entry.revision == (entries[entry.id]?.revision ?: 0))
        return entry.copy(revision = entry.revision + 1).also { entries[entry.id] = it }
    }
    override suspend fun load(id: String, owner: String) = entries[id]?.takeIf { it.owner == owner }
    override suspend fun list(owner: String) = entries.values.filter { it.owner == owner }.sortedByDescending { it.updated }
        .map { ConversationSummary(it.id, it.title, it.model, it.route, it.updated, it.revision) }
    override suspend fun delete(id: String, owner: String, revision: Long) {
        val entry = checkNotNull(load(id, owner)); require(entry.revision == revision)
        entries.remove(id); deleted.add(id)
    }
    override suspend fun rename(id: String, owner: String, revision: Long, title: String) {
        val entry = checkNotNull(load(id, owner)); require(entry.revision == revision && title.isNotBlank() && title.length <= 120)
        entries[id] = entry.copy(title = title, revision = revision + 1)
    }
}
