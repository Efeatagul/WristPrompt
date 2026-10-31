package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.openai.*
import io.github.grassified.wristprompt.storage.SecretRecord
import io.github.grassified.wristprompt.storage.SecureStorageException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable private data class KeyContext(val node: String, val request: AuthStartRequest) { override fun toString() = SafeDiagnostics.REDACTED }
@Serializable private data class KeyReceipt(val node: String, val request: String, val transfer: String, val digest: List<Byte>, val expires: Long) { override fun toString() = SafeDiagnostics.REDACTED }
@Serializable private data class ApiVault(
    val version: Int = 1, val mode: CredentialMode = CredentialMode.CHATGPT_PLAN, val modeRevision: Long = 0,
    val key: String? = null, val keyRevision: Long = 0, val model: String? = null,
    val conversationScope: String? = null,
    val pending: KeyContext? = null, val accepted: KeyReceipt? = null,
) { override fun toString() = SafeDiagnostics.REDACTED }

data class ApiKeyState(val loaded: Boolean = false, val mode: CredentialMode = CredentialMode.CHATGPT_PLAN,
    val modeRevision: Long = 0, val keyRevision: Long = 0, val configured: Boolean = false, val storageError: Boolean = false)

class ApiKeyRepository(private val record: SecretRecord, private val now: SessionClock = SessionClock.SYSTEM) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ApiKeyState())
    val state = mutableState.asStateFlow()
    private var deleting = false
    private fun read(): ApiVault = try {
        val bytes = record.read()
        if (bytes == null) ApiVault() else try {
            ProfileProtocol.json.decodeFromString<ApiVault>(bytes.decodeToString(throwOnInvalidSequence = true)).also {
                require(it.version == 1 && it.modeRevision >= 0 && it.keyRevision >= 0)
                require(it.key == null || ApiKeyProtocol.sane(it.key))
                require(it.model == null || validModel(it.model))
                require(it.conversationScope == null || java.util.UUID.fromString(it.conversationScope).toString() == it.conversationScope)
            }
        } finally { bytes.fill(0) }
    } catch (_: Exception) { mutableState.value = mutableState.value.copy(storageError = true); throw SecureStorageException() }
    private fun publish(v: ApiVault) { mutableState.value = ApiKeyState(true, v.mode, v.modeRevision, v.keyRevision, v.key != null && !deleting) }
    private fun save(v: ApiVault) {
        val bytes = ProfileProtocol.json.encodeToString(ApiVault.serializer(), v).encodeToByteArray()
        try { record.write(bytes); publish(v) }
        catch (_: Exception) { mutableState.value = mutableState.value.copy(storageError = true); throw SecureStorageException() }
        finally { bytes.fill(0) }
    }
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { try { publish(read()) } catch (_: SecureStorageException) { } } }
    suspend fun snapshot(): ApiKeyState = withContext(Dispatchers.IO) { mutex.withLock {
        if (state.value.storageError) throw SecureStorageException()
        val v = read(); publish(v); state.value
    } }
    suspend fun selectMode(mode: CredentialMode) = withContext(Dispatchers.IO) { mutex.withLock {
        val v = read(); save(v.copy(mode = mode, modeRevision = Math.addExact(v.modeRevision, 1), pending = null, accepted = null))
    } }
    suspend fun prepare(node: String, request: AuthStartRequest) = withContext(Dispatchers.IO) { mutex.withLock {
        BridgeCodec.decodeRequest(BridgeCodec.encode(request), now())
        val v = read(); save(v.copy(pending = KeyContext(node, request), accepted = null))
    } }
    suspend fun accept(node: String, bytes: ByteArray): ProfileAck = withContext(Dispatchers.IO) {
        val t = ApiKeyProtocol.transfer(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toList()
        mutex.withLock {
            val v = read(); val old = v.accepted
            if (old != null && old.node == node && old.request == t.requestId && old.transfer == t.transferId && old.digest == digest && old.expires >= now()) {
                publish(v)
                return@withLock ProfileAck(1, t.requestId, t.transferId)
            }
            val p = v.pending ?: throw BridgeProtocolException()
            require(p.node == node && p.request.requestId == t.requestId && p.request.hostId == t.hostId && p.request.isFresh(now()))
            deleting = false
            save(v.copy(key = t.key, keyRevision = Math.addExact(v.keyRevision, 1), model = null, conversationScope = java.util.UUID.randomUUID().toString(), pending = null,
                accepted = KeyReceipt(node, t.requestId, t.transferId, digest, p.request.createdAtEpochMs + BridgeProtocol.REQUEST_LIFETIME_MS)))
            ProfileAck(1, t.requestId, t.transferId)
        }
    }
    suspend fun remove() = withContext(Dispatchers.IO) { mutex.withLock {
        deleting = true
        val v = read(); save(v.copy(key = null, keyRevision = Math.addExact(v.keyRevision, 1), model = null, conversationScope = null, pending = null, accepted = null))
        deleting = false
    } }
    suspend fun credential(): CredentialLease = withContext(Dispatchers.IO) { mutex.withLock {
        if (state.value.storageError) throw SecureStorageException()
        val v = read()
        if (v.mode != CredentialMode.OPENAI_API_KEY) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        if (deleting || v.key == null) throw ApiFailure(ApiProblem.API_KEY_MISSING)
        CredentialLease(API_CLIENT, v.keyRevision, v.key, Long.MAX_VALUE, CredentialMode.OPENAI_API_KEY, v.modeRevision)
    } }
    suspend fun modeCurrent(mode: CredentialMode, revision: Long): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        if (state.value.storageError) return@withLock false
        val v = read(); v.mode == mode && v.modeRevision == revision
    } }
    suspend fun current(lease: CredentialLease): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val v = read(); !deleting && !state.value.storageError && v.mode == lease.mode && v.modeRevision == lease.modeRevision &&
            v.keyRevision == lease.generation && v.key == lease.accessToken
    } }
    suspend fun selectedModel(): String? = withContext(Dispatchers.IO) { mutex.withLock { read().model } }
    suspend fun conversationIdentity(lease: CredentialLease): String = withContext(Dispatchers.IO) { mutex.withLock {
        val v = read()
        require(!deleting && v.key != null && v.key == lease.accessToken && v.keyRevision == lease.generation &&
            v.mode == lease.mode && v.modeRevision == lease.modeRevision)
        val identity = v.conversationScope ?: java.util.UUID.randomUUID().toString().also { save(v.copy(conversationScope = it)) }
        MessageDigest.getInstance("SHA-256").digest(identity.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    } }
    suspend fun selectModel(lease: CredentialLease, slug: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val v = read(); require(validModel(slug))
        require(v.mode == CredentialMode.OPENAI_API_KEY && v.modeRevision == lease.modeRevision && v.keyRevision == lease.generation)
        save(v.copy(model = slug))
    } }
    private fun validModel(s: String) = s.isNotBlank() && s.length <= 256 && s.none(Char::isISOControl)
    companion object { const val API_CLIENT = "openai_api_key" }
}
