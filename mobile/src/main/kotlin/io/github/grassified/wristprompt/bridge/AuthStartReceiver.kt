package io.github.grassified.wristprompt.bridge

class AuthStartReceiver(
    private val inbox: AuthRequestInbox,
    private val eligibleWatches: suspend () -> List<WearPeer>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun receive(nodeId: String, payload: ByteArray): ByteArray {
        val request = BridgeCodec.decodeRequest(payload, now())
        if (eligibleWatches().none { it.id == nodeId }) throw BridgeProtocolException()
        inbox.accept(nodeId, request)
        // Receipt confirms durable metadata storage, not OAuth or permission to transfer credentials.
        return BridgeCodec.encode(AuthStartReceipt(BridgeProtocol.VERSION, request.requestId))
    }
}
