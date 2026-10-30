package io.github.grassified.wristprompt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.openai.ChatStage
import io.github.grassified.wristprompt.openai.ChatState
import java.lang.ref.WeakReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Main-thread presentation scheduling only. The canonical owner still owns every request. */
internal class PresentationCoalescer(
    private val scope: CoroutineScope,
    private val canonical: () -> ChatState,
    private val metrics: PresentationMetrics? = null,
    private val publish: (ChatPresentation) -> Unit,
) {
    val projection = PlainTextProjection(metrics)
    private data class Boundary(val messages: List<io.github.grassified.wristprompt.openai.ChatMessage>,
        val stage: ChatStage, val hasText: Boolean, val problem: io.github.grassified.wristprompt.openai.ApiProblem?)
    private var displayed: Boundary? = null
    private var pending: Job? = null
    private var version = 0L
    private var lastPublished = WeakReference<ChatState>(null)
    val flushes get() = metrics?.flushes ?: 0L
    val pendingCount get() = if (pending != null) 1 else 0
    fun discard() { pause(); projection.clear(); displayed = null; lastPublished.clear() }
    fun pause() { version++; pending?.cancel(); pending = null }
    fun accept(next: ChatState) {
        val old = displayed
        // First text, ownership/attempt, metadata and every terminal/reset boundary bypass cadence.
        val immediate = old == null || !next.busy || old.stage == ChatStage.EMPTY ||
            old.messages !== next.messages || old.problem != next.problem ||
            (!old.hasText && next.partial.isNotEmpty())
        if (immediate) { pause(); flush(next); return }
        if (pending != null) return
        val scheduled = version
        val attempt = next.messages.lastOrNull()?.id
        pending = scope.launch {
            delay(100)
            pending = null
            val latest = canonical()
            if (scheduled == version && latest.busy && latest.messages.lastOrNull()?.id == attempt) flush(latest)
        }
    }
    fun resume() { pause(); flush(canonical()) }
    private fun flush(next: ChatState) {
        if (lastPublished.get() === next) return
        lastPublished = WeakReference(next)
        displayed = Boundary(next.messages, next.stage, next.partial.isNotEmpty(), next.problem)
        metrics?.let { it.flushes++ }
        publish(projection.project(next))
    }
}

/** Retained across navigation/recreation, active only while Chat is visibly STARTED. */
internal class ChatPresentationOwner(private val canonical: StateFlow<ChatState>, metrics: PresentationMetrics? = null, dispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private val mutable = MutableStateFlow(ChatPresentation())
    val state = mutable.asStateFlow()
    private val markdown = MarkdownWorker(viewModelScope, { canonical.value }, { mutable.value = it }, dispatcher)
    val coalescer = PresentationCoalescer(viewModelScope, { canonical.value }, metrics) {
        mutable.value = markdown.accept(canonical.value, it)
    }
    private var visible = false
    init {
        viewModelScope.launch {
            canonical.collect {
                if (visible) coalescer.accept(it)
                else if (it.messages.isEmpty()) {
                    coalescer.discard()
                    markdown.accept(it, ChatPresentation())
                    mutable.value = ChatPresentation()
                }
            }
        }
    }
    fun visible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (value) {
            coalescer.resume()
            markdown.accept(canonical.value, mutable.value)
        } else { coalescer.pause(); markdown.pause() }
    }
    override fun onCleared() { markdown.pause(); coalescer.pause(); coalescer.projection.clear() }
}
