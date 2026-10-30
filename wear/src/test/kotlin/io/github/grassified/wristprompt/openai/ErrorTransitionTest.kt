package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.ui.errorPresentation
import io.github.grassified.wristprompt.ui.RecoveryAction
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test

class ErrorTransitionTest {
    @Test fun transportErrorTableKeepsModeAndSafeFailureCategory(): Unit = runBlocking {
        for (mode in CredentialMode.entries) for (case in listOf("401", "429", "503", "offline", "malformed")) {
            var calls = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls++
                assertEquals("Bearer synthetic-${mode.name}", chain.request().header("Authorization"))
                if (case == "offline") throw IOException("synthetic-private-network-detail")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(case.toIntOrNull() ?: 200).message("Synthetic")
                    .body((if (case == "malformed") "data: private-invalid-json\n\n" else """{"detail":"private-admission"}""")
                        .toResponseBody((if (case == "malformed") "text/event-stream" else "application/json").toMediaType())).build()
            }.build()
            val expected = when (case) {
                "401" -> if (mode == CredentialMode.OPENAI_API_KEY) ApiProblem.INVALID_API_KEY else ApiProblem.PERMISSION
                "429" -> if (mode == CredentialMode.OPENAI_API_KEY) ApiProblem.API_LIMIT else ApiProblem.USAGE_LIMIT
                "503" -> ApiProblem.TEMPORARY
                "offline" -> ApiProblem.NETWORK
                else -> ApiProblem.PROTOCOL
            }
            try {
                OpenAiProvider(OpenAiHttp(client)).stream(CredentialLease("client", 0, "synthetic-${mode.name}", Long.MAX_VALUE, mode),
                    "terra", StreamResult()::accept)
                fail("Expected safe failure")
            } catch (failure: ApiFailure) {
                assertEquals(expected, failure.problem); assertNull(failure.cause)
                assertFalse(failure.message.orEmpty().contains("private"))
                assertEquals("[REDACTED]", failure.toString())
            }
            assertEquals(1, calls)
        }
    }
    @Test fun recoveryGuidanceUsesExistingActionsWithoutBillingFallback() {
        assertEquals(RecoveryAction.ACCOUNT, errorPresentation(ApiProblem.INVALID_API_KEY).action)
        assertFalse(errorPresentation(ApiProblem.INVALID_API_KEY).text.contains("sign-in"))
        assertEquals(RecoveryAction.ACCOUNT, errorPresentation(ApiProblem.PERMISSION).action)
        assertEquals(RecoveryAction.ACCOUNT, errorPresentation(ApiProblem.REAUTHORIZE).action)
        assertEquals(RecoveryAction.MODELS, errorPresentation(ApiProblem.NO_MODELS).action)
        assertEquals(RecoveryAction.USAGE, errorPresentation(ApiProblem.USAGE_LIMIT).action)
        assertEquals(RecoveryAction.ACCOUNT, errorPresentation(ApiProblem.API_LIMIT).action)
        for (problem in listOf(ApiProblem.NETWORK, ApiProblem.TEMPORARY, ApiProblem.PROTOCOL))
            assertEquals(RecoveryAction.ASK, errorPresentation(problem).action)
    }
}
