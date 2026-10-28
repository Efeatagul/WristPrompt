package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.withTimeout

class ApiKeySetupBridge(
    private val host: suspend () -> String, private val peers: suspend () -> List<WearPeer>,
    private val send: suspend (String, String, ByteArray) -> ByteArray,
    private val prepare: suspend (String, AuthStartRequest) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun request(): Boolean = withTimeout(BridgeProtocol.TIMEOUT_MS) {
        val nodes = peers(); val nearby = nodes.filter { it.isNearby }
        val phone = when { nodes.isEmpty() -> return@withTimeout false; nearby.size == 1 -> nearby.single(); nodes.size == 1 -> nodes.single(); else -> throw AmbiguousCompanionException() }
        val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), host(), now())
        prepare(phone.id, request)
        BridgeCodec.decodeReceipt(send(phone.id, ApiKeyProtocol.START_PATH, BridgeCodec.encode(request)), request.requestId)
        true
    }
}
