package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.openai.OpenAiHttp
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class OpenAiRevocationTest {
    private val credential = RevocationCredential("issued_client", "synthetic-refresh")
    private val endpoint = "https://auth.openai.com/discovered-revocation"
    private fun discovery(endpoint: String = this.endpoint) = """{"issuer":"https://auth.openai.com","revocation_endpoint":"$endpoint","revocation_endpoint_auth_methods_supported":["none"]}"""
    private fun transport(reply: (Request) -> Pair<Int, String>) = OpenAiRevocation(OpenAiHttp(OkHttpClient.Builder()
        .addInterceptor { chain ->
            val (status, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()))

    @Test fun discoveredEndpointAndEmpty200ConfirmEvenAlreadyInvalidToken() = runBlocking {
        var posts = 0
        val remote = transport { request ->
            assertNull(request.header("Authorization"))
            if (request.url.toString() == OpenAiAuth.DISCOVERY) 200 to discovery() else {
                posts++; assertEquals(endpoint, request.url.toString()); assertEquals("POST", request.method)
                val form = request.body as FormBody
                assertEquals(mapOf("token" to "synthetic-refresh", "token_type_hint" to "refresh_token", "client_id" to "issued_client"),
                    (0 until form.size).associate { form.name(it) to form.value(it) })
                200 to ""
            }
        }
        assertEquals(RevocationResult.CONFIRMED, remote.revoke(credential))
        assertEquals(RevocationResult.CONFIRMED, remote.revoke(credential))
        assertEquals(2, posts) // Same empty-200 contract applies to an already-invalid token.
        assertEquals("[REDACTED]", credential.toString())
    }
    @Test fun malformedMissingOrUntrustedDiscoveryNeverReceivesToken() = runBlocking {
        for (body in listOf("not-json", "{}", """{"issuer":"https://wrong.invalid","revocation_endpoint":"$endpoint"}""",
            discovery("http://auth.openai.com/revoke"), discovery("https://evil.invalid/revoke"),
            discovery("https://auth.openai.com:444/revoke"), discovery("https://user@auth.openai.com/revoke"),
            discovery("https://auth.openai.com/revoke?unexpected=value"), discovery().replace("[\"none\"]", "[\"client_secret_basic\"]"))) {
            var calls = 0
            val result = transport { request -> calls++; assertEquals(OpenAiAuth.DISCOVERY, request.url.toString()); 200 to body }.revoke(credential)
            assertEquals(RevocationResult.UNCONFIRMED, result); assertEquals(1, calls)
        }
    }
    @Test fun temporary5xxAndNetworkFailureRetryAtMostOnce() = runBlocking {
        for (mode in listOf("503", "network", "timeout")) {
            var posts = 0
            val remote = transport { request ->
                if (request.url.toString() == OpenAiAuth.DISCOVERY) 200 to discovery() else {
                    posts++
                    when (mode) {
                        "network" -> throw IOException("synthetic-private-value")
                        "timeout" -> throw SocketTimeoutException("synthetic-private-value")
                        else -> 503 to "synthetic-private-response"
                    }
                }
            }
            assertEquals(RevocationResult.UNCONFIRMED, remote.revoke(credential)); assertEquals(2, posts)
        }
    }
    @Test fun retryCanConfirmButNonempty200AndOtherStatusesCannot() = runBlocking {
        var posts = 0
        val retry = transport { request ->
            if (request.url.toString() == OpenAiAuth.DISCOVERY) 200 to discovery()
            else { posts++; if (posts == 1) 500 to "" else 200 to "" }
        }
        assertEquals(RevocationResult.CONFIRMED, retry.revoke(credential)); assertEquals(2, posts)
        for ((status, body) in listOf(200 to "unexpected", 204 to "", 400 to "{\"error\":\"invalid_token\"}", 401 to "", 302 to "")) {
            posts = 0
            val remote = transport { request ->
                if (request.url.toString() == OpenAiAuth.DISCOVERY) 200 to discovery()
                else { posts++; status to body }
            }
            assertEquals(RevocationResult.UNCONFIRMED, remote.revoke(credential)); assertEquals(1, posts)
        }
    }
    @Test fun failedDiscoveryAndDynamicClientNeverClaimRevocation() = runBlocking {
        assertEquals(RevocationResult.UNCONFIRMED, transport { throw IOException("synthetic") }.revoke(credential))
        assertEquals(RevocationResult.UNCONFIRMED, transport { error("No dynamic client request") }
            .revoke(RevocationCredential(OpenAiAuth.DYNAMIC_CLIENT, "synthetic")))
    }
}
