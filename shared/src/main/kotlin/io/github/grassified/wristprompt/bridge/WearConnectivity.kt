package io.github.grassified.wristprompt.bridge

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

data class WearPeer(val id: String, val isNearby: Boolean)

interface AuthBridgeTransport {
    suspend fun reachablePeers(): List<WearPeer>
    suspend fun requestStart(nodeId: String, payload: ByteArray): ByteArray
}

/** Matching package/signature is enforced by Data Layer. Discovery alone is not credential consent. */
class WearConnectivity(context: Context, private val peerCapability: String) : AuthBridgeTransport {
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)

    override suspend fun reachablePeers(): List<WearPeer> =
        capabilityClient.getCapability(peerCapability, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.map { WearPeer(id = it.id, isNearby = it.isNearby) }

    override suspend fun requestStart(nodeId: String, payload: ByteArray): ByteArray =
        request(nodeId, BridgeProtocol.AUTH_START_PATH, payload)

    suspend fun request(nodeId: String, path: String, payload: ByteArray): ByteArray =
        messageClient.sendRequest(nodeId, path, payload).await()
}
