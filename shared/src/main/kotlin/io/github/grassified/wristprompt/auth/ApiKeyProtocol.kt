package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import kotlinx.serialization.Serializable

enum class CredentialMode { CHATGPT_PLAN, OPENAI_API_KEY }

object ApiKeyProtocol {
    const val START_PATH = "/wristprompt/v1/api-key/start"
    const val TRANSFER_PATH = "/wristprompt/v1/api-key/transfer"
    const val MAX_BYTES = 12_288
    fun sane(key: String) = key.length in 8..8192 && key.all { it.code in 33..126 }
    fun start(bytes: ByteArray, now: Long): AuthStartRequest = BridgeCodec.decodeRequest(bytes, now)
    fun transfer(bytes: ByteArray): ApiKeyTransfer = try {
        require(bytes.size <= MAX_BYTES)
        ProfileProtocol.decode<ApiKeyTransfer>(bytes).also {
            ProfileProtocol.validate(ContextRequest(it.version, it.requestId, it.hostId))
            require(BridgeProtocol.isRequestId(it.transferId) && sane(it.key))
        }
    } catch (_: Exception) { throw BridgeProtocolException() }
    fun ack(bytes: ByteArray, request: String, transfer: String) = try {
        ProfileProtocol.decode<ProfileAck>(bytes).also {
            require(it.version == 1 && it.requestId == request && it.transferId == transfer)
        }
    } catch (_: Exception) { throw BridgeProtocolException() }
}

@Serializable
data class ApiKeyTransfer(val version: Int, val requestId: String, val transferId: String, val hostId: String, val key: String) {
    override fun toString() = SafeDiagnostics.REDACTED
}
