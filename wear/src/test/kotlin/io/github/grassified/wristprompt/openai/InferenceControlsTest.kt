package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.storage.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class InferenceControlsTest {
    private val mode = CredentialMode.OPENAI_API_KEY
    private val capability = ControlCapability("thinking", "openai", "opaque-model", mode.name,
        listOf("none", "medium", "ultra-Future"), "synthetic authoritative fixture")
    private val input = listOf(buildJsonObject { put("role", "user"); put("content", "synthetic") })
    private fun config(value: String) = InferenceConfiguration(controls = listOf(capability.selection(value)))
    @Test fun nativeStringsIncludingFutureValueAreExactAndDefaultIsOmitted() {
        val default = Json.parseToJsonElement(ResponsesProtocol.request("opaque-model", input)).jsonObject
        assertFalse(default.containsKey("reasoning"))
        assertFalse(default["store"]!!.jsonPrimitive.boolean); assertTrue(default["stream"]!!.jsonPrimitive.boolean)
        assertFalse(default.containsKey("previous_response_id"))
        capability.values.forEach { value ->
            val wire = Json.parseToJsonElement(ResponsesProtocol.request("opaque-model", input, config(value), capability, mode)).jsonObject
            assertEquals(value, wire["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            assertEquals(JsonArray(input), wire["input"])
        }
        assertNotEquals(InferenceConfiguration(), config("none"))
        assertNotEquals(InferenceConfiguration(), config("medium"))
    }
    @Test fun ownershipUnsupportedValuesAndUnknownControlKeysFailClosed() {
        val original = config("ultra-Future").controls.single()
        listOf(original.copy(value = "ULTRA-FUTURE"), original.copy(provider = "other"),
            original.copy(route = "CHATGPT_PLAN"), original.copy(model = "other"), original.copy(key = "unknown"))
            .forEach { invalid ->
                val failure = assertThrows(ApiFailure::class.java) {
                    ResponsesProtocol.request("opaque-model", input, InferenceConfiguration(controls = listOf(invalid)), capability, mode)
                }
                assertEquals(ApiProblem.THINKING_UNSUPPORTED, failure.problem); assertTrue(failure.requestNotSent)
            }
        assertThrows(ApiFailure::class.java) { ResponsesProtocol.request("opaque-model", input, config("none"), null, mode) }
    }
    @Test fun exactDocumentedApiIdsNeverInferFromNamesOrTransferToPlan() {
        val cap = OpenAiThinkingCapabilities.forModel("gpt-6.1-sol", mode)!!
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), cap.values)
        assertEquals("https://developers.openai.com/api/docs/models/gpt-6.1-sol", cap.source)
        listOf("gpt-6.1-sol-new", "gpt-6.1-sol-2026-11-01", "GPT-6.1-Sol", "reasoning", "unknown").forEach {
            assertNull(OpenAiThinkingCapabilities.forModel(it, mode))
        }
        assertNull(OpenAiThinkingCapabilities.forModel("gpt-6.1-sol", CredentialMode.CHATGPT_PLAN))
        val plan = ResponsesProtocol.models("""{"models":[{"slug":"gpt-6.1-sol","display_name":"Reasoning high max","visibility":"list","supported_reasoning_levels":[{"effort":"max"}]}]}""").single()
        assertNull(plan.thinking) // Undocumented catalog fields never become capabilities.
    }
    @Test fun oldSnapshotMigratesToDefaultAndNewConfigurationRoundTripsOutsideReplay() {
        val old = """{"version":1,"messages":[],"replay":[],"attempts":0,"nextId":0,"partial":"","terminal":"EMPTY"}"""
        val migrated = ConversationCodec.decode(old, "[]")
        assertEquals(InferenceConfiguration(), migrated.configuration)
        val chat = ChatConversation().apply { restore(migrated); configure(config("ultra-Future")) }
        val entry = ConversationEntry(owner = UUID.randomUUID().toString(), route = mode.name, model = "opaque-model",
            title = "Synthetic", updated = 1, snapshot = chat.snapshot())
        val (body, replay) = ConversationCodec.encode(entry)
        assertEquals("[]", replay)
        assertEquals(4, chat.snapshot().version)
        val restored = ChatConversation().apply { restore(ConversationCodec.decode(body, replay)) }
        assertEquals(config("ultra-Future"), restored.configuration)
        assertTrue(restored.snapshot().replay.isEmpty())
        assertFalse(restored.configuration.toString().contains("ultra-Future"))
    }
    @Test fun refreshedBearerCapabilitiesAreCheckedImmediatelyBeforeDispatch() = runBlocking {
        var lease = CredentialLease("synthetic", 0, "first", Long.MAX_VALUE, mode)
        var requests = 0
        var values = capability.values
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("opaque-model", "Name", capability.copy(values = values)))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("unused")
            override suspend fun stream(credential: CredentialLease, model: AiModel, input: List<JsonObject>, configuration: InferenceConfiguration,
                onEvent: suspend (ResponseEvent) -> Unit) { requests++; onEvent(ResponseEvent.Completed("answer")) }
        }
        val session = InferenceSession({ lease }, provider) { true }
        val catalog = session.catalog()
        lease = CredentialLease("synthetic", 0, "refreshed", Long.MAX_VALUE, mode); values = listOf("medium")
        try { session.chat(catalog, "opaque-model", input, config("ultra-Future")) {}; fail("Must reject stale capability") }
        catch (e: ApiFailure) { assertEquals(ApiProblem.THINKING_UNSUPPORTED, e.problem) }
        assertEquals(0, requests)
    }
    @Test fun actualOpenAiAdapterOmitsDefaultOnBothRoutesAndSendsOnlySupportedExactValues() = runBlocking {
        val bodies = mutableListOf<JsonObject>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer(); chain.request().body!!.writeTo(buffer)
            bodies += Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
                .body("data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val provider = OpenAiProvider(OpenAiHttp(client))
        for (route in CredentialMode.entries) {
            val lease = CredentialLease("synthetic", 0, "synthetic", Long.MAX_VALUE, route)
            val cap = OpenAiThinkingCapabilities.forModel("gpt-6-sol", route)
            val model = AiModel("gpt-6-sol", "Name", cap)
            provider.stream(lease, model, input, InferenceConfiguration()) {}
            assertFalse(bodies.last().containsKey("reasoning"))
            if (cap != null) for (value in cap.values) {
                provider.stream(lease, model, input, InferenceConfiguration(controls = listOf(cap.selection(value)))) {}
                assertEquals(value, bodies.last()["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            }
            val before = bodies.size
            try {
                provider.stream(lease, model, input, InferenceConfiguration(controls = listOf(NativeControl("thinking", "ultra", "openai", model.slug, route.name)))) {}
                fail("Unsupported must not dispatch")
            } catch (e: ApiFailure) { assertEquals(ApiProblem.THINKING_UNSUPPORTED, e.problem) }
            assertEquals(before, bodies.size)
        }
        client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll()
    }
}
