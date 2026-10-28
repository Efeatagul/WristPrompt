package io.github.grassified.wristprompt.openai

import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** No cache, credentials interceptor, redirects, logging, or automatic authenticated retries. */
class OpenAiHttp(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS).build()) {
    suspend fun <T> execute(request: Request, timeoutSeconds: Long = 120, block: suspend (Response) -> T): T = coroutineScope {
        val call = client.newCall(request)
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        val task = async(Dispatchers.IO) {
            try { call.execute().use { block(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ApiFailure) { throw failure }
            catch (_: IOException) { currentCoroutineContext().ensureActive(); throw ApiFailure(ApiProblem.NETWORK) }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = ProtocolDetail.CLIENT_RESPONSE) }
        }
        // Cancel the socket while the IO child is still reading, not after waiting for it.
        try { task.await() } finally { call.cancel() }
    }
    fun body(response: Response): String {
        val source = response.body.source()
        if (source.request(ResponsesProtocol.MAX_BODY_BYTES + 1)) throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = ProtocolDetail.BODY_TOO_LARGE)
        val bytes = source.buffer.readByteArray()
        try {
            if (bytes.size > ResponsesProtocol.MAX_BODY_BYTES) throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = ProtocolDetail.BODY_TOO_LARGE)
            return try { bytes.decodeToString(throwOnInvalidSequence = true) }
            catch (_: CharacterCodingException) { throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = ProtocolDetail.INVALID_UTF8) }
        } finally { bytes.fill(0) }
    }
    fun failure(response: Response, refresh: Boolean = false): ApiFailure = ApiErrors.parse(
        response.code, body(response), response.header("openai-request-id") ?: response.header("x-request-id"), refresh)
}
