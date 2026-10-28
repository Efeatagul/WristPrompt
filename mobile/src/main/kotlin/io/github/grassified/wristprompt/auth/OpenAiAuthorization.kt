package io.github.grassified.wristprompt.auth

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OpenAiAuthorization {
    // No logging interceptor, disk cache, redirects, cookies, or automatic grant retries.
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val verifier = IdTokenVerifier()
    private val tokenJson = Json { ignoreUnknownKeys = true }

    suspend fun exchange(transaction: OAuthTransaction, code: AuthorizationCode): CredentialProfile {
        val discovery = Json.parseToJsonElement(get(OpenAiAuth.DISCOVERY)).jsonObject
        try {
            require(discovery["issuer"]?.jsonPrimitive?.content == OpenAiAuth.ISSUER)
            require(discovery["authorization_endpoint"]?.jsonPrimitive?.content == OpenAiAuth.AUTHORIZE)
            require(discovery["token_endpoint"]?.jsonPrimitive?.content == OpenAiAuth.TOKEN)
            require(discovery["jwks_uri"]?.jsonPrimitive?.content == OpenAiAuth.JWKS)
            require(discovery["id_token_signing_alg_values_supported"]?.jsonArray?.any { it.jsonPrimitive.content == "RS256" } == true)
        } catch (_: Exception) { throw AuthException(AuthProblem.IDENTITY) }
        val body = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", code.clientId)
            .add("code", code.code).add("code_verifier", transaction.verifier)
            .add("redirect_uri", transaction.redirectUri).add("resource", OpenAiAuth.RESOURCE).build()
        val savedAt: Long
        val tokens = try {
            val raw = request(Request.Builder().url(OpenAiAuth.TOKEN).post(body).build())
            savedAt = System.currentTimeMillis()
            tokenJson.decodeFromString<TokenResponse>(raw)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { throw AuthException(AuthProblem.EXCHANGE) }
        val scopes = try { OpenAiAuth.scopes(tokens.scope) } catch (_: Exception) { throw AuthException(AuthProblem.SCOPES) }
        if ("openid" !in scopes) throw AuthException(AuthProblem.SCOPES)
        // Retrieve trusted issuer keys for each exchange. Unknown kid naturally fetches a fresh set;
        // no token-controlled URL, persistent cache, or attacker-directed key lookup is accepted.
        val keys = get(OpenAiAuth.JWKS)
        val identity = verifier.verify(tokens.id_token, keys, code.clientId, transaction.nonce, savedAt,
            tokens.access_token, transaction.registration)
        return try {
            CredentialProfile(identity.identity, transaction.hostId, identity.name, identity.email, identity.emailVerified,
                tokens.id_token, tokens.access_token, tokens.refresh_token, tokens.token_type, tokens.expires_in,
                savedAt, scopes, tokens.earliest_refresh_at).also { it.validate() }
        } catch (_: Exception) { throw AuthException(AuthProblem.SCOPES) }
    }
    private suspend fun get(url: String) = request(Request.Builder().url(url).get().build())

    private suspend fun request(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(AuthException(AuthProblem.EXCHANGE))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        if (it.code != 200) throw AuthException(AuthProblem.EXCHANGE)
                        val bytes = it.body.byteStream().use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (output.size() <= 262_144) {
                                val count = stream.read(buffer, 0, minOf(buffer.size, 262_145 - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            buffer.fill(0)
                            output.toByteArray()
                         }
                        if (bytes.size > 262_144) throw AuthException(AuthProblem.EXCHANGE)
                        try { bytes.decodeToString(throwOnInvalidSequence = true) } finally { bytes.fill(0) }
                    }
                    if (continuation.isActive) continuation.resume(value)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(AuthException(AuthProblem.EXCHANGE))
                }
            }
        })
    }
}

@Serializable
private data class TokenResponse(
    val id_token: String, val scope: String, val access_token: String? = null,
    val refresh_token: String? = null, val token_type: String? = null, val expires_in: Long? = null,
    val earliest_refresh_at: JsonElement? = null,
) { override fun toString() = "[REDACTED]" }
