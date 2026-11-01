package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.*
import java.text.BreakIterator
import java.util.Locale

internal enum class TranscriptType { USER, ASSISTANT, IMAGE, TAIL, STATUS, RECOVERY }
internal data class TranscriptRow(
    val key: String, val type: TranscriptType, private val source: String, val exchangeStart: Boolean = false,
    val boundaryNewline: Boolean = false, private val start: Int = 0, private val end: Int = source.length,
    val markdown: MarkdownBlock? = null, val generated: GeneratedAttachment? = null,
) {
    val text: String get() = if (start == 0 && end == source.length) source else source.substring(start, end)
    val textLength get() = end - start
    override fun toString() = "TranscriptRow(redacted)"
}
internal data class ChatPresentation(val rows: List<TranscriptRow> = emptyList(), val stage: ChatStage = ChatStage.EMPTY,
    val recovery: RecoveryAction? = null) {
    override fun toString() = "ChatPresentation(redacted)"
}

/** Disposable plain-text projection. No layout, replay, credential or syntax objects. */
internal class PresentationMetrics {
    var projectedChars = 0L
    var segmentations = 0L
    var flushes = 0L
}

internal class PlainTextProjection(private val metrics: PresentationMetrics? = null) {
    companion object { const val SEGMENT_CHARS = 512; const val CACHE_BYTES = 262_144 }
    private data class Cached(val source: String, val rows: List<TranscriptRow>)
    private val completed = linkedMapOf<Long, Cached>()
    private var attempt: Long? = null
    private val settled = mutableListOf<TranscriptRow>()
    private var consumed = 0
    val projectedChars get() = metrics?.projectedChars ?: 0L
    val segmentations get() = metrics?.segmentations ?: 0L
    val cachedBytes get() = completed.values.sumOf { 64 + it.rows.size * 128 }
    fun clear() { completed.clear(); clearTail(); attempt = null }
    private fun clearTail() { settled.clear(); consumed = 0 }

    fun project(chat: ChatState): ChatPresentation {
        val ids = chat.messages.mapTo(mutableSetOf()) { it.id }
        completed.keys.retainAll(ids)
        val currentAttempt = chat.messages.lastOrNull { it.role == ChatRole.USER }?.id
        if (currentAttempt != attempt) { clearTail(); attempt = currentAttempt }
        if (chat.messages.isEmpty()) clear()
        val rows = buildList {
            var userId: Long? = null
            for (message in chat.messages) {
                if (message.role == ChatRole.USER) userId = message.id
                val old = completed[message.id]
                val parts = if (old?.source === message.text) old.rows else {
                    val type = if (message.role == ChatRole.USER) TranscriptType.USER else TranscriptType.ASSISTANT
                    val key = if (type == TranscriptType.USER) "user-${message.id}" else "answer-${userId ?: message.id}"
                    // Preserve settled boundaries when final canonical text agrees. Replace
                    // their copied streaming slices with ranges of that one canonical string.
                    val prefix = if (message.role == ChatRole.ASSISTANT && message.id == chat.messages.last().id &&
                        prefixMatches(message.text)) settled else emptyList()
                    var offset = 0
                    val ranges = prefix.map { row ->
                        val end = offset + row.textLength
                        TranscriptRow(row.key, type, message.text, row.exchangeStart, row.boundaryNewline, offset, end)
                            .also { offset = end }
                    }
                    ranges + segments(message.text, offset, key, type, ranges.size,
                        exchangeStart = type == TranscriptType.USER, retainSource = true)
                }.also { projected ->
                    val bytes = 64 + projected.size * 128
                    if (bytes <= CACHE_BYTES) {
                        while (cachedBytes + bytes > CACHE_BYTES && completed.isNotEmpty())
                            completed.remove(completed.keys.first())
                        completed[message.id] = Cached(message.text, projected)
                    }
                }
                addAll(parts)
                message.generated?.let { add(TranscriptRow("generated-${message.id}", TranscriptType.IMAGE, "", generated = it)) }
                if (message.role == ChatRole.USER && message.status == ChatMessageStatus.EXCLUDED && message.id != currentAttempt)
                    add(TranscriptRow("excluded-${message.id}", TranscriptType.STATUS, "Unfinished message · not used for future replies."))
            }
            if (chat.partial.isNotEmpty()) {
                if (consumed > chat.partial.length) clearTail()
                val parts = segments(chat.partial, consumed, "answer-$currentAttempt", TranscriptType.ASSISTANT, settled.size)
                // The last segment remains unfinished until a later publication. All previous
                // rows are immutable and reused by reference, rather than rescanned per delta.
                if (parts.isNotEmpty()) {
                    settled.addAll(parts.dropLast(1))
                    consumed = settled.sumOf { it.textLength }
                }
                addAll(settled)
                parts.lastOrNull()?.let { add(it.copy(type = if (chat.busy) TranscriptType.TAIL else TranscriptType.ASSISTANT)) }
            }
            (terminalText(chat) ?: chatError(chat)?.text)?.let { add(TranscriptRow("status-$currentAttempt", TranscriptType.STATUS, it)) }

            if (chat.limitReached && chat.stage in setOf(ChatStage.FAILED, ChatStage.CANCELLED))
                add(TranscriptRow("limit", TranscriptType.STATUS, "Start a new chat to continue"))
        }
        if (!chat.busy && chat.partial.isEmpty()) clearTail()
        return ChatPresentation(rows, chat.stage, if (chat.limitReached) RecoveryAction.NEW_CHAT else chatError(chat)?.action)
    }

