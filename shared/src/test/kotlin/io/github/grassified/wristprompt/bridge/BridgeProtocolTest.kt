package io.github.grassified.wristprompt.bridge

import org.junit.Assert.*
import org.junit.Test

class BridgeProtocolTest {
    private val now = 1_000_000L
    private val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), now)

    @Test fun generatedHostIdsAreCanonicalRandomUuidV4Uris() {
        val ids = List(100) { BridgeProtocol.newHostId() }
        assertEquals(100, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(BridgeProtocol.isHostId(id))
            val uuid = java.util.UUID.fromString(id.removePrefix("urn:uuid:"))
            assertEquals(4, uuid.version())
            assertEquals(2, uuid.variant())
        }
    }

    @Test fun identityStringsAndWrongUuidVersionsAreRejected() {
        listOf("user@example.com", "urn:uuid:user@example.com", "urn:uuid:00000000-0000-1000-8000-000000000000")
            .forEach { assertFalse(BridgeProtocol.isHostId(it)) }
    }

    @Test fun requestRoundTrip() {
        assertEquals(request, BridgeCodec.decodeRequest(BridgeCodec.encode(request), now))
    }

    @Test fun receiptIsCorrelatedToThePendingRequest() {
        val receipt = AuthStartReceipt(1, request.requestId)
        assertEquals(receipt, BridgeCodec.decodeReceipt(BridgeCodec.encode(receipt), request.requestId))
        assertThrows(BridgeProtocolException::class.java) {
            BridgeCodec.decodeReceipt(BridgeCodec.encode(receipt), BridgeProtocol.newRequestId())
        }
    }

    @Test fun unknownVersionAndInvalidHostAreRejected() {
        val encoded = BridgeCodec.encode(request).decodeToString()
        listOf(
            encoded.replace("\"version\":1", "\"version\":2"),
            encoded.replace(request.hostId, "urn:uuid:not-a-uuid"),
            encoded.replace(request.requestId, "bad-request"),
        ).forEach { reject(it.encodeToByteArray()) }
    }

    @Test fun unknownAndDuplicateFieldsAreRejected() {
        val encoded = BridgeCodec.encode(request).decodeToString()
        reject(encoded.replaceFirst("{", "{\"access_token\":\"synthetic-secret\",").encodeToByteArray())
        reject(encoded.replaceFirst("{", "{\"version\":1,").encodeToByteArray())
    }

    @Test fun malformedUtf8EmptyTruncatedAndOversizedInputsAreRejected() {
        listOf(byteArrayOf(), byteArrayOf(0xc3.toByte(), 0x28), "{".encodeToByteArray(),
            ByteArray(BridgeProtocol.MAX_PAYLOAD_BYTES + 1) { 32 }).forEach(::reject)
    }

    @Test fun staleOrExcessivelyFutureRequestsAreRejected() {
        reject(BridgeCodec.encode(request.copy(createdAtEpochMs = now - BridgeProtocol.REQUEST_LIFETIME_MS - 1)))
        reject(BridgeCodec.encode(request.copy(createdAtEpochMs = now + BridgeProtocol.CLOCK_SKEW_MS + 1)))
    }

    @Test fun freshnessBoundaryIsExplicit() {
        for (created in listOf(now - BridgeProtocol.REQUEST_LIFETIME_MS, now + BridgeProtocol.CLOCK_SKEW_MS)) {
            assertEquals(created, BridgeCodec.decodeRequest(BridgeCodec.encode(request.copy(createdAtEpochMs = created)), now).createdAtEpochMs)
        }
    }

    @Test fun trailingDataAndNonCanonicalJsonAreRejected() {
        reject(BridgeCodec.encode(request) + "garbage".encodeToByteArray())
        reject((" " + BridgeCodec.encode(request).decodeToString()).encodeToByteArray())
    }

    @Test fun invalidReceiptVersionAndExtraFieldsAreRejected() {
        val encoded = BridgeCodec.encode(AuthStartReceipt(1, request.requestId)).decodeToString()
        for (input in listOf(encoded.replace("\"version\":1", "\"version\":2"), encoded.replaceFirst("{", "{\"token\":\"synthetic-secret\","))) {
            assertThrows(BridgeProtocolException::class.java) {
                BridgeCodec.decodeReceipt(input.encodeToByteArray(), request.requestId)
            }
        }
    }

    @Test fun parserFailureDoesNotExposePayloadOrCause() {
        val exception = assertThrows(BridgeProtocolException::class.java) { rejectDirect("synthetic-secret") }
        assertFalse(exception.toString().contains("synthetic-secret"))
        assertNull(exception.cause)
    }

    @Test fun protocolObjectsDoNotPrintTheirContents() {
        assertEquals(SafeDiagnostics.REDACTED, request.toString())
        assertEquals(SafeDiagnostics.REDACTED, AuthStartReceipt(1, request.requestId).toString())
    }

    private fun reject(bytes: ByteArray) {
        assertThrows(BridgeProtocolException::class.java) { BridgeCodec.decodeRequest(bytes, now) }
    }

    private fun rejectDirect(value: String) { BridgeCodec.decodeRequest(value.encodeToByteArray(), now) }
}
