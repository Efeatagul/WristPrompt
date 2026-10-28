package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialProfile
import io.github.grassified.wristprompt.auth.OpenAiAuth
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** Shared wall-clock source for session expiry, receipt timestamps and retry gates. */
fun interface SessionClock {
    operator fun invoke(): Long
    companion object { val SYSTEM = SessionClock(System::currentTimeMillis) }
}

object TokenExpiry {
    const val MARGIN_MS = 60_000L
    fun needsRefresh(profile: CredentialProfile, now: Long): Boolean {
        if (profile.savedAtEpochMs > now + 30_000) throw ApiFailure(ApiProblem.REAUTHORIZE)
        val expiry = profile.expiresAtEpochMs ?: return true
        val needed = now >= expiry - MARGIN_MS
        // Official docs name the field but define neither encoding nor units. Do not
        // guess a timestamp and potentially send a rotating grant before permitted.
        if (needed && profile.earliestRefreshAt != null && profile.earliestRefreshAt != JsonNull) {
            // The current bearer can still be used until expiry without submitting
            // an early rotating grant. Expiry itself does not resolve unknown units.
            if (now < expiry) return false
            throw ApiFailure(ApiProblem.CONFIGURATION, protocolDetail = ProtocolDetail.REFRESH_TIMING_UNDOCUMENTED)
        }
        return needed
    }
}

object RefreshProtocol {
    private val json = Json { ignoreUnknownKeys = true }
    fun update(profile: CredentialProfile, body: String, receivedAt: Long): CredentialProfile = try {
        require(body.length <= ResponsesProtocol.MAX_BODY_BYTES)
        val tokens = json.decodeFromString<RefreshTokens>(body)
        require(tokens.access_token.isNotBlank() && tokens.refresh_token.isNotBlank())
        val scopes = tokens.scope?.let(OpenAiAuth::scopes) ?: profile.scopes
        profile.copy(accessToken = tokens.access_token, refreshToken = tokens.refresh_token,
            tokenType = tokens.token_type, expiresIn = tokens.expires_in, savedAtEpochMs = receivedAt,
            scopes = scopes, earliestRefreshAt = tokens.earliest_refresh_at, reauthorizationRequired = false).also { it.validate() }
        // Identity and the previously verified ID-token hint remain unchanged. This grant
        // renews access; an optional returned ID token is not trusted/used for a new identity.
    } catch (_: Exception) { throw ApiFailure(ApiProblem.PROTOCOL) }
}

@Serializable
private data class RefreshTokens(
    val access_token: String, val refresh_token: String, val token_type: String,
    val expires_in: Long, val scope: String? = null, val earliest_refresh_at: JsonElement? = null,
) { override fun toString() = SafeDiagnostics.REDACTED }

class CredentialLease(val clientId: String, val generation: Long, val accessToken: String, val expiresAt: Long) {
    override fun toString() = SafeDiagnostics.REDACTED
}

fun interface OpenAiCredentialSource { suspend fun current(): CredentialLease }
