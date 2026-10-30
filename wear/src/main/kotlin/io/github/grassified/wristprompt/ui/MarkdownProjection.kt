package io.github.grassified.wristprompt.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.ext.gfm.tables.*
import java.net.URI
import io.github.grassified.wristprompt.openai.*

internal enum class MarkdownKind { PARAGRAPH, HEADING, LIST_ITEM, QUOTE, CODE, RULE, TABLE_RECORD, PLAIN }
internal data class MarkdownLink(val label: String, val destination: String) {
    val host: String? get() = try { URI(destination).takeIf {
        it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrEmpty() && it.rawUserInfo == null
    }?.host } catch (_: Exception) { null }
    override fun toString() = "MarkdownLink(redacted)"
}
internal data class MarkdownBlock(
    val kind: MarkdownKind, val inline: AnnotatedString, val level: Int = 0,
    val marker: String = "", val links: List<MarkdownLink> = emptyList(), val language: String = "",
) {
    override fun toString() = "MarkdownBlock(redacted)"
}
internal class MarkdownMetrics {
    var parses = 0L; var chars = 0L; var nanos = 0L; var fallbacks = 0L
    var evictions = 0L; var cacheBytes = 0; var active = 0; var maxActive = 0
}

/** No AST escapes this converter. Source is never rewritten or sent to inference. Worker-confined. */
internal class MarkdownProjection(
    val metrics: MarkdownMetrics = MarkdownMetrics(),
    private val parse: (String) -> Node = { text -> Parser.builder()
        .maxOpenBlockParsers(16).maxInlineNesting(16)
        .includeSourceSpans(IncludeSourceSpans.BLOCKS)
        .extensions(listOf(TablesExtension.builder().maxCells(808).build())).build().parse(text) },
) {
    companion object {
        const val MAX_SOURCE = 65_536
        const val MAX_BLOCKS = 512
        const val SEGMENT = 2048
        const val CACHE_BYTES = 262_144
    }
    private data class Cached(val source: String, val blocks: List<MarkdownBlock>, val bytes: Int)
    private val cache = linkedMapOf<Long, Cached>()
    fun clear() { cache.clear(); metrics.cacheBytes = 0 }
    fun project(chat: ChatState, base: ChatPresentation): ChatPresentation {
        val ids = chat.messages.mapTo(mutableSetOf()) { it.id } + listOfNotNull(chat.messages.lastOrNull()?.id?.let { -it })
        for (key in cache.keys.toList()) if (key !in ids) remove(key)
        if (chat.messages.isEmpty()) { clear(); return base }
        val replacements = mutableMapOf<String, List<TranscriptRow>>()
        var userId = 0L
        for (message in chat.messages) {
            if (message.role == ChatRole.USER) userId = message.id
            else if (!chat.busy || cache[message.id]?.source === message.text)
                replacements["answer-$userId-"] = rows(message.id, message.text, "answer-$userId")
        }
        // The unfinished current answer is deliberately plain: future reference definitions,
        // list/table continuations and unclosed delimiters can reinterpret arbitrary prefixes.
        // Prior completed answers are settled, cached and reused throughout the stream.
        if (!chat.busy && chat.partial.isNotEmpty())
            replacements["answer-$userId-"] = rows(-userId, chat.partial, "answer-$userId")
        val emitted = mutableSetOf<String>()
        return base.copy(rows = buildList {
            for (row in base.rows) {
                val prefix = replacements.keys.firstOrNull { row.key.startsWith(it) }
                if (prefix == null) add(row)
                else if (emitted.add(prefix)) addAll(replacements.getValue(prefix))
            }
        })
    }
    private fun remove(key: Long) { cache.remove(key)?.let { metrics.cacheBytes -= it.bytes; metrics.evictions++ } }
    private fun rows(id: Long, source: String, key: String): List<TranscriptRow> {
        val old = cache[id]
        val blocks = if (old?.source === source) old.blocks else {
            val next = blocks(source)
            val bytes = source.length * 2 + next.sumOf { 128 + it.inline.length * 2 + it.links.sumOf { link -> 64 + link.destination.length * 2 } }
            if (bytes <= CACHE_BYTES) {
                remove(id)
                while (metrics.cacheBytes + bytes > CACHE_BYTES && cache.isNotEmpty()) remove(cache.keys.first())
                cache[id] = Cached(source, next, bytes); metrics.cacheBytes += bytes
            }
            next
        }
        return blocks.mapIndexed { index, block -> TranscriptRow("$key-$index", TranscriptType.ASSISTANT,
            block.inline.text, markdown = block) }
    }
    fun blocks(source: String): List<MarkdownBlock> {
        val start = System.nanoTime()
        metrics.active++; metrics.maxActive = maxOf(metrics.maxActive, metrics.active)
        try {
            if (source.length > MAX_SOURCE) return fallback(source)
            metrics.parses++; metrics.chars += source.length
            val root = parse(source)
            val result = mutableListOf<MarkdownBlock>()
            fun add(block: MarkdownBlock) {
                var offset = 0
                if (block.inline.isEmpty()) result.add(block)
                while (offset < block.inline.length) {
                    val end = safeEnd(block.inline.text, offset, minOf(offset + SEGMENT, block.inline.length))
                    result.add(block.copy(inline = block.inline.subSequence(offset, end), marker = if (offset == 0) block.marker else "",
                        language = if (offset == 0) block.language else "", links = if (offset == 0) block.links else emptyList()))
                    offset = end
                    require(result.size <= MAX_BLOCKS)
                }
                require(result.size <= MAX_BLOCKS)
            }
            fun walk(parent: Node, depth: Int = 0, quote: Int = 0, marker: String = "") {
                require(depth <= 32)
                var child = parent.firstChild
                var number = (parent as? OrderedList)?.markerStartNumber ?: 1
                while (child != null) {
                    val node = child
                    when (node) {
                        is TableBlock -> table(node, source, ::add)
                        is Heading -> add(inline(node, MarkdownKind.HEADING).copy(level = node.level))
                        is Paragraph -> add(inline(node, if (marker.isNotEmpty()) MarkdownKind.LIST_ITEM else if (quote > 0) MarkdownKind.QUOTE else MarkdownKind.PARAGRAPH)
                            .copy(level = if (marker.isNotEmpty()) depth.coerceAtMost(3) else quote.coerceAtMost(3), marker = marker))
                        is FencedCodeBlock -> add(MarkdownBlock(MarkdownKind.CODE, AnnotatedString(codeText(node, source)), language = node.info.take(40)))
                        is IndentedCodeBlock -> add(MarkdownBlock(MarkdownKind.CODE, AnnotatedString(node.literal)))
                        is ThematicBreak -> add(MarkdownBlock(MarkdownKind.RULE, AnnotatedString("")))
                        is HtmlBlock -> add(MarkdownBlock(MarkdownKind.PLAIN, AnnotatedString(node.literal)))
                        is BlockQuote -> walk(node, depth + 1, quote + 1)
                        is ListItem -> { walk(node, depth + 1, quote, if (parent is OrderedList) "${number++}." else "•") }
                        is LinkReferenceDefinition -> Unit
                        else -> walk(node, depth + 1, quote)
                    }
                    child = node.next
                }
            }
            walk(root)
            return result.toList()
        } catch (_: Exception) { return fallback(source) }
        finally { metrics.active--; metrics.nanos += System.nanoTime() - start }
    }
    private fun fallback(source: String): List<MarkdownBlock> {
        metrics.fallbacks++
        return buildList {
            var offset = 0
            while (offset < source.length) {
                val end = safeEnd(source, offset, minOf(offset + SEGMENT, source.length))
                add(MarkdownBlock(MarkdownKind.PLAIN, AnnotatedString(source.substring(offset, end))))
                offset = end
            }
        }
    }
    private fun inline(node: Node, kind: MarkdownKind): MarkdownBlock {
        val out = AnnotatedString.Builder()
        val links = mutableListOf<MarkdownLink>()
        fun walk(parent: Node, depth: Int = 0) {
            require(depth <= 32)
            var current = parent.firstChild
            while (current != null) {
                val child = current
                val style = when (child) {
                    is StrongEmphasis -> SpanStyle(fontWeight = FontWeight.Bold)
                    is Emphasis -> SpanStyle(fontStyle = FontStyle.Italic)
                    is Code -> SpanStyle(fontFamily = FontFamily.Monospace)
                    is Link -> SpanStyle(textDecoration = TextDecoration.Underline)
                    else -> null
                }
                if (style != null) out.pushStyle(style)
                when (child) {
                    is Text -> out.append(child.literal)
                    is Code -> out.append(child.literal)
                    is SoftLineBreak, is HardLineBreak -> out.append("\n")
                    is HtmlInline -> out.append(child.literal)
                    is Image -> { out.append("[Image: "); walk(child, depth + 1); out.append("]") }
                    is Link -> {
                        val start = out.length
                        walk(child, depth + 1)
                        require(links.size < 8 && child.destination.length <= 2048)
                        links.add(MarkdownLink(out.toAnnotatedString().text.substring(start), child.destination))
                    }
                    else -> walk(child, depth + 1)
                }
                if (style != null) out.pop()
                current = child.next
            }
        }
        walk(node)
        return MarkdownBlock(kind, out.toAnnotatedString(), links = links.distinct())
    }
    private fun codeText(node: FencedCodeBlock, source: String): String {
        val spans = node.sourceSpans
        val start = spans.first().let { it.inputIndex + it.length }
        val end = if (node.closingFenceLength != null) spans.last().inputIndex else source.length
        val separators = Regex("\\r\\n|\\r|\\n").findAll(source.substring(start, end)).map { it.value }.drop(1).toList()
        val lines = node.literal.split('\n').dropLast(1)
        // CommonMark omits zero-length source spans and normalizes line endings.
        // Source-span bounds recover separators, including a missing final newline.
        return lines.mapIndexed { index, line -> line + separators.getOrElse(index) { "" } }.joinToString("")
    }

    private fun table(table: TableBlock, source: String, add: (MarkdownBlock) -> Unit) {
        val spans = table.sourceSpans
        val raw = spans.joinToString("\n") { source.substring(it.inputIndex, it.inputIndex + it.length) }
        require(raw.length <= 16_384)
        val rows = mutableListOf<List<MarkdownBlock>>()
        fun collect(node: Node) {
            var child = node.firstChild
            while (child != null) {
                if (child is TableRow) {
                    val cells = mutableListOf<MarkdownBlock>()
                    var cell = child.firstChild
                    while (cell != null) { cells.add(inline(cell, MarkdownKind.TABLE_RECORD)); cell = cell.next }
                    rows.add(cells)
                } else collect(child)
                child = child.next
            }
        }
        collect(table)
        require(rows.size <= 101 && rows.first().size <= 8)
        // GFM drops excess cells. Prefer exact plain source over losing arbitrary content.
        val columns = rows.first().size
        require(raw.lines().all { line ->
            val trimmed = line.trim().removePrefix("|").removeSuffix("|")
            trimmed.split(Regex("(?<!\\\\)\\|")).size <= columns
        })
        val headers = rows.first().mapIndexed { index, cell -> cell.inline.text.ifBlank { "Column ${index + 1}" } }
        if (rows.size == 1) rows.first().forEach(add)
        for (row in rows.drop(1)) {
            for (index in headers.indices) {
                val cell = row.getOrNull(index) ?: MarkdownBlock(MarkdownKind.TABLE_RECORD, AnnotatedString(""))
                val value = cell.inline.ifEmpty { AnnotatedString("(empty)") }
                add(cell.copy(inline = if (index == 0) value else AnnotatedString("${headers[index]}: ") + value,
                    level = if (index == 0) 1 else 0))
            }
        }
    }
}
