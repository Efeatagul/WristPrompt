package io.github.grassified.wristprompt.auth

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneApiKeyTest {
    private val time = 1_000_000L
    private fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), time)
    @Test fun phoneRequiresEligibleFreshWatchContext() = runTest {
        val inbox = ApiKeyInbox({ listOf(WearPeer("watch", true)) }) { time }
        val r = request()
        assertThrows(Exception::class.java) { runBlocking { inbox.receive("other", BridgeCodec.encode(r)) } }
        val receipt = inbox.receive("watch", BridgeCodec.encode(r)); BridgeCodec.decodeReceipt(receipt, r.requestId)
        assertThrows(Exception::class.java) { runBlocking { inbox.receive("watch", BridgeCodec.encode(request())) } }
        assertThrows(Exception::class.java) { runBlocking { inbox.receive("watch", BridgeCodec.encode(r.copy(createdAtEpochMs = 0))) } }
    }
    @Test fun matchingStorageAckClearsKeyAndTransferBytes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val inbox = ApiKeyInbox({ listOf(WearPeer("watch", true)) }) { time }; val r = request(); inbox.receive("watch", BridgeCodec.encode(r))
        var retained: ByteArray? = null
        val vm = PhoneApiKeyViewModel(inbox, { node, path, bytes ->
            assertEquals("watch", node); assertEquals(ApiKeyProtocol.TRANSFER_PATH, path)
            val t = ApiKeyProtocol.transfer(bytes); assertEquals("synthetic-user-key", t.key); retained = bytes
            ProfileProtocol.encode(ProfileAck(1, t.requestId, t.transferId))
        }) { time }
        try {
            runCurrent(); vm.edit("synthetic-user-key"); vm.transfer(); runCurrent()
            assertEquals(PhoneKeyStage.ACKNOWLEDGED, vm.stage.value); assertEquals("", vm.key.value)
            assertTrue(retained!!.all { it == 0.toByte() })
            vm.transfer(); assertEquals(PhoneKeyStage.ACKNOWLEDGED, vm.stage.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun missingOrWrongAckRetainsOnlySameBoundedRetryUntilExpiry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now = time
        val inbox = ApiKeyInbox({ listOf(WearPeer("watch", true)) }) { now }; val r = request(); inbox.receive("watch", BridgeCodec.encode(r))
        val sent = mutableListOf<ByteArray>()
        val vm = PhoneApiKeyViewModel(inbox, { _, _, bytes -> sent += bytes.clone(); "wrong-ack".encodeToByteArray() }) { now }
        try {
            runCurrent(); vm.edit("synthetic-user-key"); vm.transfer(); runCurrent(); assertEquals(PhoneKeyStage.RETRY, vm.stage.value); assertEquals("", vm.key.value)
            vm.transfer(); runCurrent(); assertArrayEquals(sent[0], sent[1]); assertEquals(PhoneKeyStage.RETRY, vm.stage.value)
            now += BridgeProtocol.REQUEST_LIFETIME_MS + 1; advanceTimeBy(BridgeProtocol.REQUEST_LIFETIME_MS + 1); runCurrent()
            assertEquals(PhoneKeyStage.EXPIRED, vm.stage.value); assertEquals("", vm.key.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun timeoutCanRetryAndFormatDoesNotProveValidity() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val inbox = ApiKeyInbox({ listOf(WearPeer("watch", true)) }) { time }; inbox.receive("watch", BridgeCodec.encode(request()))
        val vm = PhoneApiKeyViewModel(inbox, { _, _, _ -> awaitCancellation() }) { time }
        try {
            runCurrent(); vm.edit("short"); vm.transfer(); assertEquals(PhoneKeyStage.FORMAT_ERROR, vm.stage.value)
            vm.edit("clearly-fake-placeholder"); vm.transfer(); runCurrent(); advanceTimeBy(BridgeProtocol.TIMEOUT_MS + 1); runCurrent()
            assertEquals(PhoneKeyStage.RETRY, vm.stage.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun supersededTransferCannotPublishLateAckOrFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (fail in listOf(false, true)) {
                val inbox = ApiKeyInbox({ listOf(WearPeer("watch", true)) }) { time }
                val r = request(); inbox.receive("watch", BridgeCodec.encode(r))
                val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
                val vm = PhoneApiKeyViewModel(inbox, { _, _, bytes ->
                    val t = ApiKeyProtocol.transfer(bytes); entered.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    if (fail) throw java.io.IOException("Synthetic late disconnect")
                    ProfileProtocol.encode(ProfileAck(1, t.requestId, t.transferId))
                }) { time }
                try {
                    runCurrent(); vm.edit("synthetic-user-key"); vm.transfer(); runCurrent(); entered.await()
                    inbox.receive("watch", BridgeCodec.encode(r.copy(requestId = BridgeProtocol.newRequestId())))
                    runCurrent(); release.complete(Unit); runCurrent()
                    assertEquals(PhoneKeyStage.READY, vm.stage.value); assertEquals("", vm.key.value)
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

}
