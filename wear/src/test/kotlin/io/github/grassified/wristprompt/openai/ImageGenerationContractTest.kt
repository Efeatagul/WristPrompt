package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Exact reviewed contract and fail-closed capability policy. Synthetic HTTP only. */
class ImageGenerationContractTest {
    private val api = CredentialMode.OPENAI_API_KEY
    private val plan = CredentialMode.CHATGPT_PLAN
    private fun model(id: String = "gpt-6.1-sol", mode: CredentialMode = api) =
        AiModel(id, "Synthetic", imageGeneration = OpenAiGeneration.capability(id, mode))
    private fun input(text: String = "synthetic prompt") = listOf(ImageInput.user(text))

    @Test fun exactReviewedIdsHaveApiCapabilityAndPlanAlwaysFailsClosed() {
        listOf("gpt-6.1-sol", "gpt-6-astra", "gpt-5.5", "gpt-5.4").forEach { id ->
            val capability = checkNotNull(OpenAiGeneration.capability(id, api))
            assertEquals("openai", capability.provider); assertEquals(id, capability.model)
            assertEquals(api.name, capability.route); assertEquals(OpenAiGeneration.SOURCE, capability.source)
            assertNull(OpenAiGeneration.capability(id, plan))
            val failure = assertThrows(ApiFailure::class.java) { OpenAiGeneration.request(model(id), plan, input()) }
            assertEquals(ApiProblem.GENERATION_UNSUPPORTED, failure.problem); assertTrue(failure.requestNotSent)
        }
    }
    @Test fun namesFamiliesUnknownAndMissingCapabilitiesNeverAuthorizeGeneration() {
        listOf("unknown", "gpt-6.1-sol-new", "gpt-6.1-sol-2026-11-01", "GPT-6.1-Sol", "gpt-image-2.5-flare").forEach { id ->
            assertNull(OpenAiGeneration.capability(id, api))
            assertThrows(ApiFailure::class.java) { OpenAiGeneration.request(model(id), api, input()) }
        }
        assertThrows(ApiFailure::class.java) { OpenAiGeneration.request(AiModel("gpt-6.1-sol", "Image generation supported"), api, input()) }
    }
    @Test fun providerModelRouteAndReviewedSourceMustMatch() {
        val cap = checkNotNull(model().imageGeneration)
        listOf(cap.copy(provider = "future-provider"), cap.copy(model = "other"), cap.copy(route = plan.name), cap.copy(source = "unreviewed"))
            .forEach { invalid ->
                assertThrows(ApiFailure::class.java) { OpenAiGeneration.request(model().copy(imageGeneration = invalid), api, input()) }
            }
    }
    @Test fun forcedGenerateRequestUsesReviewedToolAndOmitsThinkingAndContinuationId() {
        val exact = " synthetic\nprompt "
        val controls = OpenAiThinkingCapabilities.forModel("gpt-6.1-sol", api)!!
        val selectedThinking = InferenceConfiguration(controls = listOf(controls.selection("medium")))
        val wire = Json.parseToJsonElement(OpenAiGeneration.request(model().copy(thinking = controls), api, input(exact))).jsonObject
        assertEquals("gpt-6.1-sol", wire["model"]!!.jsonPrimitive.content)
        assertTrue("Exact prompt preserved without printing payload", wire["input"] == JsonArray(input(exact)))
        assertFalse(wire["store"]!!.jsonPrimitive.boolean); assertTrue(wire["stream"]!!.jsonPrimitive.boolean)
        assertFalse(wire.containsKey("reasoning")); assertFalse(wire.containsKey("previous_response_id"))
        val tool = wire["tools"]!!.jsonArray.single().jsonObject
        assertEquals("image_generation", tool["type"]!!.jsonPrimitive.content)
        assertEquals(OpenAiGeneration.TOOL_MODEL, tool["model"]!!.jsonPrimitive.content)
        assertEquals("generate", tool["action"]!!.jsonPrimitive.content)
        assertEquals("1024x1024", tool["size"]!!.jsonPrimitive.content)
        assertEquals("low", tool["quality"]!!.jsonPrimitive.content)
        assertEquals("jpeg", tool["output_format"]!!.jsonPrimitive.content)
        assertEquals(80, tool["output_compression"]!!.jsonPrimitive.int)
        assertEquals("image_generation", wire["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("medium", selectedThinking.controls.single().value)
    }
    @Test fun blankOversizedOrNonUserPromptAndInputImagesAreRejected() {
        val invalid = listOf(emptyList(), listOf(buildJsonObject { put("role", "user"); put("content", "  ") }),
            listOf(buildJsonObject { put("role", "user"); put("content", "x".repeat(8193)) }),
            listOf(buildJsonObject { put("role", "assistant"); put("content", "synthetic") }),
            listOf(buildJsonObject { put("role", "user"); put("content", 42) }),
            listOf(ImageInput.user("synthetic", listOf(java.util.UUID.randomUUID().toString()))))
        invalid.forEach {
            val failure = assertThrows(ApiFailure::class.java) { OpenAiGeneration.request(model(), api, it) }
            assertEquals(ApiProblem.GENERATION_PROMPT, failure.problem); assertTrue(failure.requestNotSent)
        }
        assertTrue(OpenAiGeneration.request(model(), api, input("x".repeat(8192))).isNotBlank())
    }
    @Test fun catalogEnrichmentDoesNotDispatchOrUseAnotherRouteOrBearer() = runBlocking {
        for (mode in CredentialMode.entries) {
            var calls = 0
            val lease = CredentialLease("synthetic-client", 0, "synthetic-token", Long.MAX_VALUE, mode)
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls++
                check(chain.request().method == "GET")
                check(chain.request().url.toString() == ResponsesProtocol.MODELS_URL)
                check(chain.request().header("Authorization") == "Bearer synthetic-token")
                val body = if (mode == api) """{"data":[{"id":"gpt-6.1-sol"},{"id":"unknown"}]}"""
                    else """{"models":[{"slug":"gpt-6.1-sol","display_name":"Synthetic","visibility":"list","image_generation":true}]}"""
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            try {
                val models = OpenAiProvider(OpenAiHttp(client)).models(lease)
                assertEquals(1, calls)
                if (mode == api) {
                    assertNotNull(models.first().imageGeneration); assertNull(models.last().imageGeneration)
                } else assertNull(models.single().imageGeneration)
            } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }
    @Test fun ordinaryTextAndImageInputNeverAcquireGenerationTools() {
        val wire = Json.parseToJsonElement(ResponsesProtocol.request("gpt-6.1-sol", input(), mode = api)).jsonObject
        assertFalse(wire.containsKey("tools")); assertFalse(wire.containsKey("tool_choice"))
        assertTrue(OpenAiImageCapabilities.supports("gpt-6.1-sol", plan))
        assertNull(OpenAiGeneration.capability("gpt-6.1-sol", plan))
        assertFalse(checkNotNull(model().imageGeneration).toString().contains("gpt-6.1-sol"))
    }
    @Test fun refreshedCatalogLosingGenerationCapabilityCannotDispatch() = runBlocking {
        var lease = CredentialLease("synthetic", 0, "first", Long.MAX_VALUE, api)
        var dispatches = 0
        val p = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(if (credential.accessToken == "first") model() else AiModel("gpt-6.1-sol", "Synthetic"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = error("unused")
            override suspend fun generateImage(credential: CredentialLease, model: AiModel, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) { dispatches++ }
        }
        val session = InferenceSession({ lease }, p) { true }
        val catalog = session.catalog(); lease = CredentialLease("synthetic", 0, "second", Long.MAX_VALUE, api)
        try { session.image(catalog, "gpt-6.1-sol", input()) {}; fail("Unreviewed refreshed capability must fail") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.GENERATION_UNSUPPORTED, failure.problem) }
        assertEquals(0, dispatches)
    }

}
