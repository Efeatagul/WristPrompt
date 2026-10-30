package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** D5-F stress evidence: synthetic text only; timing is diagnostic, never a host-speed gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsolidatedPresentationTest {
    @Test fun fineStreamTerminalAndStopHaveBoundedWork(): Unit = runTest {
        var current = ChatState(listOf(ChatMessage(1, ChatRole.USER, "q")), stage = ChatStage.RECEIVING, partial = "a")
        val metrics = PresentationMetrics()
        val rich = MarkdownProjection()
        val publications = mutableListOf<ChatPresentation>()
        val worker = MarkdownWorker(backgroundScope, { current }, publications::add,
            StandardTestDispatcher(testScheduler), rich)
        val coalescer = PresentationCoalescer(this, { current }, metrics) {
            publications.add(worker.accept(current, it))
        }
        coalescer.accept(current); runCurrent()
        var maxPending = 0
        repeat(65_000) { i ->
            current = current.copy(partial = current.partial + "a")
            coalescer.accept(current)
            maxPending = maxOf(maxPending, coalescer.pendingCount)
            if (i % 10 == 9) { advanceTimeBy(1); runCurrent() }
        }
        advanceTimeBy(100); runCurrent()
        val streamingFlushes = metrics.flushes
        assertTrue(streamingFlushes <= 67)
        assertEquals(0, rich.metrics.parses)
        assertEquals(1, maxPending)
        val at = currentTime
        current = current.copy(stage = ChatStage.CANCELLED)
        coalescer.accept(current)
        assertEquals(ChatStage.CANCELLED, publications.last().stage)
        assertEquals(at, currentTime)
        runCurrent()
        assertEquals(1, rich.metrics.parses)
        current = ChatState(); coalescer.accept(current); runCurrent()
        assertTrue(publications.last().rows.isEmpty())
        assertEquals(0, coalescer.projection.cachedBytes)
        assertEquals(0, rich.metrics.cacheBytes)
        val count = publications.size
        advanceTimeBy(10_000); runCurrent()
        assertEquals(count, publications.size)
        println("D5F_STREAM deltas=65000 streamingFlushes=$streamingFlushes segmentations=${metrics.segmentations} projectedChars=${metrics.projectedChars} parses=${rich.metrics.parses} maxPending=$maxPending maxActive=${rich.metrics.maxActive} stopVirtualMs=${currentTime-at-10000} idlePublications=${publications.size-count}")
    }

    @Test fun repeatedRichResetReleasesDisposableCachesAndPreservesCanonical(): Unit = runTest {
        val plain = PlainTextProjection()
        val rich = MarkdownProjection()
        repeat(24) { cycle ->
            val source = when (cycle % 3) {
                0 -> "# Heading\n\n**paragraph**\n\n".repeat(200)
                1 -> "```kt\n" + "val value = 123\n".repeat(2000) + "```"
                else -> "|Name|Value|\n|---|---|\n" + "|item|data|\n".repeat(100)
            }
            val chat = ChatState(listOf(ChatMessage(cycle * 2L + 1, ChatRole.USER, "q"),
                ChatMessage(cycle * 2L + 2, ChatRole.ASSISTANT, source)), stage = ChatStage.COMPLETED)
            val projected = plain.project(chat)
            val first = rich.project(chat, projected)
            val parses = rich.metrics.parses
            val second = rich.project(chat, plain.project(chat))
            assertEquals(parses, rich.metrics.parses)
            assertSame(first.rows.last().markdown, second.rows.last().markdown)
            assertTrue(plain.cachedBytes <= PlainTextProjection.CACHE_BYTES)
            assertTrue(rich.metrics.cacheBytes <= MarkdownProjection.CACHE_BYTES)
            assertSame(source, chat.messages.last().text)
            rich.project(ChatState(), plain.project(ChatState()))
            assertEquals(0, plain.cachedBytes)
            assertEquals(0, rich.metrics.cacheBytes)
        }
        println("D5F_RESET cycles=24 parses=${rich.metrics.parses} maxActive=${rich.metrics.maxActive} plainCacheBytes=${plain.cachedBytes} richCacheBytes=${rich.metrics.cacheBytes}")
    }

    @Test fun streamAccumulationAtCurrentLimitRetainsExactOutput() {
        for (chunk in listOf(1, 64, 512)) {
            val source = "a".repeat(65_536)
            val start = System.nanoTime()
            val result = StreamResult()
            source.chunked(chunk).forEach { result.accept(ResponseEvent.Delta(it)) }
            assertEquals(source, result.output)
            result.accept(ResponseEvent.Completed(source))
            assertTrue(result.completed)
            println("D5F_ACCUMULATION chars=${source.length} chunk=$chunk elapsedMs=${(System.nanoTime()-start)/1_000_000}")
        }
    }
}
