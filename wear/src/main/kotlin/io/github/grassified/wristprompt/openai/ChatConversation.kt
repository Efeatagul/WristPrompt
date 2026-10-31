package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.json.*

enum class ChatRole { USER, ASSISTANT }
enum class ChatMessageStatus { PENDING, COMPLETED, EXCLUDED }
enum class ChatStage { EMPTY, COMPOSING, STARTING, RECEIVING, COMPLETED, FAILED, CANCELLED }
data class ChatMessage(val id: Long, val role: ChatRole, val text: String, val status: ChatMessageStatus = ChatMessageStatus.COMPLETED) {
    override fun toString() = SafeDiagnostics.REDACTED
}
data class ChatState(
    val messages: List<ChatMessage> = emptyList(), val draft: String = "",
    val stage: ChatStage = ChatStage.EMPTY, val partial: String = "",
    val problem: ApiProblem? = null, val limitReached: Boolean = false,
) {
    val busy get() = stage == ChatStage.STARTING || stage == ChatStage.RECEIVING
    val canSend get() = !busy && !limitReached && draft.isNotBlank() && draft.length <= ChatConversation.MAX_PROMPT_CHARS
    override fun toString() = SafeDiagnostics.REDACTED
}

/** One process-memory conversation. Only whole completed exchanges enter replay history. */
class ChatConversation {
    companion object {
        const val MAX_PROMPT_CHARS = 8192
        const val MAX_ATTEMPTS = 12
        const val MAX_CONTEXT_BYTES = 196_608
    }
    var state = ChatState(); private set
    private var history = emptyList<JsonObject>()
    private var pending: JsonObject? = null
    private var pendingUserId: Long? = null
    private var nextId = 0L
    private var attempts = 0
    fun reset() { history = emptyList(); pending = null; pendingUserId = null; attempts = 0; state = ChatState() }
    fun snapshot(): ConversationSnapshot = ConversationSnapshot(
        messages = state.messages.map { StoredMessage(it.id, it.role.name, it.status.name, listOf(ContentPart.Text(it.text))) },
        replay = history.toList(), attempts = attempts, nextId = nextId,
        partial = if (state.busy) "" else state.partial, terminal = state.stage.name,
    ).also { it.validate() }


    /** Process restoration is inert: pending work becomes excluded, never resumed. */
    fun restore(snapshot: ConversationSnapshot) {
        snapshot.validate()
        require(snapshot.messages.all { it.parts.all { part -> part is ContentPart.Text } })
        val interrupted = snapshot.messages.any { it.status == "PENDING" }
        val restored = snapshot.messages.map { m -> ChatMessage(m.id, ChatRole.valueOf(m.role),
            m.parts.joinToString("") { (it as ContentPart.Text).text },
            if (m.status == "PENDING") ChatMessageStatus.EXCLUDED else ChatMessageStatus.valueOf(m.status)) }
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
    fun begin(): List<JsonObject>? {
        if (!state.canSend) return null
        val user = message("user", state.draft)
        val input = history + user
        if (attempts >= MAX_ATTEMPTS || size(input) > MAX_CONTEXT_BYTES) {
            state = state.copy(limitReached = true); return null
        }
        attempts++
        pending = user
        pendingUserId = ++nextId
        state = state.copy(messages = state.messages + ChatMessage(nextId, ChatRole.USER, state.draft, ChatMessageStatus.PENDING),
            draft = "", stage = ChatStage.STARTING, partial = "", problem = null)
        return input
    }
    fun progress(text: String) {
        if (state.busy) state = state.copy(stage = ChatStage.RECEIVING, partial = text)
    }
    fun complete(result: StreamResult) {
        check(state.busy && result.completed)
        val user = checkNotNull(pending)
        val output = result.outputItems ?: listOf(message("assistant", result.output))
        // Unsupported/tool output must never become an implicit tool or be silently discarded.
        validateReplayOutput(output)
        require(size(output) <= ResponsesProtocol.MAX_BODY_BYTES)
        val next = history + user + output
        val full = size(next) > MAX_CONTEXT_BYTES || attempts >= MAX_ATTEMPTS
        history = next
        pending = null
        val messages = state.messages.map { if (it.id == pendingUserId) it.copy(status = ChatMessageStatus.COMPLETED) else it }
        pendingUserId = null
        state = state.copy(messages = messages + ChatMessage(++nextId, ChatRole.ASSISTANT, result.output),
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
