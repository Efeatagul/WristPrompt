package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.BridgeProtocol
import io.github.grassified.wristprompt.bridge.BridgeProtocolException
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProfileProtocol {
    const val CONTEXT_PATH = "/wristprompt/v1/auth/context"
    const val TRANSFER_PATH = "/wristprompt/v1/auth/profile"
    const val FAILURE_PATH = "/wristprompt/v1/auth/failure"
    const val VERSION = 1
    val json = Json { encodeDefaults = true }

    inline fun <reified T> encode(value: T): ByteArray = json.encodeToString(value).encodeToByteArray().also {
        if (it.size > OpenAiAuth.MAX_PROFILE_BYTES) throw BridgeProtocolException()
    }
    inline fun <reified T> decode(bytes: ByteArray): T = try {
        if (bytes.isEmpty() || bytes.size > OpenAiAuth.MAX_PROFILE_BYTES) throw BridgeProtocolException()
        val result = json.decodeFromString<T>(bytes.decodeToString(throwOnInvalidSequence = true))
        if (!encode(result).contentEquals(bytes)) throw BridgeProtocolException()
        result
    } catch (_: Exception) { throw BridgeProtocolException() }

    fun validate(request: ContextRequest) {
        if (request.version != VERSION || !BridgeProtocol.isRequestId(request.requestId) ||
            !BridgeProtocol.isHostId(request.hostId)) throw BridgeProtocolException()
    }
    fun decodeTransfer(bytes: ByteArray): ProfileTransfer = try {
        decode<ProfileTransfer>(bytes).also {
            validate(ContextRequest(it.version, it.requestId, it.profile.hostId))
            require(BridgeProtocol.isRequestId(it.transferId))
            it.profile.validate()
        }
    } catch (_: Exception) { throw BridgeProtocolException() }
}

@Serializable
data class ContextRequest(val version: Int, val requestId: String, val hostId: String) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

@Serializable
data class LoginContext(val version: Int, val requestId: String, val identity: RegistrationIdentity?, val idTokenHint: String?, val hasSavedAccounts: Boolean = false) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

@Serializable
data class ProfileTransfer(val version: Int, val requestId: String, val transferId: String, val profile: CredentialProfile) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

@Serializable
data class ProfileAck(val version: Int, val requestId: String, val transferId: String) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

// Deliberately carries no raw provider error text.
@Serializable
data class AuthFailure(val version: Int, val requestId: String, val hostId: String) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}
