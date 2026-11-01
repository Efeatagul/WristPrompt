package io.github.grassified.wristprompt.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.openai.InferenceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class PresetChoice { INSERT, REPLACE, CANCEL }
class PresetSelection(val preset: PromptPreset, val draft: String, val token: Long) {
    override fun toString() = "PresetSelection([redacted])"
}
class WatchPresetsState(val catalog: PresetCatalog = PresetCatalog(), val selection: PresetSelection? = null, val error: String? = null) {
    override fun toString() = "WatchPresetsState([redacted])"
}
class WatchPresetsViewModel(private val store: SyncedPresetStore, private val refreshSync: suspend () -> Boolean) : ViewModel() {
    private val mutable = MutableStateFlow(WatchPresetsState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            store.catalogs.catch { mutable.value = WatchPresetsState(error = "Saved prompts unavailable. Existing data was preserved.") }
                .collect { mutable.value = WatchPresetsState(it, mutable.value.selection, mutable.value.error) }
        }
    }
    fun refresh() {
        viewModelScope.launch {
            val ok = refreshSync()
            mutable.value = WatchPresetsState(mutable.value.catalog, mutable.value.selection,
                if (ok) null else "Sync unavailable. Previously saved prompts remain available.")
        }
    }
    fun select(preset: PromptPreset, model: InferenceViewModel) {
        val token = model.inputToken() ?: return
        mutable.value = WatchPresetsState(mutable.value.catalog, PresetSelection(preset, model.chat.value.draft, token))
    }
    fun cancel() { mutable.value = WatchPresetsState(mutable.value.catalog) }
    fun apply(choice: PresetChoice, model: InferenceViewModel, applied: () -> Unit = {}) {
        if (choice == PresetChoice.CANCEL) { cancel(); return }
        val selected = mutable.value.selection ?: return
        viewModelScope.launch {
            try {
                val latest = store.load().records.find { it.id == selected.preset.id }
                if (mutable.value.selection !== selected) return@launch
                if (latest == null || latest.deleted || latest != selected.preset) {
                    mutable.value = WatchPresetsState(mutable.value.catalog, error = "Prompt changed or was deleted. Select it again.")
                } else if (model.applyPreset(latest.text, choice == PresetChoice.REPLACE, selected.draft, selected.token)) {
                    cancel(); applied()
                } else {
                    mutable.value = WatchPresetsState(mutable.value.catalog, selected,
                        "Draft changed, is busy, or would exceed 8,192 characters. Draft kept unchanged.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = WatchPresetsState(mutable.value.catalog, selected, "Saved prompts unavailable. Draft kept unchanged.") }
        }
    }
}
