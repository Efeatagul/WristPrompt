package io.github.grassified.wristprompt.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PresetEditor(val original: PromptPreset?, val name: String, val text: String) {
    override fun toString() = "PresetEditor([redacted])"
}
class PresetsState(
    val catalog: PresetCatalog = PresetCatalog(), val editor: PresetEditor? = null,
    val loaded: Boolean = false, val busy: Boolean = false, val problem: PresetProblem? = null,
) {
    override fun toString() = "PresetsState([redacted])"
}

class PresetsViewModel(private val repository: PresetRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PresetsState())
    val state = mutable.asStateFlow()
    init { reload() }

    fun reload() = operation { repository.load() }
    fun open(preset: PromptPreset? = null) {
        val old = mutable.value
        if (!old.loaded || old.busy) return
        mutable.value = PresetsState(old.catalog, PresetEditor(preset, preset?.name ?: "", preset?.text ?: ""), true)
    }
    fun edit(name: String, text: String) {
        val old = mutable.value
        val editor = old.editor ?: return
        if (old.busy) return
        mutable.value = PresetsState(old.catalog, PresetEditor(editor.original, name, text), true)
    }
    fun close() {
        val old = mutable.value
        if (!old.busy) mutable.value = PresetsState(old.catalog, loaded = old.loaded)
    }
    fun save() {
        val editor = mutable.value.editor ?: return
        operation { repository.save(editor.original?.id, editor.original?.revision, editor.name, editor.text) }
    }
    fun delete(preset: PromptPreset) = operation { repository.delete(preset.id, preset.revision) }

    private fun operation(block: suspend () -> PresetCatalog) {
        val old = mutable.value
        if (old.busy) return
        mutable.value = PresetsState(old.catalog, old.editor, old.loaded, true)
        viewModelScope.launch {
            try { mutable.value = PresetsState(block(), loaded = true) }
            catch (failure: PresetException) { mutable.value = PresetsState(old.catalog, old.editor, old.loaded, problem = failure.problem) }
        }
    }
}