    private fun prefixMatches(text: String): Boolean {
        if (consumed > text.length) return false
        var offset = 0
        for (row in settled) {
            if (!text.regionMatches(offset, row.text, 0, row.textLength)) return false
            offset += row.textLength
        }
        return true
    }

    private fun segments(text: String, start: Int, key: String, type: TranscriptType, index: Int,
        exchangeStart: Boolean = false, retainSource: Boolean = false): List<TranscriptRow> {
        metrics?.let { it.segmentations++; it.projectedChars += text.length - start }
        return buildList {
            var offset = start
            while (offset < text.length) {
                val limit = minOf(offset + SEGMENT_CHARS, text.length)
                val newline = (limit - 1 downTo offset).firstOrNull { text[it] == '\n' }
                val end = if (newline != null) newline + 1 else safeEnd(text, offset, limit)
                add(TranscriptRow("$key-${index + size}", type,
                    if (retainSource) text else text.substring(offset, end), exchangeStart && offset == 0,
                    boundaryNewline = end < text.length && text[end - 1] == '\n',
                    start = if (retainSource) offset else 0, end = if (retainSource) end else end - offset))
                offset = end
            }
        }
    }
}

/** Boundaries preserve surrogate pairs, combining sequences, emoji modifiers/ZWJ and flags. */
internal fun safeEnd(text: String, start: Int, limit: Int): Int {
    if (limit == text.length) return limit
    val breaks = BreakIterator.getCharacterInstance(Locale.ROOT)
    // Only the unfinished bounded tail is segmented; never hand the entire history to BreakIterator.
    breaks.setText(text.substring(start, minOf(text.length, limit + 128)))
    var end = start + breaks.preceding(limit - start + 1)
    fun unsafe(at: Int): Boolean {
        if (at <= start || at >= text.length) return false
        val before = text.codePointBefore(at); val after = text.codePointAt(at)
        val kind = Character.getType(after)
        return before == 0x200D || after == 0x200D || after in 0xFE00..0xFE0F ||
            after in 0x1F3FB..0x1F3FF || after in 0xE0020..0xE007F ||
            kind == Character.NON_SPACING_MARK.toInt() || kind == Character.COMBINING_SPACING_MARK.toInt() ||
            kind == Character.ENCLOSING_MARK.toInt() ||
            (before in 0x1F1E6..0x1F1FF && after in 0x1F1E6..0x1F1FF && regionalCount(text, at) % 2 == 1) ||
            (text[at - 1].isHighSurrogate() && text[at].isLowSurrogate())
    }
    while (end > start && unsafe(end)) end -= Character.charCount(text.codePointBefore(end))
    if (end > start) return end
    // A single unusually long grapheme may exceed the normal row budget; do not corrupt it.
    end = limit
    if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end++
    while (end < text.length && unsafe(end)) end += Character.charCount(text.codePointAt(end))
    return end
}

internal fun terminalText(chat: ChatState): String? = when (chat.stage) {
    ChatStage.STARTING -> "Sending…"
    ChatStage.RECEIVING -> "Responding…"
    ChatStage.CANCELLED, ChatStage.FAILED -> chatError(chat)?.text
    else -> null
}

internal fun latestAvailable(hasContent: Boolean, canScrollForward: Boolean) = hasContent && canScrollForward
internal fun latestTarget(totalItems: Int) = (totalItems - 1).coerceAtLeast(0)

private fun regionalCount(text: String, end: Int): Int {
    var offset = end
    var count = 0
    while (offset > 0) {
        val cp = text.codePointBefore(offset)
        if (cp !in 0x1F1E6..0x1F1FF) break
        count++; offset -= Character.charCount(cp)
    }
    return count
}

internal fun transcriptDisplayText(row: TranscriptRow): String =
    if (row.boundaryNewline) row.text.dropLast(1).removeSuffix("\r") else row.text
