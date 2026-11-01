package io.github.grassified.wristprompt.openai

import java.util.Base64
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GenerationOutputTest {
    private fun terminal(calls: List<JsonObject>) = buildJsonObject {
        put("type", "response.completed"); put("response", buildJsonObject {
            put("status", "completed"); put("output", JsonArray(calls))
        })
    }.toString()
    private fun call(encoded: String = "AQID") = buildJsonObject {
        put("type", "image_generation_call"); put("id", "ig_synthetic"); put("status", "completed"); put("result", encoded)
    }
    @Test fun completedOutputBecomesBytesAndMinimalOpaqueReplayWithNoEncodedResult() {
        val event = ResponseEvents.decode(null, terminal(listOf(call())), true) as ResponseEvent.Completed
        assertArrayEquals(byteArrayOf(1,2,3), event.generated!!.bytes)
        assertEquals(setOf("type", "id"), event.outputItems!!.single().keys)
        assertFalse(event.outputItems.toString().contains("AQID"))
    }
    @Test fun missingDuplicateMalformedAndUnreviewedToolResultsFailClosed() {
        val invalid = listOf(emptyList(), listOf(call(), call()), listOf(JsonObject(call() - "result")),
            listOf(JsonObject(call() + ("status" to JsonPrimitive("failed")))),
            listOf(JsonObject(call() + ("id" to JsonPrimitive("not-reviewed-id")))),
            listOf(JsonObject(call() + ("action" to JsonPrimitive("edit")))),
            listOf(JsonObject(call() + ("output_format" to JsonPrimitive("png")))),
            listOf(call(), buildJsonObject { put("type", "function_call"); put("name", "unexpected") }))
        invalid.forEach { assertThrows(ApiFailure::class.java) { ResponseEvents.decode(null, terminal(it), true) } }
    }
    @Test fun base64IsStrictCanonicalAndBounded() {
        listOf("", "AQI", "AR==", "AQ==\n", " A Q I D", "____", "AQID====", "!!!!").forEach {
            assertThrows(ApiFailure::class.java) { GenerationOutput.decode(it) }
        }
        val bytes = ByteArray(GenerationOutput.MAX_BYTES) { 1 }
        assertEquals(bytes.size, GenerationOutput.decode(Base64.getEncoder().encodeToString(bytes)).bytes.size)
        assertThrows(ApiFailure::class.java) { GenerationOutput.decode(Base64.getEncoder().encodeToString(bytes + 1)) }
    }
    @Test fun frameLineAndCumulativeEncodedStreamAreBoundedBeforeParsing() {
        assertThrows(ApiFailure::class.java) { ResponseEvents.decode(null, "[".repeat(40) + "]".repeat(40), true) }
        val largeLine = "data: " + "x".repeat(GenerationOutput.MAX_FRAME + 1) + "\n\n"
        assertThrows(ApiFailure::class.java) { SseReader(largeLine.byteInputStream(), true).next() }
        val comments = (":" + "x".repeat(60000) + "\n\n").repeat(211)
        assertThrows(ApiFailure::class.java) { SseReader(comments.byteInputStream(), true).next() }
        val large = call(Base64.getEncoder().encodeToString(ByteArray(100_000)))
        val frame = "data: ${terminal(listOf(large))}\n\n"
        assertNotNull(SseReader(frame.byteInputStream(), true).next())
        assertThrows(ApiFailure::class.java) { SseReader(frame.byteInputStream()).next() }
    }
    @Test fun toolCompletedSignalAloneNeverEstablishesResponseCompletion() {
        val reader = SseReader("data: {\"type\":\"response.image_generation_call.completed\",\"item_id\":\"ig_synthetic\"}\n\n".byteInputStream(), true)
        assertEquals(ResponseEvent.Ignored, reader.next()); assertNull(reader.next())
        assertThrows(ApiFailure::class.java) { StreamResult().end() }
    }
}
