package io.github.grassified.wristprompt.presets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Phone identity and catalog commit together. No credential/replay dependencies. */
class SyncedPresetStore(private val store: DataStore<Preferences>) {
    private val nodeKey = stringPreferencesKey("phone_node")
    private val sourceKey = stringPreferencesKey("phone_source")
    private val catalogKey = stringPreferencesKey("catalog_v1")
    val catalogs = store.data.map { decode(it) }.flowOn(Dispatchers.IO)
    suspend fun phone(): String? = safe { store.data.first()[nodeKey] }
    suspend fun load() = safe { decode(store.data.first()) }
    private fun decode(preferences: Preferences): PresetCatalog =
        preferences[catalogKey]?.let(PresetCodec::decode) ?: PresetCatalog()

    suspend fun accept(node: String, eligiblePhones: Set<String>, deliveries: List<PresetDelivery>): Unit = safe {
        if (node.isBlank() || node.length > 256 || node !in eligiblePhones || deliveries.isEmpty() || deliveries.size > PresetCodec.MAX_RECORDS) PresetWire.invalid()
        // Bound before parsing, and validate a complete batch before any mutation.
        if (deliveries.sumOf { it.bytes.size.toLong() } > PresetCodec.MAX_BYTES + 65536L) PresetWire.invalid()
        val packets = deliveries.map { PresetWire.decode(it.path, it.bytes) }
        val source = packets.first().source
        if (packets.any { it.source != source }) PresetWire.invalid()
        val incoming = PresetCatalog(records = packets.flatMap { it.catalog.records })
        PresetCodec.encode(incoming)
        store.edit { preferences ->
            val oldNode = preferences[nodeKey]
            val oldSource = preferences[sourceKey]
            if (oldNode == null) {
                if (oldSource != null || preferences[catalogKey] != null || eligiblePhones != setOf(node)) PresetWire.invalid()
            } else if (oldNode != node || oldSource != source) PresetWire.invalid()
            val merged = PresetCodec.merge(decode(preferences), incoming)
            preferences[nodeKey] = node
            preferences[sourceKey] = source
            preferences[catalogKey] = PresetCodec.encode(merged)
        }
        Unit
    }
    private suspend fun <T> safe(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: PresetException) { throw failure }
        catch (_: Exception) { throw PresetException(PresetProblem.STORAGE) }
    }
}
