package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.openai.*
import io.github.grassified.wristprompt.storage.*
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApiKeyRepositoryTest {
    @Test fun conversationOwnerSurvivesRecreationButNotKeyReplacement(): Unit = runBlocking {
        val record = Record(); val repo = ApiKeyRepository(record) { now }; store(repo)
        repo.selectMode(CredentialMode.OPENAI_API_KEY)
        val oldLease = repo.credential(); val owner = repo.conversationIdentity(oldLease)
        val restored = ApiKeyRepository(record) { now }; restored.load()
        assertEquals(owner, restored.conversationIdentity(restored.credential()))
        restored.remove(); store(restored)
        assertNotEquals(owner, restored.conversationIdentity(restored.credential()))
        assertThrows(Exception::class.java) { runBlocking { restored.conversationIdentity(oldLease) } }
    }
    @Test fun preD6VaultGetsAnAdditiveStableScopeWithoutChangingCredential(): Unit = runBlocking {
        val record = Record(); val repo = ApiKeyRepository(record) { now }; store(repo)
        repo.selectMode(CredentialMode.OPENAI_API_KEY)
        val old = kotlinx.serialization.json.Json.parseToJsonElement(record.read()!!.decodeToString()).let {
            kotlinx.serialization.json.JsonObject((it as kotlinx.serialization.json.JsonObject).filterKeys { key -> key != "conversationScope" })
        }
        record.write(old.toString().encodeToByteArray())
        val restored = ApiKeyRepository(record) { now }; val lease = restored.credential()
        val owner = restored.conversationIdentity(lease)
        assertEquals(64, owner.length); assertTrue(restored.current(lease))
        assertEquals(owner, ApiKeyRepository(record) { now }.conversationIdentity(lease))
        assertEquals("synthetic-user-key", restored.credential().accessToken)
    }
    @Test fun ownerBindingStorageFailureNeverFallsBackToAnotherScope(): Unit = runBlocking {
        val record = Record(); val repo = ApiKeyRepository(record) { now }; store(repo); repo.selectMode(CredentialMode.OPENAI_API_KEY)
        val old = kotlinx.serialization.json.Json.parseToJsonElement(record.read()!!.decodeToString()) as kotlinx.serialization.json.JsonObject
        record.write(kotlinx.serialization.json.JsonObject(old.filterKeys { it != "conversationScope" }).toString().encodeToByteArray())
        record.fail = true
        assertThrows(SecureStorageException::class.java) { runBlocking { repo.conversationIdentity(repo.credential()) } }
        assertTrue(repo.state.value.storageError)
    }
    private class Record : SecretRecord {
        var encrypted: ByteArray? = null
        var fail = false
        var writes = 0
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val aad = "api_key.v1".encodeToByteArray()
        override fun read() = encrypted?.let { GcmRecord.decrypt(it, key, aad) }
        override fun write(plaintext: ByteArray) { if (fail) throw SecureStorageException(); encrypted = GcmRecord.encrypt(plaintext, key, aad); writes++ }
    }
    private var now = 1_000_000L
    private fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), now)
    private fun transfer(r: AuthStartRequest) = ApiKeyTransfer(1, r.requestId, BridgeProtocol.newRequestId(), r.hostId, "synthetic-user-key")
    private suspend fun store(repo: ApiKeyRepository): Pair<AuthStartRequest, ApiKeyTransfer> {
        val r = request(); val t = transfer(r); repo.prepare("phone", r); repo.accept("phone", ProfileProtocol.encode(t)); return r to t
    }
    @Test fun encryptedRecreationExplicitModeAndRemoval(): Unit = runBlocking {
        val record = Record(); val repo = ApiKeyRepository(record) { now }; store(repo)
        assertFalse(record.encrypted!!.decodeToString().contains("synthetic-user-key"))
        assertEquals(CredentialMode.CHATGPT_PLAN, repo.snapshot().mode)
        assertThrows(ApiFailure::class.java) { runBlocking { repo.credential() } }
        repo.selectMode(CredentialMode.OPENAI_API_KEY); val lease = repo.credential(); repo.selectModel(lease, "api-model")
        val restored = ApiKeyRepository(record) { now }; restored.load()
        assertEquals(CredentialMode.OPENAI_API_KEY, restored.state.value.mode)
        assertEquals("synthetic-user-key", restored.credential().accessToken); assertEquals("api-model", restored.selectedModel())
        restored.remove(); restored.remove()
        assertFalse(restored.current(lease)); assertFalse(restored.snapshot().configured)
        assertEquals(CredentialMode.OPENAI_API_KEY, restored.snapshot().mode)
        val failure = assertThrows(ApiFailure::class.java) { runBlocking { restored.credential() } }
        assertEquals(ApiProblem.API_KEY_MISSING, failure.problem)
    }
    @Test fun acknowledgementRequiresDurableStorageAndLostAckRetryIsIdempotent(): Unit = runBlocking {
        val record = Record(); val repo = ApiKeyRepository(record) { now }; val r = request(); val t = transfer(r)
        repo.prepare("phone", r); record.fail = true
        assertThrows(SecureStorageException::class.java) { runBlocking { repo.accept("phone", ProfileProtocol.encode(t)) } }
        assertFalse(repo.state.value.configured)
        record.fail = false
        val ack = repo.accept("phone", ProfileProtocol.encode(t)); val writes = record.writes
        val recreated = ApiKeyRepository(record) { now }
        assertEquals(ack, recreated.accept("phone", ProfileProtocol.encode(t))); assertEquals(writes, record.writes)
        assertTrue(recreated.state.value.configured)
    }
    @Test fun contextRejectsUnsolicitedWrongNodeHostNonceAndStaleReplay(): Unit = runBlocking {
        val repo = ApiKeyRepository(Record()) { now }; val r = request(); val t = transfer(r)
        fun reject(node: String, transfer: ApiKeyTransfer) = assertThrows(Exception::class.java) { runBlocking { repo.accept(node, ProfileProtocol.encode(transfer)) } }
        reject("phone", t); repo.prepare("phone", r)
        reject("other", t); reject("phone", t.copy(hostId = BridgeProtocol.newHostId())); reject("phone", t.copy(requestId = BridgeProtocol.newRequestId()))
        repo.accept("phone", ProfileProtocol.encode(t))
        reject("phone", t.copy(key = "different-synthetic-key")); reject("phone", t.copy(transferId = BridgeProtocol.newRequestId()))
        now += BridgeProtocol.REQUEST_LIFETIME_MS + 1; reject("phone", t)
    }
    @Test fun deleteInvalidatesPendingTransfersAndOldLeases(): Unit = runBlocking {
        val repo = ApiKeyRepository(Record()) { now }; val (_, t) = store(repo)
        repo.selectMode(CredentialMode.OPENAI_API_KEY); val lease = repo.credential(); repo.remove()
        assertFalse(repo.current(lease))
        assertThrows(Exception::class.java) { runBlocking { repo.accept("phone", ProfileProtocol.encode(t)) } }
    }
    @Test fun corruptStorageAndFailedDeletionFailClosed(): Unit = runBlocking {
        val record = Record(); val repo = ApiKeyRepository(record) { now }; store(repo); repo.selectMode(CredentialMode.OPENAI_API_KEY)
        val lease = repo.credential(); record.fail = true
        assertThrows(SecureStorageException::class.java) { runBlocking { repo.remove() } }
        assertFalse(repo.current(lease)); assertTrue(repo.state.value.storageError)
        record.encrypted!![4] = (record.encrypted!![4].toInt() xor 1).toByte()
        val restored = ApiKeyRepository(record); restored.load(); assertTrue(restored.state.value.storageError)
        assertThrows(SecureStorageException::class.java) { runBlocking { restored.credential() } }
    }
}
