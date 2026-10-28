package io.github.grassified.wristprompt.bridge

import kotlinx.coroutines.withTimeout

class AmbiguousCompanionException : IllegalStateException("More than one companion is available")

class WatchAuthBridge(
    private val hostId: suspend () -> String,
    private val transport: AuthBridgeTransport,
    private val onPrepared: suspend (String, AuthStartRequest) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
) {
    // The RPC response is bound by Data Layer to this one node and pending invocation.
    // Phase C must add its own expiring profile/transfer binding; this receipt is not authorization.
    suspend fun request(): Boolean = withTimeout(BridgeProtocol.TIMEOUT_MS) {
        val installationId = hostId()
        val phones = transport.reachablePeers()
        val nearby = phones.filter { it.isNearby }
        val phone = when {
            phones.isEmpty() -> return@withTimeout false
            nearby.size == 1 -> nearby.single()
            phones.size == 1 -> phones.single()
            else -> throw AmbiguousCompanionException()
        }
        val request = AuthStartRequest(
            BridgeProtocol.VERSION, BridgeProtocol.newRequestId(), installationId, now(),
        )
        onPrepared(phone.id, request)
        val receipt = transport.requestStart(phone.id, BridgeCodec.encode(request))
        BridgeCodec.decodeReceipt(receipt, request.requestId)
        true
    }
}
