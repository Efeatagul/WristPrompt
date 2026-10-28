package io.github.grassified.wristprompt.bridge

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WatchAuthBridgeTest {
    private val now = 1_000_000L
    private val host = BridgeProtocol.newHostId()
    private class Transport(var peers: List<WearPeer>) : AuthBridgeTransport {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        var reply: suspend (ByteArray) -> ByteArray = { bytes ->
            BridgeCodec.encode(AuthStartReceipt(1, BridgeCodec.decodeRequest(bytes, 1_000_000L).requestId))
        }
        override suspend fun reachablePeers() = peers
        override suspend fun requestStart(nodeId: String, payload: ByteArray): ByteArray {
            sent += nodeId to payload
            return reply(payload)
        }
    }

    @Test fun noPhoneSendsNothing() = runTest {
        val transport = Transport(emptyList())
        assertFalse(WatchAuthBridge({ host }, transport) { now }.request())
        assertTrue(transport.sent.isEmpty())
    }

    @Test fun onlyOneNearbyPhoneIsSelectedWithoutBroadcast() = runTest {
        val transport = Transport(listOf(WearPeer("remote", false), WearPeer("phone", true)))
        assertTrue(WatchAuthBridge({ host }, transport) { now }.request())
        assertEquals(listOf("phone"), transport.sent.map { it.first })
    }

    @Test fun ambiguousPhonesAreRejectedWithoutSending() = runTest {
        val transport = Transport(listOf(WearPeer("phone-a", true), WearPeer("phone-b", true)))
        try {
            WatchAuthBridge({ host }, transport) { now }.request()
            fail("Expected ambiguity rejection")
        } catch (_: AmbiguousCompanionException) { }
        assertTrue(transport.sent.isEmpty())
    }

    @Test fun attemptsReuseHostButHaveFreshRequestIds() = runTest {
        val transport = Transport(listOf(WearPeer("phone", true)))
        val bridge = WatchAuthBridge({ host }, transport) { now }
        bridge.request(); bridge.request()
        val requests = transport.sent.map { BridgeCodec.decodeRequest(it.second, now) }
        assertEquals(listOf(host, host), requests.map { it.hostId })
        assertNotEquals(requests[0].requestId, requests[1].requestId)
    }

    @Test fun unrelatedReceiptCannotReportSuccess() = runTest {
        val transport = Transport(listOf(WearPeer("phone", true)))
        transport.reply = { BridgeCodec.encode(AuthStartReceipt(1, BridgeProtocol.newRequestId())) }
        try {
            WatchAuthBridge({ host }, transport) { now }.request()
            fail("Expected receipt rejection")
        } catch (_: BridgeProtocolException) { }
    }

    @Test fun receiptTimeoutIsBoundedAndDoesNotRetry() = runTest {
        val transport = Transport(listOf(WearPeer("phone", true)))
        transport.reply = { delay(BridgeProtocol.TIMEOUT_MS + 1); byteArrayOf() }
        try {
            WatchAuthBridge({ host }, transport) { now }.request()
            fail("Expected timeout")
        } catch (_: TimeoutCancellationException) { }
        assertEquals(1, transport.sent.size)
    }
}
