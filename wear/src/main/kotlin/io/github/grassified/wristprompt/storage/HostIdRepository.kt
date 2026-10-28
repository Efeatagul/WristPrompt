package io.github.grassified.wristprompt.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.grassified.wristprompt.bridge.BridgeProtocol

class HostIdRepository(
    private val store: DataStore<Preferences>,
    private val create: () -> String = BridgeProtocol::newHostId,
) {
    suspend fun getOrCreate(): String {
        val preferences = store.edit { values ->
            val current = values[HOST_ID]
            if (current == null) {
                val generated = create()
                check(BridgeProtocol.isHostId(generated)) { "Invalid installation identity" }
                values[HOST_ID] = generated
            } else {
                // Corruption must not silently change an installation's identity.
                check(BridgeProtocol.isHostId(current)) { "Invalid installation identity" }
            }
        }
        return checkNotNull(preferences[HOST_ID])
    }

    companion object {
        val HOST_ID = stringPreferencesKey("ext_agent_host_id")
    }
}
