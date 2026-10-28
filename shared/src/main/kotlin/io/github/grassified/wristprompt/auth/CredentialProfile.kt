package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.BridgeProtocol
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object OpenAiAuth {
    const val ISSUER = "https://auth.openai.com"
    const val DISCOVERY = "$ISSUER/.well-known/openid-configuration"
    const val AUTHORIZE = "$ISSUER/api/accounts/authorize"
    const val TOKEN = "$ISSUER/api/accounts/oauth/token"
    const val JWKS = "$ISSUER/.well-known/jwks.json"
    const val RESOURCE = "https://api.openai.com/v1"
    const val DYNAMIC_CLIENT = "dynamic_agent_client"
    const val CALLBACK_PATH = "/auth/callback"
    const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    const val REQUESTED_SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    const val ATTEMPT_MS = 600_000L
    const val BROWSER_MS = 300_000L
    const val MAX_PROFILE_BYTES = 65_536

    fun validClient(value: String): Boolean = value.length in 1..256 &&
        value != DYNAMIC_CLIENT && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "_-" }

    fun scopes(raw: String): Set<String> {
        require(raw.length <= 4096 && raw.all { it == ' ' || it.code in 0x21..0x7e })
        return raw.split(' ').filter { it.isNotEmpty() }.toSet()
    }

    fun expiresAt(savedAt: Long, seconds: Long): Long {
        require(savedAt > 0 && seconds in 1..86_400)
        return Math.addExact(savedAt, Math.multiplyExact(seconds, 1000))
    }
}

@Serializable
data class RegistrationIdentity(val clientId: String, val issuer: String, val subject: String) {
    fun validate() {
        require(OpenAiAuth.validClient(clientId) && issuer == OpenAiAuth.ISSUER && subject.length in 1..512 && subject.isNotBlank())
    }
    override fun toString(): String = SafeDiagnostics.REDACTED
}

@Serializable
data class CredentialProfile(
    val identity: RegistrationIdentity,
    val hostId: String,
    val name: String?,
    val email: String?,
    val emailVerified: Boolean,
    val idToken: String,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenType: String?,
    val expiresIn: Long?,
    val savedAtEpochMs: Long,
    val scopes: Set<String>,
    val earliestRefreshAt: JsonElement? = null,
    val reauthorizationRequired: Boolean = false,
) {
    val planUsageEnabled: Boolean get() = !reauthorizationRequired && OpenAiAuth.PLAN_SCOPE in scopes &&
        "resource.invoke" in scopes && !accessToken.isNullOrBlank()
    val expiresAtEpochMs: Long? get() = expiresIn?.let { OpenAiAuth.expiresAt(savedAtEpochMs, it) }
    fun validate() {
        identity.validate()
        require(BridgeProtocol.isHostId(hostId) && savedAtEpochMs > 0)
        require(idToken.length in 1..24_000 && name.orEmpty().length <= 256 && email.orEmpty().length <= 320)
        require("openid" in scopes && scopes.size <= 64 && OpenAiAuth.scopes(scopes.joinToString(" ")) == scopes)
        require(accessToken == null || accessToken.length in 1..24_000)
        require(refreshToken == null || refreshToken.length in 1..8192)
        if (accessToken != null) require(tokenType.equals("Bearer", true) && expiresAtEpochMs != null)
        if (!reauthorizationRequired && OpenAiAuth.PLAN_SCOPE in scopes) require("resource.invoke" in scopes && accessToken != null)
        if (!reauthorizationRequired && "offline_access" in scopes) require(!refreshToken.isNullOrBlank())
        if (reauthorizationRequired) require(accessToken == null && refreshToken == null)
        expiresAtEpochMs
        // The current token reference names this field but does not specify its encoding.
        // Retain it opaquely; the later refresh implementation must verify its semantics.
        require(earliestRefreshAt == null || earliestRefreshAt.toString().length <= 256)
    }
    override fun toString(): String = SafeDiagnostics.REDACTED
}
