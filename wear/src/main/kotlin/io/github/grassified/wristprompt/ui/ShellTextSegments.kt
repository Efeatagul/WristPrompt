package io.github.grassified.wristprompt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope

/** Spacing belongs to logical items, never to an implementation-only text segment. */
internal const val SHELL_CONTINUATION = "shell-text-continuation"
internal class ShellListScope(
    private val target: TransformingLazyColumnScope, private val spacing: Dp,
) {
    private var hasItems = false
    fun item(key: Any? = null, contentType: Any? = null, content: @Composable TransformingLazyColumnItemScope.() -> Unit) {
        val gap = if (hasItems && contentType != SHELL_CONTINUATION) spacing else 0.dp
        hasItems = true
        target.item(key, contentType) { Box(Modifier.padding(top = gap)) { content() } }
    }
    fun items(count: Int, key: ((Int) -> Any)? = null, contentType: (Int) -> Any? = { null },
        itemContent: @Composable TransformingLazyColumnItemScope.(Int) -> Unit) {
        val hadItems = hasItems
        if (count > 0) hasItems = true
        target.items(count, key, contentType) { index ->
            val gap = if ((hadItems || index > 0) && contentType(index) != SHELL_CONTINUATION) spacing else 0.dp
            Box(Modifier.padding(top = gap)) { itemContent(index) }
        }
    }
}

internal data class ShellTextSegment(val text: String, val continuation: Boolean) {
    override fun toString() = "ShellTextSegment(redacted)"
}

/** Preserve source and blank-line boundaries; bounded slices are not new paragraphs. */
internal fun shellTextSegments(text: String): List<ShellTextSegment> = buildList {
    val boundaries = Regex("\\r?\\n[\\t ]*\\r?\\n").findAll(text).map { it.range.last + 1 }.iterator()
    var paragraphEnd: Int? = if (boundaries.hasNext()) boundaries.next() else null
    var offset = 0
    var continuation = false
    while (offset < text.length) {
        val limit = minOf(offset + 512, text.length)
        val end = if (paragraphEnd != null && paragraphEnd <= limit) paragraphEnd else safeEnd(text, offset, limit)
        add(ShellTextSegment(text.substring(offset, end), continuation))
        continuation = end != paragraphEnd
        offset = end
        if (!continuation) paragraphEnd = if (boundaries.hasNext()) boundaries.next() else null
    }
}
