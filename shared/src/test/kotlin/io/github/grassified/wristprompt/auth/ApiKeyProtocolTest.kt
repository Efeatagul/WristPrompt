package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import org.junit.Assert.*
import org.junit.Test

class ApiKeyProtocolTest {
    private val value = ApiKeyTransfer(1, BridgeProtocol.newRequestId(), BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), "synthetic-user-key")
    @Test fun canonicalTransferAndAckAreBoundToRequest() {
        assertEquals(value, ApiKeyProtocol.transfer(ProfileProtocol.encode(value)))
        val ack = ProfileProtocol.encode(ProfileAck(1, value.requestId, value.transferId))
        ApiKeyProtocol.ack(ack, value.requestId, value.transferId)
        assertThrows(BridgeProtocolException::class.java) { ApiKeyProtocol.ack(ack, BridgeProtocol.newRequestId(), value.transferId) }
        assertFalse(value.toString().contains(value.key))
    }
    @Test fun minimalFormatDoesNotClaimKeyValidity() {
        assertTrue(ApiKeyProtocol.sane("clearly-fake-placeholder"))
        for (s in listOf("", "short", "has spaces", "contains\nnewline", "x".repeat(8193))) assertFalse(ApiKeyProtocol.sane(s))
    }
    @Test fun invalidTransferIsBoundedAndRedacted() {
        for (b in listOf(ByteArray(ApiKeyProtocol.MAX_BYTES + 1), "not-json-synthetic-user-key".encodeToByteArray(),
            ProfileProtocol.encode(value.copy(version = 2)), ProfileProtocol.encode(value.copy(hostId = "invalid")),
            ProfileProtocol.encode(value.copy(transferId = "invalid")), ProfileProtocol.encode(value.copy(key = "x\n")))) {
            val failure = assertThrows(BridgeProtocolException::class.java) { ApiKeyProtocol.transfer(b) }
            assertNull(failure.cause); assertFalse(failure.toString().contains(value.key))
        }
    }
}
