package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialProfile
import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.auth.OpenAiAuth
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.RoundingMode
import java.time.Instant

/** Shared wall-clock source for session expiry, receipt timestamps and retry gates. */
fun interface SessionClock {
    operator fun invoke(): Long
    companion object { val SYSTEM = SessionClock(System::currentTimeMillis) }
}

object TokenExpiry {
    const val MARGIN_MS = 60_000L
    fun needsRefresh(profile: CredentialProfile, now: Long): Boolean {
        if (profile.savedAtEpochMs > now + 30_000) throw ApiFailure(ApiProblem.REAUTHORIZE)
        val expiry = profile.expiresAtEpochMs ?: throw timingFailure()
        val needed = now >= expiry - MARGIN_MS
        if (!needed) return false
        val hint = profile.earliestRefreshAt
        // Missing/null is a supported legacy record, not an unsafe rotation marker.
        if (hint == null || hint == JsonNull) return true
        val earliest = earliestMillis(hint)
        if (earliest == null) {
            if (now < expiry) return false
            throw timingFailure()
        }
        if (now < earliest) {
            if (now < expiry) return false
            throw ApiFailure(ApiProblem.REFRESH_NOT_READY, protocolDetail = ProtocolDetail.REFRESH_NOT_READY)
        }
        return needed
    }
    private fun timingFailure() = ApiFailure(ApiProblem.CONFIGURATION, protocolDetail = ProtocolDetail.REFRESH_TIMING_UNDOCUMENTED)

    // OpenAI's Sign-in devkit packages/local/src/index.ts uses numeric Unix
    // seconds or a date string. Do not guess milliseconds or relative delays.
    // Accept only ISO instants for strings, stricter than JavaScript Date.parse.
    private fun earliestMillis(value: JsonElement): Long? { return try {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive.isString) {
            val instant = Instant.parse(primitive.content)
            Math.addExact(Math.multiplyExact(instant.epochSecond, 1000L), (instant.nano + 999_999L) / 1_000_000L)
        } else {
            primitive.content.toBigDecimal().multiply(1000.toBigDecimal())
                .setScale(0, RoundingMode.CEILING).longValueExact()
        }.takeIf { it >= 0 }
    } catch (_: Exception) { null } }
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

class CredentialLease(val clientId: String, val generation: Long, val accessToken: String, val expiresAt: Long, val mode: CredentialMode = CredentialMode.CHATGPT_PLAN, val modeRevision: Long = 0) {
    override fun toString() = SafeDiagnostics.REDACTED
}

fun interface OpenAiCredentialSource { suspend fun current(): CredentialLease }
