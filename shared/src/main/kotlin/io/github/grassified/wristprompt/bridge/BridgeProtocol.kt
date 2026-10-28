package io.github.grassified.wristprompt.bridge

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BridgeProtocol {
    const val AUTH_START_PATH = "/wristprompt/v1/auth/start"
    const val MOBILE_CAPABILITY = "wristprompt_mobile"
    const val WEAR_CAPABILITY = "wristprompt_wear"
    const val VERSION = 1
    const val MAX_PAYLOAD_BYTES = 1024
    const val REQUEST_LIFETIME_MS = 120_000L
    const val CLOCK_SKEW_MS = 30_000L
    const val TIMEOUT_MS = 15_000L

    private val uuidV4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    fun newRequestId(): String = UUID.randomUUID().toString()
    fun isRequestId(value: String): Boolean = uuidV4.matches(value)
    fun newHostId(): String = "urn:uuid:${UUID.randomUUID()}"
    fun isHostId(value: String): Boolean =
        value.startsWith("urn:uuid:") && uuidV4.matches(value.removePrefix("urn:uuid:"))
}

@Serializable
data class AuthStartRequest(
    val version: Int,
    val requestId: String,
    val hostId: String,
    val createdAtEpochMs: Long,
) {
    fun isFresh(now: Long): Boolean = createdAtEpochMs > 0 &&
        createdAtEpochMs >= now - BridgeProtocol.REQUEST_LIFETIME_MS &&
        createdAtEpochMs <= now + BridgeProtocol.CLOCK_SKEW_MS

    override fun toString(): String = SafeDiagnostics.REDACTED
}

@Serializable
data class AuthStartReceipt(val version: Int, val requestId: String) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

class BridgeProtocolException : IllegalArgumentException("Invalid authentication bridge payload")

object BridgeCodec {
    private val json = Json { encodeDefaults = true }

    fun encode(request: AuthStartRequest): ByteArray {
        validate(request)
        return json.encodeToString(request).encodeToByteArray()
    }

    fun decodeRequest(bytes: ByteArray, now: Long): AuthStartRequest = safely {
        val request = json.decodeFromString<AuthStartRequest>(text(bytes))
        validate(request)
        if (!request.isFresh(now)) throw BridgeProtocolException()
        // A single canonical form rejects duplicate keys, extra fields, and ambiguous encodings.
        if (!encode(request).contentEquals(bytes)) throw BridgeProtocolException()
        request
    }

    fun encode(receipt: AuthStartReceipt): ByteArray {
        if (receipt.version != BridgeProtocol.VERSION || !BridgeProtocol.isRequestId(receipt.requestId)) {
            throw BridgeProtocolException()
        }
        return json.encodeToString(receipt).encodeToByteArray()
    }

    fun decodeReceipt(bytes: ByteArray, expectedRequestId: String): AuthStartReceipt = safely {
        val receipt = json.decodeFromString<AuthStartReceipt>(text(bytes))
        if (receipt.requestId != expectedRequestId || !encode(receipt).contentEquals(bytes)) {
            throw BridgeProtocolException()
        }
        receipt
    }

    private fun validate(request: AuthStartRequest) {
        if (request.version != BridgeProtocol.VERSION || !BridgeProtocol.isRequestId(request.requestId) ||
            !BridgeProtocol.isHostId(request.hostId) || request.createdAtEpochMs <= 0
        ) throw BridgeProtocolException()
    }

    private fun text(bytes: ByteArray): String {
        if (bytes.isEmpty() || bytes.size > BridgeProtocol.MAX_PAYLOAD_BYTES) throw BridgeProtocolException()
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }

    private inline fun <T> safely(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        // Serialization exceptions can contain the original input. Never propagate their message/cause.
        throw BridgeProtocolException()
    }
}
