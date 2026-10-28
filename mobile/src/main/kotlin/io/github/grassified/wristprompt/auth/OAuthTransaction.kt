package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.BridgeProtocol
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

enum class AuthProblem {
    BROWSER, LISTENER, TIMEOUT, DENIED, STATE, CALLBACK, EXCHANGE, IDENTITY, SCOPES, TRANSFER, STORAGE, WATCH_UNAVAILABLE,
}
class AuthException(val problem: AuthProblem) : IllegalStateException("ChatGPT authorization: ${problem.name}")

object OAuthRandom {
    private val random = SecureRandom()
    fun value(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8)))
    fun equal(expected: String, actual: String): Boolean = MessageDigest.isEqual(expected.toByteArray(UTF_8), actual.toByteArray(UTF_8))
}

class OAuthTransaction(
    val hostId: String,
    val redirectUri: String,
    val registration: RegistrationIdentity?,
    val retainedIdToken: String?,
    val clientOverride: String? = null,
    val verifier: String = OAuthRandom.value(),
    val state: String = OAuthRandom.value(),
    val nonce: String = OAuthRandom.value(),
) {
    val selectedClient: String? get() = registration?.clientId ?: clientOverride
    init {
        require(BridgeProtocol.isHostId(hostId))
        val redirect = URI(redirectUri)
        require(redirect.scheme == "http" && redirect.host == "127.0.0.1" && redirect.port in 1..65535 &&
            redirect.rawPath == OpenAiAuth.CALLBACK_PATH && redirect.rawQuery == null && redirect.rawFragment == null && redirect.userInfo == null)
        registration?.validate()
        require(clientOverride == null || OpenAiAuth.validClient(clientOverride))
    }
    fun authorizationUrl(): String {
        val params = linkedMapOf(
            "client_id" to (selectedClient ?: OpenAiAuth.DYNAMIC_CLIENT),
            "ext_agent_host_id" to hostId,
            "response_type" to "code", "redirect_uri" to redirectUri,
            "scope" to OpenAiAuth.REQUESTED_SCOPES, "resource" to OpenAiAuth.RESOURCE,
            "state" to state, "nonce" to nonce, "code_challenge_method" to "S256",
            "code_challenge" to OAuthRandom.challenge(verifier),
        )
        if (selectedClient == null) params["agent_name_hint"] = "WristPrompt"
        if (registration != null && retainedIdToken != null) params["id_token_hint"] = retainedIdToken
        return OpenAiAuth.AUTHORIZE + "?" + params.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }
    }
    fun callback(target: String): AuthorizationCode {
        try {
            val uri = URI(target)
            require(!uri.isAbsolute && uri.rawPath == OpenAiAuth.CALLBACK_PATH && uri.rawFragment == null)
            val params = linkedMapOf<String, String>()
            val query = uri.rawQuery ?: throw AuthException(AuthProblem.CALLBACK)
            require(query.length <= 8192)
            for (part in query.split('&')) {
                val pair = part.split('=', limit = 2)
                require(pair.size == 2)
                val key = URLDecoder.decode(pair[0], "UTF-8")
                val value = URLDecoder.decode(pair[1], "UTF-8")
                require(key !in params && value.none { it.isISOControl() })
                params[key] = value
            }
            val returnedState = params["state"] ?: throw AuthException(AuthProblem.STATE)
            if (!OAuthRandom.equal(state, returnedState)) throw AuthException(AuthProblem.STATE)
            if ("error" in params) {
                require("code" !in params)
                throw AuthException(if (params["error"] == "access_denied") AuthProblem.DENIED else AuthProblem.CALLBACK)
            }
            val code = params["code"]?.takeIf { it.isNotBlank() } ?: throw AuthException(AuthProblem.CALLBACK)
            val clientId = params["client_id"] ?: selectedClient ?: throw AuthException(AuthProblem.CALLBACK)
            require(OpenAiAuth.validClient(clientId) && (selectedClient == null || clientId == selectedClient))
            return AuthorizationCode(code, clientId)
        } catch (e: AuthException) { throw e
        } catch (_: Exception) { throw AuthException(AuthProblem.CALLBACK) }
    }
    override fun toString() = SafeDiagnostics.REDACTED
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

class AuthorizationCode(val code: String, val clientId: String) {
    override fun toString() = SafeDiagnostics.REDACTED
}
