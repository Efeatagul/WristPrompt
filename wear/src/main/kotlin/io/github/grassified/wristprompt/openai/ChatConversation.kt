package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.json.*

enum class ChatRole { USER, ASSISTANT }
enum class ChatMessageStatus { PENDING, COMPLETED, EXCLUDED }
enum class ChatStage { EMPTY, COMPOSING, STARTING, RECEIVING, COMPLETED, FAILED, CANCELLED }
data class ChatMessage(val id: Long, val role: ChatRole, val text: String, val status: ChatMessageStatus = ChatMessageStatus.COMPLETED, val attachments: List<String> = emptyList(), val generated: GeneratedAttachment? = null) {
    override fun toString() = SafeDiagnostics.REDACTED
}
data class ChatState(
    val messages: List<ChatMessage> = emptyList(), val draft: String = "",
    val stage: ChatStage = ChatStage.EMPTY, val partial: String = "",
    val problem: ApiProblem? = null, val limitReached: Boolean = false,
    val readOnlyReason: String? = null,
) {
    val busy get() = stage == ChatStage.STARTING || stage == ChatStage.RECEIVING
    val canSend get() = !busy && readOnlyReason == null && !limitReached && draft.isNotBlank() && draft.length <= ChatConversation.MAX_PROMPT_CHARS
    override fun toString() = SafeDiagnostics.REDACTED
}

/** One process-memory conversation. Only whole completed exchanges enter replay history. */
class ChatConversation {
    companion object {
        const val MAX_PROMPT_CHARS = 8192
        const val MAX_ATTEMPTS = 12
        const val MAX_CONTEXT_BYTES = 196_608
    }
    var configuration = InferenceConfiguration(); private set
    fun configure(value: InferenceConfiguration) { value.validate(); check(!state.busy); configuration = value }
    var state = ChatState(); private set
    private var history = emptyList<JsonObject>()
    private var pending: JsonObject? = null
    private var pendingUserId: Long? = null
    private var nextId = 0L
    private var attempts = 0
    fun reset() { configuration = InferenceConfiguration(); history = emptyList(); pending = null; pendingUserId = null; attempts = 0; state = ChatState() }
    fun readOnly(reason: String?) { state = state.copy(readOnlyReason = reason) }
    fun snapshot(): ConversationSnapshot = ConversationSnapshot(
        messages = state.messages.map { StoredMessage(it.id, it.role.name, it.status.name, listOf(ContentPart.Text(it.text)) + it.attachments.map(ContentPart::ImageAttachment) + listOfNotNull(it.generated?.let(ContentPart::GeneratedImage))) },
        replay = history.toList(), attempts = attempts, nextId = nextId,
        partial = if (state.busy) "" else state.partial, terminal = state.stage.name, configuration = configuration,
    ).also { it.validate() }


    /** Process restoration is inert: pending work becomes excluded, never resumed. */
    fun restore(snapshot: ConversationSnapshot) {
        snapshot.validate()
        val interrupted = snapshot.messages.any { it.status == "PENDING" }
        val restored = snapshot.messages.map { m -> ChatMessage(m.id, ChatRole.valueOf(m.role),
            m.parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text },
            if (m.status == "PENDING") ChatMessageStatus.EXCLUDED else ChatMessageStatus.valueOf(m.status), m.parts.filterIsInstance<ContentPart.ImageAttachment>().map { it.attachmentId }, m.parts.filterIsInstance<ContentPart.GeneratedImage>().singleOrNull()?.reference) }
        configuration = snapshot.configuration
        history = snapshot.replay.toList(); attempts = snapshot.attempts; nextId = snapshot.nextId
        pending = null; pendingUserId = null
        state = ChatState(messages = restored,
            stage = if (interrupted) ChatStage.FAILED else if (snapshot.terminal in setOf("FAILED", "CANCELLED")) ChatStage.valueOf(snapshot.terminal)
                else if (restored.isEmpty()) ChatStage.EMPTY else ChatStage.COMPLETED,
            partial = snapshot.partial, problem = ApiProblem.INCOMPLETE.takeIf { interrupted },
            limitReached = attempts >= MAX_ATTEMPTS || size(history) > MAX_CONTEXT_BYTES)
    }
    fun draft(text: String) {
        if (!state.busy) state = state.copy(draft = text, stage = ChatStage.COMPOSING, problem = null)
    }
    fun preview(attachments: List<String> = emptyList()): List<JsonObject> = history + ImageInput.user(state.draft, attachments)
    fun begin(attachments: List<String> = emptyList()): List<JsonObject>? {
        if (!state.canSend) return null
        val user = ImageInput.user(state.draft, attachments)
        val input = history + user
        if (attempts >= MAX_ATTEMPTS || size(input) > MAX_CONTEXT_BYTES) {
            state = state.copy(limitReached = true); return null
        }
        attempts++
        pending = user
        pendingUserId = ++nextId
        state = state.copy(messages = state.messages + ChatMessage(nextId, ChatRole.USER, state.draft, ChatMessageStatus.PENDING, attachments.toList()),
            draft = "", stage = ChatStage.STARTING, partial = "", problem = null)
        return input
    }
    fun progress(text: String) {
        if (state.busy) state = state.copy(stage = ChatStage.RECEIVING, partial = text)
    }
    fun complete(result: StreamResult, generated: GeneratedAttachment? = null) {
        check(state.busy && result.completed)
        val user = checkNotNull(pending)
        val output = result.outputItems ?: listOf(message("assistant", result.output))
        // Unsupported/tool output must never become an implicit tool or be silently discarded.
        validateReplayOutput(output)
        require(size(output) <= ResponsesProtocol.MAX_BODY_BYTES)
        require(output.count { it["type"]?.jsonPrimitive?.content == "image_generation_call" } == if (generated == null) 0 else 1)
        generated?.validate()
        val next = history + user + output
        val full = size(next) > MAX_CONTEXT_BYTES || attempts >= MAX_ATTEMPTS
        history = next
        pending = null
        val messages = state.messages.map { if (it.id == pendingUserId) it.copy(status = ChatMessageStatus.COMPLETED) else it }
        pendingUserId = null
        state = state.copy(messages = messages + ChatMessage(++nextId, ChatRole.ASSISTANT, result.output, generated = generated),
            stage = ChatStage.COMPLETED, partial = "", limitReached = full)
    }
    fun fail(problem: ApiProblem?, cancelled: Boolean = false) {
        pending = null
        val messages = state.messages.map { if (it.id == pendingUserId) it.copy(status = ChatMessageStatus.EXCLUDED) else it }
        pendingUserId = null
        state = state.copy(messages = messages, stage = if (cancelled) ChatStage.CANCELLED else ChatStage.FAILED, problem = problem,
            limitReached = attempts >= MAX_ATTEMPTS || state.limitReached)
    }
    private fun size(items: List<JsonObject>) = JsonArray(items).toString().encodeToByteArray().size
    private fun message(role: String, text: String) = buildJsonObject { put("role", role); put("content", text) }
}
