package io.github.grassified.wristprompt.presets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

// Separate app-private DataStore; no credential, bridge, replay or network dependency.
class PresetRepository(private val store: DataStore<Preferences>, private val onCommitted: () -> Unit = {}) {
    private val key = stringPreferencesKey("catalog_v1")
    suspend fun load(): PresetCatalog = safe { decode(store.data.first()[key]) }

    suspend fun save(id: String?, expectedRevision: Long?, name: String, text: String): PresetCatalog =
        change(id, expectedRevision, name, text, false)

    suspend fun delete(id: String, expectedRevision: Long): PresetCatalog = change(id, expectedRevision, "", "", true)

    private suspend fun change(id: String?, revision: Long?, name: String, text: String, delete: Boolean): PresetCatalog = safe {
        var committed = PresetCatalog()
        store.edit { preferences ->
            committed = PresetCodec.change(decode(preferences[key]), id, revision, name, text, delete)
            preferences[key] = PresetCodec.encode(committed)
        }
        onCommitted()
        committed
    }
    suspend fun source(): String = safe {
        val sourceKey = stringPreferencesKey("source_v1")
        var source = ""
        store.edit { preferences ->
            source = preferences[sourceKey] ?: PresetCodec.newId()
            if (!PresetWire.validId(source)) PresetWire.invalid()
            preferences[sourceKey] = source
        }
        source
    }
    private fun decode(value: String?) = value?.let(PresetCodec::decode) ?: PresetCatalog()
    private suspend fun <T> safe(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (problem: PresetException) { throw problem }
        catch (_: Exception) { throw PresetException(PresetProblem.STORAGE) }
    }
}
