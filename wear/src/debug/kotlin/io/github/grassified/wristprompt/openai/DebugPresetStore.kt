package io.github.grassified.wristprompt.openai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.grassified.wristprompt.presets.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Synthetic UI-only store. No Context, real persistence, transport or credentials. */
internal object DebugPresetStore {
    fun create(): SyncedPresetStore {
        val catalog = PresetCatalog(records = listOf(
            PromptPreset("00000000-0000-4000-8000-000000000001", 1, "School summary", "Summarize these synthetic notes:\n"),
            PromptPreset("00000000-0000-4000-8000-000000000002", 1, "Coding assistant with a deliberately long preset name for round screen checks", "Explain this synthetic example step by step.\nKeep the answer concise."),
            PromptPreset("00000000-0000-4000-8000-000000000003", 1, "Maximum length", "S".repeat(8192)),
        ))
        val memory = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(preferencesOf(stringPreferencesKey("catalog_v1") to PresetCodec.encode(catalog)))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read-only synthetic fixture")
        }
        return SyncedPresetStore(memory)
    }
}
