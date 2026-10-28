package io.github.grassified.wristprompt.bridge

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthStartReceiverTest {
    @get:Rule val temporary = TemporaryFolder()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var now = 1_000_000L
    private val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), now)
    private fun store() = PreferenceDataStoreFactory.create(scope = scope) {
        File(temporary.root, "receipt.preferences_pb")
    }
    private fun receiver(inbox: AuthRequestInbox, peers: List<WearPeer> = listOf(WearPeer("watch", true))) =
        AuthStartReceiver(inbox, { peers }) { now }

    @After fun close() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    @Test fun receiptIsDurableBeforeAcknowledgmentAndSurvivesRestart() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val receipt = receiver(inbox).receive("watch", BridgeCodec.encode(request))
        assertEquals(request.requestId, BridgeCodec.decodeReceipt(receipt, request.requestId).requestId)
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restored = AuthRequestInbox(store()) { now }.received.first()
        assertEquals("watch", restored?.nodeId)
        assertEquals(request, restored?.request)
        assertEquals("[REDACTED]", restored.toString())
    }

    @Test fun malformedInputIsRejectedBeforeNodeDiscovery() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val endpoint = AuthStartReceiver(inbox, { error("Must not query nodes") }) { now }
        assertThrows(BridgeProtocolException::class.java) {
            runBlocking { endpoint.receive("watch", "not-json".encodeToByteArray()) }
        }
        assertNull(inbox.received.first())
    }

    @Test fun unrecognizedSenderCannotCreateRequest() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        assertThrows(BridgeProtocolException::class.java) {
            runBlocking { receiver(inbox).receive("different-watch", BridgeCodec.encode(request)) }
        }
        assertNull(inbox.received.first())
    }

    @Test fun duplicateRequestIsIdempotent() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val endpoint = receiver(inbox)
        val first = endpoint.receive("watch", BridgeCodec.encode(request))
        val second = endpoint.receive("watch", BridgeCodec.encode(request))
        assertArrayEquals(first, second)
        assertEquals(request, inbox.received.first()?.request)
    }

    @Test fun sameRequestIdWithDifferentHostCannotReplacePendingRequest() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val endpoint = receiver(inbox)
        endpoint.receive("watch", BridgeCodec.encode(request))
        assertThrows(BridgeProtocolException::class.java) {
            runBlocking { endpoint.receive("watch", BridgeCodec.encode(request.copy(hostId = BridgeProtocol.newHostId()))) }
        }
        assertEquals(request, inbox.received.first()?.request)
    }

    @Test fun anotherEligibleWatchCannotClobberAnActiveRequest() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val endpoint = receiver(inbox, listOf(WearPeer("watch", true), WearPeer("other-watch", true)))
        endpoint.receive("watch", BridgeCodec.encode(request))
        assertThrows(BridgeProtocolException::class.java) {
            runBlocking { endpoint.receive("other-watch", BridgeCodec.encode(request.copy(requestId = BridgeProtocol.newRequestId()))) }
        }
        assertEquals("watch", inbox.received.first()?.nodeId)
    }

    @Test fun explicitRetryFromSameInstallationReplacesItsPriorRequest() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val endpoint = receiver(inbox)
        endpoint.receive("watch", BridgeCodec.encode(request))
        val retry = request.copy(requestId = BridgeProtocol.newRequestId())
        endpoint.receive("watch", BridgeCodec.encode(retry))
        assertEquals(retry, inbox.received.first()?.request)
    }

    @Test fun expiredPendingRequestDoesNotBlockANewWatch() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        val endpoint = receiver(inbox, listOf(WearPeer("watch", true), WearPeer("other-watch", true)))
        endpoint.receive("watch", BridgeCodec.encode(request))
        now += BridgeProtocol.REQUEST_LIFETIME_MS + 1
        val fresh = request.copy(createdAtEpochMs = now, requestId = BridgeProtocol.newRequestId(), hostId = BridgeProtocol.newHostId())
        endpoint.receive("other-watch", BridgeCodec.encode(fresh))
        assertEquals(fresh, inbox.received.first()?.request)
    }

    @Test fun staleRequestCannotBeAcknowledged() = runBlocking {
        val inbox = AuthRequestInbox(store()) { now }
        now += BridgeProtocol.REQUEST_LIFETIME_MS + 1
        assertThrows(BridgeProtocolException::class.java) {
            runBlocking { receiver(inbox).receive("watch", BridgeCodec.encode(request)) }
        }
        assertNull(inbox.received.first())
    }

    @Test fun storageFailureCannotBeAcknowledged() = runBlocking {
        val failingStore = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("Synthetic storage failure")
        }
        val inbox = AuthRequestInbox(failingStore) { now }
        assertThrows(IOException::class.java) {
            runBlocking { receiver(inbox).receive("watch", BridgeCodec.encode(request)) }
        }
        Unit
    }
}
