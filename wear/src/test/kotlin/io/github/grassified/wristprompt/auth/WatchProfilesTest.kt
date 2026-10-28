package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WatchProfilesTest {
    private class Record : SecretRecord {
        var data: ByteArray? = null
        var writes = 0
        var failWrite = false
        var failAfterWrite = false
        override fun read() = data?.clone()
        override fun write(plaintext: ByteArray) {
            if (failWrite) error("Synthetic secure storage failure")
            data = plaintext.clone(); writes++
            if (failAfterWrite) error("Synthetic uncertainty after durable write")
        }
    }
    private var now = 1_000_000L
    private val host = BridgeProtocol.newHostId()
    private fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, now)
    private fun transfer(request: AuthStartRequest, subject: String = "subject", client: String = "oaiapp_test") = ProfileTransfer(
        1, request.requestId, BridgeProtocol.newRequestId(), CredentialProfile(
            RegistrationIdentity(client, OpenAiAuth.ISSUER, subject), host, "Test", null, false,
            "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", 3600, now,
            OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)))
    @Test fun securePersistencePrecedesAckAndSurvivesRepositoryRecreation(): Unit = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req)
        val envelope = transfer(req)
        val ack = vault.accept("phone", ProfileProtocol.encode(envelope))
        assertEquals(ProfileAck(1, req.requestId, envelope.transferId), ack)
        val restored = WatchProfiles(record) { now }; restored.load()
        assertTrue(restored.state.value.connected); assertTrue(restored.state.value.planEnabled)
    }
    @Test fun firstPlanNoticeIsPersistedPerRegistration() = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req); vault.accept("phone", ProfileProtocol.encode(transfer(req)))
        assertTrue(vault.state.value.planWelcome)
        vault.confirmPlanNotice()
        val restored = WatchProfiles(record) { now }; restored.load()
        assertFalse(restored.state.value.planWelcome)
    }
    @Test fun storageFailureReturnsNoAckAndDoesNotPublishConnected(): Unit = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req); record.failWrite = true
        assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", ProfileProtocol.encode(transfer(req))) } }
        assertFalse(vault.state.value.connected)
        assertTrue(vault.state.value.storageError)
    }
    @Test fun retryRecoversUiAfterUncertainButCompletedStorageWrite() = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req); val bytes = ProfileProtocol.encode(transfer(req))
        record.failAfterWrite = true
        assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", bytes) } }
        assertTrue(vault.state.value.storageError)
        record.failAfterWrite = false
        vault.accept("phone", bytes)
        assertTrue(vault.state.value.connected); assertFalse(vault.state.value.storageError)
    }
    @Test fun lostAckRetryIsIdempotentWithoutRewritingCredentials(): Unit = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req); val bytes = ProfileProtocol.encode(transfer(req))
        val ack = vault.accept("phone", bytes); val writes = record.writes
        assertEquals(ack, vault.accept("phone", bytes)); assertEquals(writes, record.writes)
        val restored = WatchProfiles(record) { now }
        assertEquals(ack, restored.accept("phone", bytes)); assertEquals(writes, record.writes)
    }
    @Test fun unsolicitedWrongNodeWrongHostAndExpiredTransfersFail(): Unit = runBlocking {
        val vault = WatchProfiles(Record()) { now }; val req = request(); val value = transfer(req)
        fun rejected(node: String, envelope: ProfileTransfer) {
            assertThrows(Exception::class.java) { runBlocking { vault.accept(node, ProfileProtocol.encode(envelope)) } }
        }
        rejected("phone", value)
        vault.prepare("phone", req)
        rejected("other-phone", value)
        rejected("phone", value.copy(profile = value.profile.copy(hostId = BridgeProtocol.newHostId())))
        now += OpenAiAuth.ATTEMPT_MS + 1; rejected("phone", value)
    }
    @Test fun retryCannotChangeAcceptedPayloadOrTransferId(): Unit = runBlocking {
        val vault = WatchProfiles(Record()) { now }; val req = request(); vault.prepare("phone", req)
        val value = transfer(req); vault.accept("phone", ProfileProtocol.encode(value))
        for (changed in listOf(value.copy(transferId = BridgeProtocol.newRequestId()), value.copy(profile = value.profile.copy(accessToken = "different")))) {
            assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", ProfileProtocol.encode(changed)) } }
        }
    }
    @Test fun accountSelectionInvalidatesPendingAndReturningIdentityMustMatch(): Unit = runBlocking {
        val vault = WatchProfiles(Record()) { now }; val req = request(); vault.prepare("phone", req)
        vault.accept("phone", ProfileProtocol.encode(transfer(req)))
        val returning = request(); vault.prepare("phone", returning)
        assertEquals("oaiapp_test", vault.context("phone", ContextRequest(1, returning.requestId, host)).identity?.clientId)
        assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", ProfileProtocol.encode(transfer(returning, subject = "different"))) } }
        vault.select(null)
        assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", ProfileProtocol.encode(transfer(returning))) } }
    }
    @Test fun newRequestInvalidatesLateResultAndIdentityOnlyDoesNotEnablePlan(): Unit = runBlocking {
        val vault = WatchProfiles(Record()) { now }; val first = request(); vault.prepare("phone", first)
        val second = request(); vault.prepare("phone", second)
        assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", ProfileProtocol.encode(transfer(first))) } }
        val value = transfer(second).let { it.copy(profile = it.profile.copy(scopes = setOf("openid"))) }
        vault.accept("phone", ProfileProtocol.encode(value))
        assertTrue(vault.state.value.connected); assertFalse(vault.state.value.planEnabled)
    }
}
