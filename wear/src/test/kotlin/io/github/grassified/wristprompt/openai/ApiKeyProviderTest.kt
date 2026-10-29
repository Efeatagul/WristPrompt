package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ApiKeyProviderTest {
    private val lease = CredentialLease("openai_api_key", 1, "synthetic-user-key", Long.MAX_VALUE, CredentialMode.OPENAI_API_KEY, 2)
    private fun provider(reply: (Request) -> Pair<Int, String>, type: String = "application/json") = OpenAiProvider(OpenAiHttp(
        OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                .body(body.toResponseBody(type.toMediaType())).build()
        }.build()))
    @Test fun realKeyTestUsesApiModelMechanismWithoutOAuthCapabilityAssumptions() = runBlocking {
        val p = provider({ request ->
            assertEquals("https://api.openai.com/v1/models", request.url.toString())
            assertEquals("GET", request.method); assertEquals("Bearer synthetic-user-key", request.header("Authorization"))
            200 to """{"object":"list","data":[{"id":"api-model","owned_by":"openai"},{"id":"not-necessarily-responses-capable"}]}"""
        })
        assertEquals(listOf("api-model", "not-necessarily-responses-capable"), p.models(lease).map { it.slug })
        assertFalse(lease.toString().contains(lease.accessToken))
    }
    @Test fun unauthorizedQuotaAndServerErrorsStayInApiModeWithSanitizedDiagnostics() = runBlocking {
        for ((status, code, expected) in listOf(Triple(401, "invalid_api_key", ApiProblem.INVALID_API_KEY),
            Triple(429, "insufficient_quota", ApiProblem.API_LIMIT), Triple(500, "server_error", ApiProblem.TEMPORARY),
            Triple(400, "model_not_found", ApiProblem.NO_MODELS))) {
            var calls = 0
            val p = provider({ calls++; status to """{"error":{"code":"$code","message":"synthetic-user-key"}}""" })
            try { p.models(lease); fail("Expected failure") } catch (f: ApiFailure) {
                assertEquals(expected, f.problem); assertFalse(f.toString().contains(lease.accessToken)); assertNull(f.cause)
            }
            assertEquals(1, calls); assertEquals(CredentialMode.OPENAI_API_KEY, lease.mode)
        }
    }
    @Test fun networkFailureNeverRequestsOAuth() = runBlocking {
        val p = provider({ throw IOException("synthetic-user-key") })
        try { p.models(lease); fail("Expected failure") } catch (f: ApiFailure) {
            assertEquals(ApiProblem.NETWORK, f.problem); assertNull(f.cause); assertFalse(f.toString().contains(lease.accessToken))
        }
    }
    @Test fun apiResponseUsesSharedTransportAndCompletionSemantics() = runBlocking {
        val p = provider({ request ->
            assertEquals("https://api.openai.com/v1/responses", request.url.toString())
            assertEquals("Bearer synthetic-user-key", request.header("Authorization"))
            val b = okio.Buffer(); request.body!!.writeTo(b)
            val json = kotlinx.serialization.json.Json.parseToJsonElement(b.readUtf8()).toString()
            assertTrue(json.contains("BYOK path works.")); assertTrue(json.contains("\"store\":false")); assertTrue(json.contains("\"stream\":true"))
            200 to "data: {\"type\":\"response.output_text.delta\",\"delta\":\"BYOK path works.\"}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n"
        }, "text/event-stream")
        val r = StreamResult(ResponsesProtocol.API_EXPECTED_TEXT); p.stream(lease, "api-model", r::accept); r.end(); assertTrue(r.proofPassed)
    }
    @Test fun unsupportedModelsFailedIncompleteAndInterruptedNeverPass() = runBlocking {
        for (body in listOf("data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"model_not_found\"}}}\n\n",
            "data: {\"type\":\"response.incomplete\",\"response\":{\"status\":\"incomplete\"}}\n\n",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"BYOK path works.\"}\n\n")) {
            val r = StreamResult(ResponsesProtocol.API_EXPECTED_TEXT)
            try { provider({ 200 to body }, "text/event-stream").stream(lease, "unsupported", r::accept); r.end() } catch (_: ApiFailure) { }
            assertFalse(r.proofPassed)
        }
    }
    @Test fun apiCatalogRejectsPlanShapeMalformedDuplicateAndUnboundedIds() {
        for (body in listOf("{}", "synthetic-user-key", """{"models":[]}""", """{"data":[{"id":"x"},{"id":"x"}]}""", """{"data":[{"id":""}]}""")) {
            val f = assertThrows(ApiFailure::class.java) { ApiModelCatalog.read(body) }
            assertNull(f.cause); assertFalse(f.toString().contains("synthetic-user-key"))
        }
    }
}
