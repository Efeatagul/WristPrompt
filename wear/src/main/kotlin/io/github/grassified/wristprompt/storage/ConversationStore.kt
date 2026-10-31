package io.github.grassified.wristprompt.storage

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import io.github.grassified.wristprompt.openai.ConversationSnapshot
import kotlinx.serialization.json.*
import java.util.UUID

class ConversationStorageException : IllegalStateException("Conversation storage unavailable")

data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(), val owner: String, val route: String, val model: String,
    val title: String, val updated: Long, val revision: Long = 0, val snapshot: ConversationSnapshot,
) { override fun toString() = SafeDiagnostics.REDACTED }

data class ConversationSummary(val id: String, val title: String, val model: String, val route: String,
    val updated: Long, val revision: Long) { override fun toString() = SafeDiagnostics.REDACTED }

interface ConversationStore {
    suspend fun owner(identityHash: String): String
    suspend fun save(entry: ConversationEntry): ConversationEntry
    suspend fun load(id: String, owner: String): ConversationEntry?
    suspend fun list(owner: String): List<ConversationSummary>
    suspend fun delete(id: String, owner: String, revision: Long)
    suspend fun rename(id: String, owner: String, revision: Long, title: String)
}

/** Strict bounded codec. Unknown versions/types are errors, never silent data loss. */
object ConversationCodec {
    const val MAX_ENTRY_BYTES = 1_048_576
    val json = Json { encodeDefaults = true }
    fun validate(entry: ConversationEntry) {
        require(UUID.fromString(entry.id).toString() == entry.id)
        require(UUID.fromString(entry.owner).toString() == entry.owner)
        require(entry.route in setOf("CHATGPT_PLAN", "OPENAI_API_KEY"))
        require(entry.model.length in 1..256 && entry.title.isNotBlank() && entry.title.length <= 120)
        require(entry.updated >= 0 && entry.revision >= 0)
        entry.snapshot.validate()
    }
    fun encode(entry: ConversationEntry): Pair<String, String> {
        validate(entry)
        val body = json.encodeToString(ConversationSnapshot.serializer(), entry.snapshot.copy(replay = emptyList()))
        val replay = JsonArray(entry.snapshot.replay).toString()
        require(body.encodeToByteArray().size + replay.encodeToByteArray().size <= MAX_ENTRY_BYTES)
        return body to replay
    }
    fun decode(body: String, replay: String): ConversationSnapshot {
        require(body.encodeToByteArray().size + replay.encodeToByteArray().size <= MAX_ENTRY_BYTES)
        requireBoundedDepth(body); requireBoundedDepth(replay)
        val snapshot = json.decodeFromString(ConversationSnapshot.serializer(), body)
        require(snapshot.replay.isEmpty())
        return snapshot.copy(replay = Json.parseToJsonElement(replay).jsonArray.map { it.jsonObject }).also { it.validate() }
    }
    private fun requireBoundedDepth(source: String) {
        var depth = 0; var quoted = false; var escaped = false
        source.forEach { c ->
            if (escaped) { escaped = false }
            else if (quoted && c == '\\') escaped = true
            else if (c == '"') quoted = !quoted
            else if (!quoted) when (c) {
                '{', '[' -> { depth++; require(depth <= 32) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }
}
