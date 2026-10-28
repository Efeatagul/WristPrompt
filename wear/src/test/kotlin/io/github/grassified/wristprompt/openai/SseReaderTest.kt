package io.github.grassified.wristprompt.openai

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import org.junit.Assert.*
import org.junit.Test

class SseReaderTest {
    private val delta = """{"type":"response.output_text.delta","delta":"WristPrompt works."}"""
    private val completed = """{"type":"response.completed","response":{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"WristPrompt works."}]}]}}"""
    private fun reader(value: String) = SseReader(value.byteInputStream())
    @Test fun splitUtf8ReadsBomCrLfCommentsAndMultipleDataLinesAreHandled() {
        val value = "\uFEFF: ping\r\nevent: response.output_text.delta\r\ndata: {\r\ndata: \"type\":\"response.output_text.delta\",\r\ndata: \"delta\":\"İ✓\"}\r\n\r\n"
        val chunked = object : FilterInputStream(ByteArrayInputStream(value.encodeToByteArray())) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 1))
        }
        assertEquals(ResponseEvent.Delta("İ✓"), SseReader(chunked).next())
    }
    @Test fun bareCrAndLfFramesAndUnknownEventsDoNotImplyCompletion() {
        val events = reader("event: response.created\rdata: {\"type\":\"response.created\"}\r\rdata: $delta\n\n")
        assertEquals(ResponseEvent.Ignored, events.next())
        assertEquals(ResponseEvent.Delta("WristPrompt works."), events.next())
        assertNull(events.next())
    }
    @Test fun exactTextNeedsDocumentedSuccessfulTerminalEvent() {
        val result = StreamResult()
        result.accept(reader("data: $delta\n\n").next()!!)
        assertFalse(result.proofPassed)
        assertEquals(ApiProblem.INTERRUPTED, assertThrows(ApiFailure::class.java) { result.end() }.problem)
        result.accept(reader("event: response.completed\ndata: $completed\n\n").next()!!)
        result.end(); assertTrue(result.proofPassed); assertTrue(result.completed)
    }
    @Test fun failedIncompleteAndErrorStayDistinctAndKeepPartialText() {
        for ((event, expected) in listOf(
            """{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"synthetic-secret"}}}""" to ApiProblem.USAGE_LIMIT,
            """{"type":"response.incomplete","response":{"status":"incomplete"}}""" to ApiProblem.INCOMPLETE,
            """{"type":"error","code":"subscription_sharing_usage_unavailable"}""" to ApiProblem.USAGE_UNAVAILABLE,
        )) {
            val result = StreamResult(); result.accept(ResponseEvent.Delta("partial"))
            val failure = assertThrows(ApiFailure::class.java) { result.accept(reader("data: $event\n\n").next()!!) }
            assertEquals(expected, failure.problem); assertFalse(result.completed); assertEquals("partial", result.output)
            assertFalse(failure.message.orEmpty().contains("synthetic-secret")); assertNull(failure.cause)
        }
    }
    @Test fun doneSentinelEofAndUnterminatedCompletionDoNotSucceed() {
        for (value in listOf("data: [DONE]\n\n", "data: $completed", "data: $completed\n")) assertNull(reader(value).next())
        assertFalse(StreamResult().proofPassed)
    }
    @Test fun malformedUtf8LargeFramesAndConflictingTypesFailClosed() {
        for (value in listOf("data: nope\n\n", "event: response.failed\ndata: $completed\n\n", "data: "+"x".repeat(65_537)+"\n\n",
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"incomplete\"}}\n\n")) {
            assertThrows(ApiFailure::class.java) { reader(value).next() }
        }
        assertThrows(Exception::class.java) { SseReader(ByteArrayInputStream(byteArrayOf(0xc3.toByte()))).next() }
    }
    @Test fun streamProtocolFailuresKeepStaticCategoryWithoutInputOrCause() {
        for ((value, detail) in listOf(
            "data: synthetic-private-body\n\n" to ProtocolDetail.SSE_EVENT_JSON,
            "data: {}\n\n" to ProtocolDetail.SSE_EVENT_TYPE,
            "event: response.failed\ndata: $completed\n\n" to ProtocolDetail.SSE_EVENT_MISMATCH,
            "data: {\"type\":\"response.output_text.delta\",\"delta\":123}\n\n" to ProtocolDetail.SSE_EVENT_SHAPE,
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"incomplete\"}}\n\n" to ProtocolDetail.SSE_EVENT_SHAPE,
            "data: " + "x".repeat(65_537) + "\n\n" to ProtocolDetail.SSE_LINE_LIMIT,
            (":" + "x".repeat(60_000) + "\n").repeat(5) to ProtocolDetail.SSE_FRAME_LIMIT,
        )) {
            val failure = assertThrows(ApiFailure::class.java) { reader(value).next() }
            assertEquals(detail, failure.protocolDetail)
            assertNull(failure.cause)
            assertFalse(failure.message.orEmpty().contains("synthetic-private-body"))
            assertEquals("[REDACTED]", failure.toString())
        }
        val badUtf8 = assertThrows(ApiFailure::class.java) { SseReader(ByteArrayInputStream(byteArrayOf(0xc3.toByte()))).next() }
        assertEquals(ProtocolDetail.INVALID_UTF8, badUtf8.protocolDetail)
        val oversized = assertThrows(ApiFailure::class.java) { StreamResult().accept(ResponseEvent.Delta("x".repeat(65_537))) }
        assertEquals(ProtocolDetail.OUTPUT_LIMIT, oversized.protocolDetail)
    }
    @Test fun completionWithoutStreamingOrWrongOutputDoesNotPassTheProof() {
        val noDelta = StreamResult(); noDelta.accept(ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT))
        assertTrue(noDelta.completed); assertFalse(noDelta.proofPassed)
        val mismatch = StreamResult(); mismatch.accept(ResponseEvent.Delta("other")); mismatch.accept(ResponseEvent.Completed("other"))
        assertFalse(mismatch.proofPassed)
        assertThrows(IllegalStateException::class.java) { mismatch.accept(ResponseEvent.Delta("late")) }
    }
}
