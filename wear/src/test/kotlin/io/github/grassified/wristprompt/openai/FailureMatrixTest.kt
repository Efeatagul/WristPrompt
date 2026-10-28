package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test

class FailureMatrixTest {
    private val codes = listOf(
        Triple("subscription_sharing_user_not_eligible", 403, ApiProblem.NOT_ELIGIBLE),
        Triple("subscription_sharing_usage_limit_exceeded", 429, ApiProblem.USAGE_LIMIT),
        Triple("subscription_sharing_usage_unavailable", 503, ApiProblem.USAGE_UNAVAILABLE),
        Triple("subscription_sharing_unsupported_capability", 400, ApiProblem.INVALID_REQUEST),
        Triple("subscription_sharing_route_not_supported", 403, ApiProblem.CONFIGURATION),
        Triple("subscription_sharing_invalid_user", 401, ApiProblem.PERMISSION),
        Triple("subscription_sharing_user_unavailable", 503, ApiProblem.TEMPORARY),
        Triple("chatpass_v2_scope_not_authorized", 403, ApiProblem.PERMISSION),
        Triple("chatpass_v2_invalid_authorization_context", 403, ApiProblem.PERMISSION),
    )
    @Test fun currentPlanErrorsKeepOnlySafeDiagnosticsBeforeAndDuringStreams(): Unit = runBlocking {
        for ((code, status, problem) in codes) for (streamed in listOf(false, true)) {
            var calls = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls++
                assertEquals("Bearer synthetic-plan", chain.request().header("Authorization"))
                val error = """{"error":{"code":"$code","param":"synthetic-field","message":"synthetic-sensitive-message"}}"""
                val body = if (streamed) "data: {\"type\":\"response.failed\",\"response\":$error}\n\n" else error
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(if (streamed) 200 else status).message("Synthetic")
                    .header("x-request-id", "req_synthetic")
                    .body(body.toResponseBody((if (streamed) "text/event-stream" else "application/json").toMediaType())).build()
            }.build()
            val result = StreamResult()
            try {
                OpenAiProvider(OpenAiHttp(client)).stream(CredentialLease("client", 0, "synthetic-plan", 999), "model", result::accept)
                fail("Expected synthetic failure")
            } catch (f: ApiFailure) {
                assertEquals(problem, f.problem); assertEquals(code, f.code)
                assertEquals(if (streamed) 200 else status, f.status)
                assertEquals("req_synthetic", f.requestId); assertEquals(ErrorShape.STRUCTURED, f.shape)
                assertNull(f.cause); assertFalse(f.message.orEmpty().contains("synthetic-sensitive"))
                assertEquals("[REDACTED]", f.toString())
            }
            assertEquals(1, calls); assertFalse(result.completed)
        }
    }
    @Test fun directAdmissionStatusesAreNotTerminalRefreshEvidence() {
        for ((status, problem) in listOf(401 to ApiProblem.PERMISSION, 403 to ApiProblem.PERMISSION, 503 to ApiProblem.TEMPORARY)) {
            val f = ApiErrors.parse(status, """{"detail":"synthetic-sensitive-admission"}""", "unsafe request identifier")
            assertEquals(problem, f.problem); assertEquals(ErrorShape.DETAIL, f.shape)
            assertNull(f.code); assertNull(f.requestId); assertFalse(ApiErrors.unusableRefresh(f.code))
        }
        assertTrue(ApiErrors.unusableRefresh("invalid_grant"))
        assertEquals(ApiProblem.REAUTHORIZE, ApiErrors.parse(400, """{"error":"invalid_grant"}""", refresh = true).problem)
    }
    @Test fun staleCatalogCannotSwitchRegistrationOrBillingEvenWithSameModelId(): Unit = runBlocking {
        val original = CredentialLease("client-a", 1, "synthetic-plan", 999)
        val replacements = listOf(
            CredentialLease("client-b", 1, "synthetic-other", 999),
            CredentialLease("client-a", 2, "synthetic-plan", 999),
            CredentialLease("client-a", 1, "synthetic-api", 999, CredentialMode.OPENAI_API_KEY, 1),
            CredentialLease("client-a", 1, "synthetic-plan", 999, modeRevision = 2),
        )
        for (replacement in replacements) {
            var current = original; var discoveries = 0; var posts = 0
            val provider = object : AiProvider {
                override suspend fun models(credential: CredentialLease): List<AiModel> { discoveries++; return listOf(AiModel("same-model", "Same")) }
                override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) { posts++ }
            }
            val session = InferenceSession({ current }, provider) { true }
            val old = session.catalog(); current = replacement
            try { session.test(old, "same-model", {}, {}); fail("Stale context must not post") }
            catch (f: ApiFailure) { assertEquals(ApiProblem.STALE_ACCOUNT, f.problem) }
            assertEquals(1, discoveries); assertEquals(0, posts)
        }
    }
}
