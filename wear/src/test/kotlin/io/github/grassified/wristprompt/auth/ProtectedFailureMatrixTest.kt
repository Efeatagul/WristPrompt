package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.openai.*
import io.github.grassified.wristprompt.storage.*
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProtectedFailureMatrixTest {
    private class FaultStore : SecretRecord {
        var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var ciphertext: ByteArray? = null
        var failRead = false
        var keyFailure: Exception? = null
        var failWrite = false
        var failReplace = false
        var uncertainCommit = false
        var writes = 0
        var lastInput: ByteArray? = null
        private val aad = "synthetic-matrix".encodeToByteArray()
        override fun read(): ByteArray? {
            if (failRead) throw SecureStorageException()
            keyFailure?.let { throw it }
            return ciphertext?.let { GcmRecord.decrypt(it, key ?: throw SecureStorageException(), aad) }
        }
        override fun write(plaintext: ByteArray) {
            lastInput = plaintext
            if (failWrite) throw SecureStorageException()
            val staged = GcmRecord.encrypt(plaintext, key ?: throw SecureStorageException(), aad)
            if (failReplace) { staged.fill(0); throw SecureStorageException() }
            ciphertext = staged; writes++
            if (uncertainCommit) throw SecureStorageException()
        }
    }
    private val time = 1_000_000L
    private val host = BridgeProtocol.newHostId()
    private fun request() = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, time)
    private fun plan(r: AuthStartRequest) = ProfileTransfer(1, r.requestId, BridgeProtocol.newRequestId(), CredentialProfile(
        RegistrationIdentity("synthetic_client", OpenAiAuth.ISSUER, "subject"), host, "Account", "same@example.test", false,
        "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", 3600, time, OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)))
    private fun api(r: AuthStartRequest) = ApiKeyTransfer(1, r.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")

    @Test fun failedReplacementPreservesExistingPlanAndApiRecordsWithoutAcknowledgement(): Unit = runBlocking {
        for (replace in listOf(false, true)) for (api in listOf(false, true)) {
            val store = FaultStore(); val p = WatchProfiles(store) { time }; val k = ApiKeyRepository(store) { time }
            val r = request()
            if (api) { k.prepare("phone", r); k.accept("phone", ProfileProtocol.encode(api(r))); k.selectMode(CredentialMode.OPENAI_API_KEY) }
            else { p.prepare("phone", r); p.accept("phone", ProfileProtocol.encode(plan(r))) }
            val next = request()
            if (api) k.prepare("phone", next) else p.prepare("phone", next)
            val before = store.ciphertext!!.clone()
            store.failWrite = !replace; store.failReplace = replace
            val failure = assertThrows(SecureStorageException::class.java) { runBlocking {
                if (api) k.accept("phone", ProfileProtocol.encode(api(next).copy(key = "synthetic-new-key")))
                else p.accept("phone", ProfileProtocol.encode(plan(next).let { it.copy(profile = it.profile.copy(accessToken = "synthetic-new-token")) }))
            } }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.all { it.message == "Protected storage unavailable" }); assertArrayEquals(before, store.ciphertext)
            assertTrue(store.lastInput!!.all { it == 0.toByte() })
            assertTrue(if (api) k.state.value.storageError else p.state.value.storageError)
            store.failWrite = false; store.failReplace = false
            if (api) {
                val restored = ApiKeyRepository(store) { time }; restored.load()
                assertEquals(CredentialMode.OPENAI_API_KEY, restored.state.value.mode)
                assertEquals("synthetic-api-key", restored.credential().accessToken)
            } else {
                val restored = WatchProfiles(store) { time }; restored.load()
                restored.failed("phone", AuthFailure(1, next.requestId, host))
                assertEquals("synthetic-access", restored.credential { error("No refresh") }.accessToken)
            }
        }
    }
    @Test fun readFailureMissingKeyAndWrongKeyNeverResetOrRecoverSecrets(): Unit = runBlocking {
        for (fault in 0..4) for (api in listOf(false, true)) {
            val store = FaultStore(); val r = request()
            if (api) {
                val k = ApiKeyRepository(store) { time }; k.prepare("phone", r); k.accept("phone", ProfileProtocol.encode(api(r)))
                k.selectMode(CredentialMode.OPENAI_API_KEY)
            } else {
                val p = WatchProfiles(store) { time }; p.prepare("phone", r); p.accept("phone", ProfileProtocol.encode(plan(r)))
            }
            val before = store.ciphertext!!.clone(); val writes = store.writes
            when (fault) {
                0 -> store.failRead = true
                1 -> store.key = null
                2 -> store.key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
                3 -> store.keyFailure = java.security.InvalidKeyException("Synthetic invalidated key")
                else -> store.keyFailure = java.security.KeyStoreException("Synthetic unavailable Keystore")
            }
            if (api) {
                val k = ApiKeyRepository(store) { time }; k.load()
                assertTrue(k.state.value.storageError); assertFalse(k.state.value.configured)
                assertThrows(SecureStorageException::class.java) { runBlocking { k.credential() } }
            } else {
                val p = WatchProfiles(store) { time }; p.load()
                assertTrue(p.state.value.storageError); assertFalse(p.state.value.connected)
                assertThrows(SecureStorageException::class.java) { runBlocking { p.credential { error("Must not refresh") } } }
            }
            assertEquals(writes, store.writes); assertArrayEquals(before, store.ciphertext)
        }
    }
    @Test fun apiUncertainCommitRetryAcknowledgesWithoutReinstallingAndOldDeliveryCannotOverwriteNewKey(): Unit = runBlocking {
        val store = FaultStore(); val k = ApiKeyRepository(store) { time }; val first = request(); val t = api(first)
        val bytes = ProfileProtocol.encode(t); k.prepare("phone", first); store.uncertainCommit = true
        assertThrows(SecureStorageException::class.java) { runBlocking { k.accept("phone", bytes) } }
        store.uncertainCommit = false; val writes = store.writes
        val restored = ApiKeyRepository(store) { time }
        assertEquals(ProfileAck(1, first.requestId, t.transferId), restored.accept("phone", bytes))
        assertEquals(writes, store.writes)
        val next = request(); restored.prepare("phone", next)
        restored.accept("phone", ProfileProtocol.encode(api(next).copy(key = "synthetic-new-key")))
        val before = store.ciphertext!!.clone()
        assertThrows(Exception::class.java) { runBlocking { restored.accept("phone", bytes) } }
        assertArrayEquals(before, store.ciphertext)
        restored.selectMode(CredentialMode.OPENAI_API_KEY)
        assertEquals("synthetic-new-key", restored.credential().accessToken)
    }
    @Test fun failedSetupAndLatePlanResultCannotDestroyNewerRegistration(): Unit = runBlocking {
        val store = FaultStore(); val p = WatchProfiles(store) { time }; val first = request(); val old = plan(first)
        p.prepare("phone", first); p.accept("phone", ProfileProtocol.encode(old))
        val next = request(); p.prepare("phone", next)
        val newer = plan(next).let { it.copy(profile = it.profile.copy(accessToken = "synthetic-new-access")) }
        p.accept("phone", ProfileProtocol.encode(newer)); val before = store.ciphertext!!.clone()
        assertThrows(Exception::class.java) { runBlocking { p.accept("phone", ProfileProtocol.encode(old)) } }
        assertArrayEquals(before, store.ciphertext)
        val failed = request(); p.prepare("phone", failed)
        p.failed("phone", AuthFailure(1, failed.requestId, host))
        assertEquals("synthetic-new-access", p.credential { error("No refresh") }.accessToken)
    }
}
