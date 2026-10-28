package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ApiKeyRequest(val node: String, val request: AuthStartRequest) { override fun toString() = SafeDiagnostics.REDACTED }
/** Phone setup context is process-memory only; never stores a key. */
class ApiKeyInbox(private val peers: suspend () -> List<WearPeer>, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<ApiKeyRequest?>(null)
    val state = mutable.asStateFlow()
    suspend fun receive(node: String, bytes: ByteArray): ByteArray {
        val request = ApiKeyProtocol.start(bytes, now())
        require(peers().any { it.id == node })
        mutex.withLock {
            val old = mutable.value
            if (old != null && old.request.isFresh(now())) require(old.node == node && old.request.hostId == request.hostId)
            mutable.value = ApiKeyRequest(node, request)
        }
        return BridgeCodec.encode(AuthStartReceipt(1, request.requestId))
    }
}
