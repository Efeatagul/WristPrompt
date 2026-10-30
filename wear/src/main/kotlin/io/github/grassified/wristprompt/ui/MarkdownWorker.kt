package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.ChatState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One consumer, one active conversion, one replaceable pending snapshot. No per-delta jobs. */
internal class MarkdownWorker(
    scope: CoroutineScope,
    private val current: () -> ChatState,
    private val publish: (ChatPresentation) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val projection: MarkdownProjection = MarkdownProjection(),
) {
    private data class Work(val chat: ChatState, val base: ChatPresentation)
    private val requests = Channel<Work>(Channel.CONFLATED)
    private var wanted: ChatState? = null
    private var ready: Work? = null
    init {
        scope.launch {
            try { for (work in requests) {
                if (wanted !== work.chat || current() !== work.chat) continue
                val rich = withContext(dispatcher) { projection.project(work.chat, work.base) }
                if (wanted === work.chat && current() === work.chat) {
                    ready = Work(work.chat, rich)
                    publish(rich)
                }
            } } finally {
                ready = null
                projection.clear()
            }
        }.invokeOnCompletion { requests.close() }
    }
    fun accept(chat: ChatState, base: ChatPresentation): ChatPresentation {
        wanted = chat
        if (chat.messages.isEmpty()) ready = null
        requests.trySend(Work(chat, base))
        // Reuse already displayed completed blocks immediately while the newest tail is plain.
        val old = ready ?: return base
        val stableIds = old.chat.messages.filter { before -> chat.messages.any { it.id == before.id && it.text === before.text } }.map { it.id }.toSet()
        val stablePrefixes = mutableSetOf<String>()
        var userId = 0L
        for (message in old.chat.messages) {
            if (message.role == io.github.grassified.wristprompt.openai.ChatRole.USER) userId = message.id
            else if (message.id in stableIds) stablePrefixes.add("answer-$userId-")
        }
        val emitted = mutableSetOf<String>()
        return base.copy(rows = buildList {
            for (row in base.rows) {
                val prefix = stablePrefixes.firstOrNull { row.key.startsWith(it) }
                if (prefix == null) add(row)
                else if (emitted.add(prefix)) addAll(old.base.rows.filter { it.key.startsWith(prefix) })
            }
        })
    }
    fun pause() { wanted = null }
}
