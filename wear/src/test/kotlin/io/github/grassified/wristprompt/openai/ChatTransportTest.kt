package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ChatTransportTest {
    @Test fun arbitraryTextHistoryUsesExistingAuthenticatedResponsesTransportInBothModes(): Unit = runBlocking {
        val input = listOf(buildJsonObject { put("role", "user"); put("content", "Türkçe 👋\nA real question?") })
        for (mode in CredentialMode.entries) {
            var posts = 0
            val http = OpenAiHttp(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request(); assertEquals("Bearer synthetic", request.header("Authorization"))
                val modelCall = request.url.toString() == ResponsesProtocol.MODELS_URL
                val body = if (modelCall) {
                    if (mode == CredentialMode.CHATGPT_PLAN) """{"models":[{"slug":"model","display_name":"Model","visibility":"list"}]}"""
                    else """{"data":[{"id":"model"}]}"""
                } else {
                    posts++; assertEquals(ResponsesProtocol.RESPONSES_URL, request.url.toString())
                    val payload = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
                    assertEquals(setOf("model", "input", "store", "stream"), payload.keys)
                    assertEquals(JsonArray(input), payload["input"])
                    assertFalse(payload["store"]!!.jsonPrimitive.boolean); assertTrue(payload["stream"]!!.jsonPrimitive.boolean)
                    "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Answer\"}\n\n" +
                        "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[" +
                        "{\"type\":\"reasoning\",\"id\":\"r\",\"summary\":[],\"encrypted_content\":\"opaque\"}," +
                        "{\"type\":\"message\",\"role\":\"assistant\",\"phase\":\"final_answer\",\"content\":[{\"type\":\"output_text\",\"text\":\"Answer\",\"annotations\":[]}]}]}}\n\n"
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body.toResponseBody((if (modelCall) "application/json" else "text/event-stream").toMediaType())).build()
            }.build())
            val lease = CredentialLease("client", 0, "synthetic", Long.MAX_VALUE, mode)
            val session = InferenceSession({ lease }, OpenAiProvider(http)) { true }
            val result = session.chat(session.catalog(), "model", input) {}
            assertTrue(result.completed); assertEquals("Answer", result.output); assertEquals(1, posts)
            assertEquals("opaque", result.outputItems!![0]["encrypted_content"]!!.jsonPrimitive.content)
        }
    }
    @Test fun responseItemReplayPreservesPhaseAndReasoningWithoutContinuationId() {
        val output = Json.parseToJsonElement("""{"type":"response.completed","response":{"status":"completed","output":[
            {"type":"reasoning","id":"r","summary":[],"encrypted_content":"opaque"},
            {"type":"message","role":"assistant","phase":"final_answer","content":[{"type":"output_text","text":"A","annotations":[]}]}]}}""")
        val event = ResponseEvents.decode(null, output.toString()) as ResponseEvent.Completed
        val c = ChatConversation(); c.draft("First"); c.begin()
        c.complete(StreamResult().apply { accept(event); end() }); c.draft("Follow-up")
        val payload = Json.parseToJsonElement(ResponsesProtocol.request("model", c.begin()!!)).jsonObject
        assertFalse(payload.containsKey("previous_response_id"))
        assertEquals("opaque", payload["input"]!!.jsonArray[1].jsonObject["encrypted_content"]!!.jsonPrimitive.content)
        assertEquals("final_answer", payload["input"]!!.jsonArray[2].jsonObject["phase"]!!.jsonPrimitive.content)
    }
    @Test fun staleCatalogCannotSendOldConversationUnderNewAccount(): Unit = runBlocking {
        var lease = CredentialLease("old", 0, "synthetic", Long.MAX_VALUE)
        var posts = 0
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("model", "Model"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("Not proof")
            override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) { posts++ }
        }
        val session = InferenceSession({ lease }, provider) { it == lease }
        val catalog = session.catalog(); lease = CredentialLease("new", 1, "synthetic", Long.MAX_VALUE)
        try { session.chat(catalog, "model", listOf(buildJsonObject { put("role", "user"); put("content", "Private old history") })) {}; fail("Must reject") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.STALE_ACCOUNT, failure.problem) }
        assertEquals(0, posts)
    }
}
