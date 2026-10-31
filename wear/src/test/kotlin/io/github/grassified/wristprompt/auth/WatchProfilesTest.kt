package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WatchProfilesTest {
    @Test fun durableConversationIdentitySurvivesRecreationAndRejectsStaleAccount(): Unit = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req); vault.accept("phone", ProfileProtocol.encode(transfer(req)))
        val lease = vault.credential { it }; val owner = vault.conversationIdentity(lease)
        assertEquals(owner, vault.localConversationIdentity(lease.clientId, lease.generation))
        assertEquals(64, owner.length); assertFalse(owner.contains("subject"))
        val restored = WatchProfiles(record) { now }; restored.load()
        assertEquals(owner, restored.conversationIdentity(restored.credential { it }))
        restored.select(null) // Explicitly add a different registration, not reauthorize the selected one.
        val next = request(); restored.prepare("phone", next)
        restored.accept("phone", ProfileProtocol.encode(transfer(next, "other-subject", "oaiapp_other")))
        assertNotEquals(owner, restored.conversationIdentity(restored.credential { it }))
        assertThrows(Exception::class.java) { runBlocking { restored.conversationIdentity(lease) } }
        assertThrows(Exception::class.java) { runBlocking { restored.localConversationIdentity(lease.clientId, lease.generation) } }
    }
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
    @Test fun recreationRestoresRegistrationCredentialsAndModelWithoutPhone() = runBlocking {
        val record = Record(); val original = WatchProfiles(record) { now }; val req = request()
        original.prepare("phone", req); val envelope = transfer(req)
        original.accept("phone", ProfileProtocol.encode(envelope))
        original.selectModel(envelope.profile.identity.clientId, original.state.value.generation, "saved-model")
        val restored = WatchProfiles(record) { now }; restored.load()
        assertEquals(original.state.value, restored.state.value)
        assertEquals("saved-model", restored.selectedModel(envelope.profile.identity.clientId))
        val lease = restored.credential { error("Fresh credentials must not refresh or use a phone") }
        assertEquals(envelope.profile.accessToken, lease.accessToken)
        val returning = request(); restored.prepare("phone", returning)
        val context = restored.context("phone", ContextRequest(1, returning.requestId, host))
        assertEquals(envelope.profile.identity, context.identity)
        assertEquals(envelope.profile.idToken, context.idTokenHint)
    }
    @Test fun identityOnlyRestoresConnectedWithoutPlanPermission() = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req)
        vault.accept("phone", ProfileProtocol.encode(transfer(req).let { it.copy(profile = it.profile.copy(scopes = setOf("openid"))) }))
        val restored = WatchProfiles(record) { now }; restored.load()
        assertTrue(restored.state.value.connected); assertFalse(restored.state.value.planEnabled)
        try { restored.credential { error("No refresh for identity-only") }; fail("Expected disabled plan") }
        catch (failure: io.github.grassified.wristprompt.openai.ApiFailure) {
            assertEquals(io.github.grassified.wristprompt.openai.ApiProblem.PLAN_DISABLED, failure.problem)
        }
    }
    @Test fun sameEmailDoesNotMergeRegistrationsOrModelPreferences() = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }
        suspend fun add(client: String, subject: String) {
            val req = request(); vault.prepare("phone", req)
            val value = transfer(req, subject, client)
            vault.accept("phone", ProfileProtocol.encode(value.copy(profile = value.profile.copy(email = "same@example.test"))))
            vault.selectModel(client, vault.state.value.generation, "model-$client")
        }
        add("client_one", "subject_one"); vault.select(null); add("client_two", "subject_two")
        val restored = WatchProfiles(record) { now }; restored.load()
        assertEquals(2, restored.state.value.accounts.size)
        for ((client, subject) in listOf("client_one" to "subject_one", "client_two" to "subject_two")) {
            restored.select(client)
            assertEquals("model-$client", restored.selectedModel(client))
            val req = request(); restored.prepare("phone", req)
            assertEquals(RegistrationIdentity(client, OpenAiAuth.ISSUER, subject),
                restored.context("phone", ContextRequest(1, req.requestId, host)).identity)
        }
    }
    @Test fun changedHostAndStaleModelWriteCannotAlterSavedRegistration() = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }; val req = request()
        vault.prepare("phone", req); vault.accept("phone", ProfileProtocol.encode(transfer(req)))
        val generation = vault.state.value.generation
        vault.select(null)
        val before = record.data!!.clone()
        assertThrows(Exception::class.java) { runBlocking { vault.selectModel("oaiapp_test", generation, "stale") } }
        assertThrows(Exception::class.java) { runBlocking { vault.prepare("phone", request().copy(hostId = BridgeProtocol.newHostId())) } }
        assertArrayEquals(before, record.data)
    }
    @Test fun missingMalformedAndUnreadableRecordsFailSafely() = runBlocking {
        val missing = WatchProfiles(Record()) { now }; missing.load()
        assertFalse(missing.state.value.connected); assertFalse(missing.state.value.storageError)
        for (bytes in listOf("not-json".encodeToByteArray(), byteArrayOf(0xc3.toByte()),
            "{\"selectedClientId\":\"missing-client\"}".encodeToByteArray(),
            "{\"selectedModels\":{\"unknown\":\"model\"}}".encodeToByteArray())) {
            val record = Record().apply { data = bytes }; val vault = WatchProfiles(record) { now }; vault.load()
            assertTrue(vault.state.value.storageError); assertFalse(vault.state.value.connected)
            assertEquals(0, record.writes)
        }
        val unreadable = WatchProfiles(object : SecretRecord {
            override fun read(): ByteArray? = error("Synthetic key unavailable")
            override fun write(plaintext: ByteArray) = error("Must not reset")
        }) { now }
        unreadable.load(); assertTrue(unreadable.state.value.storageError)
    }

    @Test fun encryptedFileRecreationCanDiscoverAndCompleteInferenceWithoutBridge() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("synthetic-vault").toFile()
        try {
            val file = java.io.File(directory, "vault.enc")
            val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val aad = "synthetic-vault".encodeToByteArray()
            fun record() = object : SecretRecord {
                override fun read(): ByteArray? = if (file.exists())
                    io.github.grassified.wristprompt.storage.GcmRecord.decrypt(file.readBytes(), key, aad) else null
                override fun write(plaintext: ByteArray) {
                    file.writeBytes(io.github.grassified.wristprompt.storage.GcmRecord.encrypt(plaintext, key, aad))
                }
            }
            val original = WatchProfiles(record()) { now }; val req = request()
            original.prepare("phone", req); original.accept("phone", ProfileProtocol.encode(transfer(req)))
            val restored = WatchProfiles(record()) { now }; restored.load()
            var calls = 0
            val provider = object : io.github.grassified.wristprompt.openai.AiProvider {
                override suspend fun models(credential: io.github.grassified.wristprompt.openai.CredentialLease): List<io.github.grassified.wristprompt.openai.AiModel> {
                    calls++; return listOf(io.github.grassified.wristprompt.openai.AiModel("model", "Model"))
                }
                override suspend fun stream(credential: io.github.grassified.wristprompt.openai.CredentialLease, model: String,
                    onEvent: suspend (io.github.grassified.wristprompt.openai.ResponseEvent) -> Unit) {
                    calls++
                    onEvent(io.github.grassified.wristprompt.openai.ResponseEvent.Delta(io.github.grassified.wristprompt.openai.ResponsesProtocol.EXPECTED_TEXT))
                    onEvent(io.github.grassified.wristprompt.openai.ResponseEvent.Completed(io.github.grassified.wristprompt.openai.ResponsesProtocol.EXPECTED_TEXT))
                }
            }
            val session = io.github.grassified.wristprompt.openai.InferenceSession(
                { restored.credential { error("Fresh token must not refresh") } }, provider, restored::isCurrent)
            val result = session.test(session.catalog(), "model", {}, {})
            assertTrue(result.proofPassed); assertEquals(2, calls)
            // The protected-record implementation is recreated too; no in-memory record survives.
            file.writeBytes(byteArrayOf(1, 2, 3))
            val corrupt = WatchProfiles(record()) { now }; corrupt.load()
            assertTrue(corrupt.state.value.storageError); assertFalse(corrupt.state.value.connected)
        } finally { directory.deleteRecursively() }
    }

}
