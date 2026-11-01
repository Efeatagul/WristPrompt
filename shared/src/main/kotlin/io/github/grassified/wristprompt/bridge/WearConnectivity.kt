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

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun putPreset(path: String, bytes: ByteArray) {
        io.github.grassified.wristprompt.presets.PresetWire.decode(path, bytes)
        dataClient.putDataItem(com.google.android.gms.wearable.PutDataRequest.create(path).setData(bytes).setUrgent()).await()
    }

    suspend fun presetItems(node: String): List<io.github.grassified.wristprompt.presets.PresetDelivery> {
        val wire = io.github.grassified.wristprompt.presets.PresetWire
        val uri = android.net.Uri.Builder().scheme("wear").authority(node).path(wire.PREFIX).build()
        val buffer = dataClient.getDataItems(uri, com.google.android.gms.wearable.DataClient.FILTER_PREFIX).await()
        try {
            if (buffer.count > io.github.grassified.wristprompt.presets.PresetCodec.MAX_RECORDS) wire.invalid()
            var total = 0L
            return buffer.map { item ->
                val bytes = item.data ?: wire.invalid()
                total += bytes.size
                if (item.uri.host != node || bytes.size > wire.MAX_PACKET_BYTES || total > io.github.grassified.wristprompt.presets.PresetCodec.MAX_BYTES + 65536L) wire.invalid()
                io.github.grassified.wristprompt.presets.PresetDelivery(item.uri.path ?: wire.invalid(), bytes.clone())
            }
        } finally { buffer.release() }
    }

    override suspend fun reachablePeers(): List<WearPeer> =
        capabilityClient.getCapability(peerCapability, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.map { WearPeer(id = it.id, isNearby = it.isNearby) }

    override suspend fun requestStart(nodeId: String, payload: ByteArray): ByteArray =
        request(nodeId, BridgeProtocol.AUTH_START_PATH, payload)

    suspend fun request(nodeId: String, path: String, payload: ByteArray): ByteArray =
        messageClient.sendRequest(nodeId, path, payload).await()
}
