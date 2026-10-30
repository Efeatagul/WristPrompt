package io.github.grassified.wristprompt.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownProjectionTest {
    private val p = MarkdownProjection()
    private fun text(s: String) = p.blocks(s).joinToString("") { it.inline.text }
    @Test fun paragraphs() { assertEquals(listOf("first", "second"), p.blocks("first\n\nsecond").map { it.inline.text }) }
    @Test fun bold() { assertEquals(FontWeight.Bold, p.blocks("**bold**").single().inline.spanStyles.single().item.fontWeight) }
    @Test fun italic() { assertEquals(FontStyle.Italic, p.blocks("*italic*").single().inline.spanStyles.single().item.fontStyle) }
    @Test fun nestedStyles() { assertEquals(2, p.blocks("**bold *italic***").single().inline.spanStyles.size) }
    @Test fun headings() { assertEquals((1..6).toList(), p.blocks((1..6).joinToString("\n") { "#".repeat(it)+" title" }).map { it.level }) }
    @Test fun unordered() { assertEquals(listOf("•", "•"), p.blocks("- one\n- two").map { it.marker }) }
    @Test fun orderedStartsAtSourceNumber() { assertEquals(listOf("7.", "8."), p.blocks("7. one\n8. two").map { it.marker }) }
    @Test fun nestedListsBounded() { assertTrue(p.blocks((0..12).joinToString("\n") { "  ".repeat(it)+"- item" }).all { it.level <= 3 }) }
    @Test fun inlineCode() { assertEquals("a b", text("`a b`")); assertNotNull(p.blocks("`code`").single().inline.spanStyles.single().item.fontFamily) }
    @Test fun fence() { val b=p.blocks("```kt\n  x\n\n  y\n``` ").single(); assertEquals(MarkdownKind.CODE,b.kind); assertEquals("  x\n\n  y\n",b.inline.text); assertEquals("kt",b.language) }
    @Test fun unclosedFence() { assertEquals(MarkdownKind.CODE,p.blocks("```\nx").single().kind) }
    @Test fun codePreservesOriginalLineEndingsAndUnclosedTail() {
        assertEquals("  x\r\n\r\ny\r\n",p.blocks("```\r\n  x\r\n\r\ny\r\n```").single().inline.text)
        assertEquals("x",p.blocks("```\nx").single().inline.text)
    }
    @Test fun quote() { assertEquals(MarkdownKind.QUOTE,p.blocks("> words").single().kind) }
    @Test fun rule() { assertEquals(MarkdownKind.RULE,p.blocks("---").single().kind) }
    @Test fun linkExtraction() { val link=p.blocks("[label](https://example.invalid/a)").single().links.single(); assertEquals("label",link.label); assertEquals("example.invalid",link.host) }
    @Test fun schemesInert() { for(s in listOf("javascript:x","intent:x","file:/x","content:x","data:x","custom:x","https://user@host/")) assertNull(MarkdownLink("label",s).host) }
    @Test fun malformedLink() { assertEquals("[unfinished](",text("[unfinished](")); assertNull(MarkdownLink("x","https://[").host) }
    @Test fun htmlLiteral() { assertEquals("<script>alert(1)</script>",text("<script>alert(1)</script>")); assertTrue(text("a <img src='x'>").contains("<img")) }
    @Test fun imageAltOnly() { val b=p.blocks("![diagram](https://example.invalid/private)").single(); assertEquals("[Image: diagram]",b.inline.text); assertTrue(b.links.isEmpty()) }
    @Test fun tableRecords() { val b=p.blocks("| Model | Speed | Quality |\n|---|---|---|\n|Luna|Fast|Good|\n|Sol|Medium|Better|"); assertEquals(listOf("Luna","Speed: Fast","Quality: Good","Sol","Speed: Medium","Quality: Better"),b.map { it.inline.text }) }
    @Test fun missingHeadersAndCells() { assertEquals(listOf("x","Column 2: (empty)"),p.blocks("|Name| |\n|---|---|\n|x|").map { it.inline.text }) }
    @Test fun extraCellsPreserved() { val source="|A|B|\n|---|---|\n|1|2|extra|"; assertEquals(source,text(source)); assertEquals(MarkdownKind.PLAIN,p.blocks(source).first().kind) }
    @Test fun tableLimitFallback() { val source="|A|B|\n|---|---|\n"+"|1|2|\n".repeat(101); assertEquals(source,text(source)) }
    @Test fun unicode() { val s="İstanbul e\u0301 👨‍👩‍👧‍👦 🇹🇷 👍🏽 𐐀"; assertEquals(s,text("**$s**")) }
    @Test fun rtlMixed() { assertEquals("العربية code İstanbul",text("العربية `code` İstanbul")) }
    @Test fun malformedDelimiters() { val s="*unfinished [link "+"* _ ` [".repeat(200); assertTrue(text(s).isNotEmpty()) }
    @Test fun deepNesting() { assertTrue(p.blocks("> ".repeat(1000)+"text").isNotEmpty()) }
    @Test fun unbrokenTextBoundedAndExact() { val s="a".repeat(65000); val b=p.blocks(s); assertEquals(s,b.joinToString("") { it.inline.text }); assertTrue(b.all { it.inline.length<=2048 }) }
    @Test fun parserFailureFallsBackExactly() { val source="**text**"; val broken=MarkdownProjection(parse={error("private payload")}); assertEquals(source,broken.blocks(source).single().inline.text) }
    @Test fun blockBudgetFallsBackWithoutDropping() { val s="a\n\n".repeat(600); assertEquals(s,text(s)) }
    @Test fun sourceBudgetFallsBack() { val s="x".repeat(70000); assertEquals(s,text(s)); assertEquals(0,p.metrics.parses) }
    @Test fun cacheReusesFinalBlocksAndClears() {
        val chat=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q"),ChatMessage(2,ChatRole.ASSISTANT,"**answer**")),stage=ChatStage.COMPLETED)
        val plain=PlainTextProjection().project(chat)
        val first=p.project(chat,plain); val second=p.project(chat,plain)
        assertSame(first.rows.last().markdown,second.rows.last().markdown); assertEquals(1,p.metrics.parses)
        p.project(ChatState(),ChatPresentation()); assertEquals(0,p.metrics.cacheBytes)
    }
    @Test fun streamingSyntaxStaysPlainAndTerminalReconciles() {
        for (tail in listOf("**open", "[link](", "```\nx", "|a|b|\n|---|")) {
            val chat=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q")),stage=ChatStage.RECEIVING,partial=tail)
            val plain=PlainTextProjection().project(chat); val rich=p.project(chat,plain)
            assertEquals(plain,rich); assertEquals(0,p.metrics.parses)
        }
    }
    @Test fun terminalReplacementWinsAndPartialStaysExcluded() {
        val chat=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q",ChatMessageStatus.EXCLUDED)),stage=ChatStage.CANCELLED,partial="**partial**")
        val rich=p.project(chat,PlainTextProjection().project(chat))
        assertEquals("partial",rich.rows.first { it.markdown!=null }.text); assertEquals(ChatStage.CANCELLED,rich.stage)
        assertEquals(ChatMessageStatus.EXCLUDED,chat.messages.single().status)
        val final=chat.copy(messages=listOf(chat.messages.first(),ChatMessage(2,ChatRole.ASSISTANT,"# replacement")),partial="",stage=ChatStage.COMPLETED)
        assertEquals("replacement",p.project(final,PlainTextProjection().project(final)).rows.last().text)
    }
    @Test fun workerRejectsStaleResetAndPublishesOnlyNewest(): Unit = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        var current=ChatState(); val results=mutableListOf<ChatPresentation>()
        val worker=MarkdownWorker(backgroundScope,{current},results::add,dispatcher)
        val plain=PlainTextProjection()
        current=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q"),ChatMessage(2,ChatRole.ASSISTANT,"**old**")),stage=ChatStage.COMPLETED)
        worker.accept(current,plain.project(current))
        current=ChatState(); worker.accept(current,plain.project(current)); runCurrent()
        assertTrue(results.all { it.rows.isEmpty() })
        current=ChatState(listOf(ChatMessage(3,ChatRole.USER,"new"),ChatMessage(4,ChatRole.ASSISTANT,"# newest")),stage=ChatStage.COMPLETED)
        worker.accept(current,plain.project(current)); worker.pause(); runCurrent()
        assertTrue(results.all { it.rows.isEmpty() })
        worker.accept(current,plain.project(current)); runCurrent(); assertEquals("newest",results.last().rows.last().text)
    }
    @Test fun rawDeltasDoNotScheduleParserJobsAndFinalParsesOnce(): Unit = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        var current=ChatState(); val results=mutableListOf<ChatPresentation>()
        val projection=MarkdownProjection()
        val worker=MarkdownWorker(backgroundScope,{current},results::add,dispatcher,projection)
        val coalescer=PresentationCoalescer(this,{current}) { worker.accept(current,it) }
        current=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q")),stage=ChatStage.RECEIVING,partial="*")
        coalescer.accept(current); runCurrent()
        repeat(10000) { current=current.copy(partial=current.partial+"x"); coalescer.accept(current) }
        advanceTimeBy(100); runCurrent()
        assertEquals(0,projection.metrics.parses); assertEquals(0,coalescer.pendingCount)
        current=current.copy(messages=current.messages+ChatMessage(2,ChatRole.ASSISTANT,"**final**"),stage=ChatStage.COMPLETED,partial="")
        coalescer.accept(current); runCurrent()
        assertEquals(1,projection.metrics.parses); assertEquals("final",results.last().rows.last().text)
        println("D5C_STREAM deltas=10000 fullParses=1 pendingFlushMax=1 activeParseMax=${projection.metrics.maxActive}")
    }
    @Test fun stateChangeDuringParseRejectsPublication(): Unit = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        var current=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q"),ChatMessage(2,ChatRole.ASSISTANT,"old")),stage=ChatStage.COMPLETED)
        val results=mutableListOf<ChatPresentation>()
        val projection=MarkdownProjection(parse={ current=ChatState(); org.commonmark.parser.Parser.builder().build().parse(it) })
        val worker=MarkdownWorker(backgroundScope,{current},results::add,dispatcher,projection)
        worker.accept(current,PlainTextProjection().project(current)); runCurrent()
        assertTrue(results.isEmpty())
    }
    @Test fun stopPublishesImmediatelyBeforeQueuedParserRuns(): Unit = runTest {
        var current=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q")),stage=ChatStage.RECEIVING,partial="**partial**")
        val results=mutableListOf<ChatPresentation>()
        val worker=MarkdownWorker(backgroundScope,{current},results::add,StandardTestDispatcher(testScheduler))
        val coalescer=PresentationCoalescer(this,{current}) { results.add(worker.accept(current,it)) }
        coalescer.accept(current)
        current=current.copy(stage=ChatStage.CANCELLED)
        coalescer.accept(current)
        assertEquals(ChatStage.CANCELLED,results.last().stage)
        assertEquals("**partial**",results.last().rows.first { it.type==TranscriptType.ASSISTANT }.text)
        runCurrent(); assertEquals(ChatStage.CANCELLED,results.last().stage)
    }
    @Test fun presentationFailureCannotChangeCompletedCanonicalState() {
        val chat=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q"),ChatMessage(2,ChatRole.ASSISTANT,"**final**")),stage=ChatStage.COMPLETED)
        val broken=MarkdownProjection(parse={error("private")})
        val display=broken.project(chat,PlainTextProjection().project(chat))
        assertEquals(ChatStage.COMPLETED,display.stage); assertEquals(ChatStage.COMPLETED,chat.stage)
        assertEquals("**final**",display.rows.last().text)
    }
    @Test fun cacheEvictsWithinBudgetAndResetReleasesIt() {
        for(id in 1L..12L) {
            val chat=ChatState(listOf(ChatMessage(id*2,ChatRole.USER,"q"),ChatMessage(id*2+1,ChatRole.ASSISTANT,"x".repeat(30000))),stage=ChatStage.COMPLETED)
            p.project(chat,PlainTextProjection().project(chat)); assertTrue(p.metrics.cacheBytes<=MarkdownProjection.CACHE_BYTES)
        }
        p.clear(); assertEquals(0,p.metrics.cacheBytes)
    }
    @Test fun evictedHistoryIsNotReparsedDuringStreaming() {
        val messages=(1L..6L).flatMap { listOf(ChatMessage(it*2,ChatRole.USER,"q"),ChatMessage(it*2+1,ChatRole.ASSISTANT,"**answer** ".repeat(2000))) }
        val completed=ChatState(messages,stage=ChatStage.COMPLETED)
        p.project(completed,PlainTextProjection().project(completed))
        val before=p.metrics.parses
        repeat(5) {
            val stream=completed.copy(stage=ChatStage.RECEIVING,partial="new $it")
            p.project(stream,PlainTextProjection().project(stream))
        }
        assertEquals(before,p.metrics.parses)
    }
    @Test fun benchmarkMatchedMarkdownWorkloads() {
        val workloads=listOf("# Heading\n\n**bold** and *italic*", "paragraph **bold**\n\n".repeat(200), "```\n"+"code line\n".repeat(200)+"```", "|A|B|\n|---|---|\n"+"|x|y|\n".repeat(100), "- list\n".repeat(200), "[x](".repeat(500), "> ".repeat(1000)+"x", "[url](https://example.invalid/"+"a".repeat(10000)+")")
        for ((index, source) in workloads.withIndex()) {
            val chat=ChatState(listOf(ChatMessage(1,ChatRole.USER,"q"),ChatMessage(2,ChatRole.ASSISTANT,source)),stage=ChatStage.COMPLETED)
            val plainStart=System.nanoTime(); PlainTextProjection().project(chat); val plainNanos=System.nanoTime()-plainStart
            val before=p.metrics.nanos; p.blocks(source)
            println("D5C_METRICS workload=$index chars=${source.length} plainNanos=$plainNanos richNanos=${p.metrics.nanos-before}")
        }
        println("D5C_METRICS parses=${p.metrics.parses} chars=${p.metrics.chars} fallbacks=${p.metrics.fallbacks}")
    }
}
