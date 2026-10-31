package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Durable domain data, deliberately independent of Compose and ChatPresentation. */
@Serializable sealed interface ContentPart {
    @Serializable data class Text(val text: String) : ContentPart { override fun toString() = SafeDiagnostics.REDACTED }
    @Serializable data class ImageAttachment(val attachmentId: String) : ContentPart { override fun toString() = SafeDiagnostics.REDACTED }
}

@Serializable data class StoredMessage(
    val id: Long, val role: String, val status: String, val parts: List<ContentPart>,
) { override fun toString() = SafeDiagnostics.REDACTED }

@Serializable data class ConversationSnapshot(
    val version: Int = 1,
    val messages: List<StoredMessage>,
    val replay: List<JsonObject>,
    val attempts: Int,
    val nextId: Long,
    val partial: String = "",
    val terminal: String = "EMPTY",
) {
    override fun toString() = SafeDiagnostics.REDACTED
    fun validate() {
        require(version == 1 && attempts in 0..ChatConversation.MAX_ATTEMPTS && nextId in 0..(Long.MAX_VALUE - 48))
        require(messages.size <= ChatConversation.MAX_ATTEMPTS * 2)
        require(messages.map { it.id }.distinct().size == messages.size)
        require(messages.zipWithNext().all { (a, b) -> a.id < b.id })
        require(messages.all { it.id in 1..nextId && it.role in setOf("USER", "ASSISTANT") &&
            it.status in setOf("PENDING", "COMPLETED", "EXCLUDED") && it.parts.isNotEmpty() && it.parts.size <= 9 })
        require(messages.all { m -> m.parts.all { part -> when (part) {
            is ContentPart.Text -> part.text.length <= ResponsesProtocol.MAX_TEXT_CHARS
            is ContentPart.ImageAttachment -> part.attachmentId.matches(Regex("[a-f0-9-]{36}"))
        } } })
        require(terminal in ChatStage.entries.map { it.name })
        require(partial.length <= ResponsesProtocol.MAX_TEXT_CHARS)
        require(JsonArray(replay).toString().encodeToByteArray().size <= MAX_REPLAY_BYTES)
        // Every completed display pair must have exactly one complete replay exchange.
        val completed = messages.filter { it.status == "COMPLETED" }
        require(completed.size % 2 == 0)
        require(completed.chunked(2).all { it[0].role == "USER" && it[1].role == "ASSISTANT" })
        val pending = messages.filter { it.status == "PENDING" }
        require(pending.size <= 1 && pending.all { it.role == "USER" && it == messages.last() })
        var users = 0
        var hasAssistant = false
        replay.forEach { item ->
            if (item["role"]?.jsonPrimitive?.content == "user") {
                require(users == 0 || hasAssistant)
                require(item["content"] is JsonPrimitive && item["content"]!!.jsonPrimitive.isString)
                require(item.keys == setOf("role", "content"))
                val display = completed.getOrNull(users * 2) ?: error("Invalid replay")
                require(display.parts.all { it is ContentPart.Text })
                require(item["content"]!!.jsonPrimitive.content == display.parts.joinToString("") { (it as ContentPart.Text).text })
                users++; hasAssistant = false
            } else {
                require(users > 0)
                validateReplayOutput(listOf(item))
                if (item["role"]?.jsonPrimitive?.content == "assistant") hasAssistant = true
            }
        }
        require(users == completed.size / 2 && (users == 0 || hasAssistant))
        require(attempts >= messages.count { it.role == "USER" })
    }
    companion object { const val MAX_REPLAY_BYTES = ChatConversation.MAX_CONTEXT_BYTES + ResponsesProtocol.MAX_BODY_BYTES }
}

internal fun validateReplayOutput(output: List<JsonObject>) {
    require(output.isNotEmpty())
    require(output.all { item -> when (item["type"]?.jsonPrimitive?.content) {
        "reasoning" -> item["encrypted_content"]?.jsonPrimitive?.let { it.isString && it.content.isNotBlank() } == true
        "message" -> item["role"]?.jsonPrimitive?.content == "assistant" &&
            item["status"]?.jsonPrimitive?.content.let { it == null || it == "completed" } &&
            item["content"]?.jsonArray?.all { it.jsonObject["type"]?.jsonPrimitive?.content in setOf("output_text", "refusal") } == true
        null -> item["role"]?.jsonPrimitive?.content == "assistant" && item["content"]?.jsonPrimitive?.isString == true
        else -> false
    } })
}
