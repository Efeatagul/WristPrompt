package io.github.grassified.wristprompt.bridge

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

data class ReceivedAuthRequest(val nodeId: String, val request: AuthStartRequest) {
    override fun toString(): String = SafeDiagnostics.REDACTED
}

/** Non-secret setup metadata only. Credential profiles must never be stored here. */
class AuthRequestInbox(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val received = store.data.map { read(it) }

    suspend fun accept(nodeId: String, request: AuthStartRequest) {
        if (nodeId.isBlank() || nodeId.length > 128 || !request.isFresh(now())) throw BridgeProtocolException()
        val encoded = BridgeCodec.encode(request).decodeToString()
        store.edit { values ->
            if (!request.isFresh(now())) throw BridgeProtocolException()
            val existing = read(values)
            if (existing != null) {
                val sameOrigin = existing.nodeId == nodeId && existing.request.hostId == request.hostId
                if (!sameOrigin || (existing.request.requestId == request.requestId && existing.request != request)) {
                    throw BridgeProtocolException()
                }
            }
            values[SOURCE_NODE] = nodeId
            values[REQUEST] = encoded
        }
    }

    private fun read(values: Preferences): ReceivedAuthRequest? {
        val node = values[SOURCE_NODE] ?: return null
        val payload = values[REQUEST] ?: return null
        return try {
            if (node.isBlank() || node.length > 128) throw BridgeProtocolException()
            ReceivedAuthRequest(node, BridgeCodec.decodeRequest(payload.encodeToByteArray(), now()))
        } catch (_: BridgeProtocolException) {
            null
        }
    }

    private companion object {
        val SOURCE_NODE = stringPreferencesKey("request_source_node")
        val REQUEST = stringPreferencesKey("auth_start_request")
    }
}
