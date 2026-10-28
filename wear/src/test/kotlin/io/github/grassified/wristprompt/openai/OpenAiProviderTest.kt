package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.OpenAiAuth
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import okio.buffer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class OpenAiProviderTest {
    private fun http(reply: (Request) -> Pair<Int, String>, contentType: String = "application/json") = OpenAiHttp(
        OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = reply(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                .body(body.toResponseBody(contentType.toMediaType())).build()
        }.build())
    private val lease = CredentialLease("client", 1, "synthetic-bearer", 9999)

    @Test fun catalogUsesPublicEndpointAndOnlyBearerCredential() = runBlocking {
        val provider = OpenAiProvider(http({ request ->
            assertEquals("https://api.openai.com/v1/models", request.url.toString())
            assertEquals("Bearer synthetic-bearer", request.header("Authorization"))
            assertEquals("GET", request.method)
            200 to """{"models":[{"slug":"from-account","display_name":"Account name","visibility":"list"}]}"""
        }))
        assertEquals("from-account", provider.models(lease).single().slug)
    }
    @Test fun oversizedCatalogPreservesStaticFailureCategory() = runBlocking {
        val provider = OpenAiProvider(http({ 200 to " ".repeat(ModelCatalog.MAX_BYTES.toInt() + 1) }))
        try { provider.models(lease); fail("Expected rejection") }
        catch (failure: ApiFailure) {
            assertEquals(ProtocolDetail.BODY_TOO_LARGE, failure.protocolDetail)
            assertNull(failure.cause)
        }
    }
    @Test fun malformedUtf8IsCategorizedWithoutReturningBody() {
        val response = Response.Builder().request(Request.Builder().url("https://synthetic.invalid").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
            .body(byteArrayOf(0xc3.toByte()).toResponseBody("application/json".toMediaType())).build()
        response.use {
            val failure = assertThrows(ApiFailure::class.java) { OpenAiHttp().body(it) }
            assertEquals(ProtocolDetail.INVALID_UTF8, failure.protocolDetail)
            assertNull(failure.cause)
        }
    }
    @Test fun realHttpAdapterDispatchesSseAndSendsMinimalJson() = runBlocking {
        val provider = OpenAiProvider(http({ request ->
            assertEquals("https://api.openai.com/v1/responses", request.url.toString())
            assertEquals("POST", request.method); assertEquals("Bearer synthetic-bearer", request.header("Authorization"))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            assertEquals(ResponsesProtocol.request("account-model"), buffer.readUtf8())
            200 to "data: {\"type\":\"response.output_text.delta\",\"delta\":\"WristPrompt works.\"}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n"
        }, "text/event-stream"))
        val result = StreamResult(); provider.stream(lease, "account-model", result::accept); result.end()
        assertTrue(result.proofPassed)
    }
    @Test fun httpAdmissionFailurePreservesSafeStatusAndBodyShape() = runBlocking {
        val provider = OpenAiProvider(http({ 503 to """{"detail":"synthetic-sensitive-detail"}""" }))
        try { provider.models(lease); fail("Expected failure") }
        catch (failure: ApiFailure) {
            assertEquals(503, failure.status); assertEquals(ErrorShape.DETAIL, failure.shape)
            assertEquals(ApiProblem.TEMPORARY, failure.problem)
            assertFalse(failure.message.orEmpty().contains("synthetic-sensitive-detail"))
        }
    }
    @Test fun refreshSendsIssuedClientAndResourceWithoutScopeOrClientSecret() = runBlocking {
        val refresh = OpenAiRefresh(http({ request ->
            assertEquals(OpenAiAuth.TOKEN, request.url.toString())
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals(setOf("grant_type", "client_id", "refresh_token", "resource"), fields.keys)
            assertEquals("refresh_token", fields["grant_type"]); assertEquals("oaiapp_test", fields["client_id"])
            assertEquals("synthetic-refresh", fields["refresh_token"]); assertEquals(OpenAiAuth.RESOURCE, fields["resource"])
            assertNull(request.header("Authorization"))
            200 to """{"access_token":"rotated-access","refresh_token":"rotated-refresh","token_type":"Bearer","expires_in":3600}"""
        }))
        assertEquals("rotated-refresh", refresh.refresh(testProfile()).refreshToken)
    }
    @Test fun eofAndWrongContentTypeNeverComplete() = runBlocking {
        for (type in listOf("application/json", "text/event-stream")) {
            val provider = OpenAiProvider(http({ 200 to "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n" }, type))
            try { provider.stream(lease, "model") {}; fail("Expected rejection") }
            catch (failure: ApiFailure) { assertTrue(failure.problem in setOf(ApiProblem.PROTOCOL, ApiProblem.INTERRUPTED)) }
        }
    }
    @Test fun nonStreamingSuccessResponsesExposeOnlyStaticTypeAndStatus() = runBlocking {
        for ((type, detail) in listOf("application/json" to ProtocolDetail.RESPONSE_JSON,
            "text/html" to ProtocolDetail.RESPONSE_HTML, "text/plain" to ProtocolDetail.RESPONSE_OTHER_TYPE)) {
            val provider = OpenAiProvider(http({ 200 to "synthetic-sensitive-body" }, type))
            var emitted = false
            try { provider.stream(lease, "model") { emitted = true }; fail("Expected failure") }
            catch (failure: ApiFailure) {
                assertEquals(200, failure.status)
                assertEquals(detail, failure.protocolDetail)
                assertNull(failure.cause)
                assertFalse(failure.message.orEmpty().contains("synthetic-sensitive-body"))
            }
            assertFalse(emitted)
        }
    }
    private fun headerless(body: String, status: Int = 200, header: String? = null): OpenAiProvider {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("Synthetic").body(body.toResponseBody())
            header?.let { builder.header("Content-Type", it) }
            builder.build()
        }.build()
        return OpenAiProvider(OpenAiHttp(client))
    }
    @Test fun missingContentTypeCanCompleteOnlyThroughValidSse() = runBlocking {
        val provider = headerless("event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"WristPrompt works.\"}\n\nevent: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n")
        val result = StreamResult()
        provider.stream(lease, "model", result::accept)
        result.end()
        assertTrue(result.completed); assertTrue(result.proofPassed)
    }
    @Test fun missingContentTypeDoesNotAcceptJsonHtmlMalformedOrUnterminatedStreams() = runBlocking {
        for (body in listOf(
            """{"type":"response.completed","response":{"status":"completed"}}""",
            "<html>synthetic-private-body</html>",
            "data: synthetic-private-body\n\n",
            "data: {\"type\":\"response.output_text.delta\",\"delta\":\"WristPrompt works.\"}\n\n",
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}",
        )) {
            val result = StreamResult()
            try { headerless(body).stream(lease, "model", result::accept); fail("Expected rejection") }
            catch (failure: ApiFailure) {
                assertTrue(failure.problem in setOf(ApiProblem.PROTOCOL, ApiProblem.INTERRUPTED))
                assertFalse(failure.message.orEmpty().contains("synthetic-private-body"))
                assertNull(failure.cause)
            }
            assertFalse(result.completed); assertFalse(result.proofPassed)
        }
    }
    @Test fun missingContentTypePreservesFailedIncompleteAndHttpFailures() = runBlocking {
        for ((body, status, expected) in listOf(
            Triple("data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n", 200, ApiProblem.USAGE_LIMIT),
            Triple("data: {\"type\":\"response.incomplete\",\"response\":{\"status\":\"incomplete\"}}\n\n", 200, ApiProblem.INCOMPLETE),
            Triple("{}", 401, ApiProblem.PERMISSION),
            Triple("{}", 503, ApiProblem.TEMPORARY),
        )) {
            val result = StreamResult()
            try { headerless(body, status).stream(lease, "model", result::accept); fail("Expected rejection") }
            catch (failure: ApiFailure) { assertEquals(expected, failure.problem) }
            assertFalse(result.completed)
        }
    }
    @Test fun explicitlyInvalidContentTypeIsStillRejected() = runBlocking {
        try { headerless("data: {}\n\n", header = "invalid-content-type").stream(lease, "model") {}; fail("Expected rejection") }
        catch (failure: ApiFailure) { assertEquals(ProtocolDetail.RESPONSE_OTHER_TYPE, failure.protocolDetail) }
    }
    @Test fun unexpectedClientExceptionsAreCategorizedWithoutExceptionText() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { throw IllegalStateException("synthetic-sensitive-exception") }.build()
        try { OpenAiProvider(OpenAiHttp(client)).models(lease); fail("Expected failure") }
        catch (failure: ApiFailure) {
            assertEquals(ProtocolDetail.CLIENT_RESPONSE, failure.protocolDetail)
            assertNull(failure.cause)
            assertFalse(failure.message.orEmpty().contains("synthetic-sensitive-exception"))
        }
    }
    @Test fun cancellingStreamCancelsUnderlyingCallAndUnblocksRead() = runBlocking {
        val reading = CompletableDeferred<Unit>(); val cancelled = AtomicBoolean(false)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val source = object : okio.Source {
                override fun timeout() = okio.Timeout.NONE
                override fun close() {}
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    reading.complete(Unit)
                    val deadline = System.nanoTime() + 5_000_000_000L
                    while (!chain.call().isCanceled()) {
                        if (System.nanoTime() > deadline) throw java.io.IOException("Synthetic deadline")
                        Thread.sleep(10)
                    }
                    cancelled.set(true)
                    throw java.io.IOException("Synthetic cancellation")
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType() = "text/event-stream".toMediaType()
                override fun contentLength() = -1L
                override fun source() = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic").body(body).build()
        }.build()
        val job = launch { OpenAiProvider(OpenAiHttp(client)).stream(lease, "model") {} }
        withTimeout(3000) { reading.await() }
        withTimeout(3000) { job.cancelAndJoin() }
        assertTrue(cancelled.get())
    }

}
