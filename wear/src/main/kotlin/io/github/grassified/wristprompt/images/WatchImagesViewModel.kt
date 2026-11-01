package io.github.grassified.wristprompt.images

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.openai.InferenceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImagePickerEdit(val original: List<String>, val pending: List<String>, val draft: String, val token: Long) {
    override fun toString() = "ImagePickerEdit([redacted])"
}
class WatchImagesViewModel(val store: ImageFileStore, private val sync: suspend () -> Boolean) : ViewModel() {
    private val mutable = MutableStateFlow<ImagePickerEdit?>(null)
    val edit = mutable.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    fun begin(proof: InferenceViewModel): Boolean {
        val token = proof.inputToken() ?: return false
        val original = proof.imageSelection.selected.value.toList()
        mutable.value = ImagePickerEdit(original, original, proof.chat.value.draft, token)
        failure.value = null
        refresh()
        return true
    }
    fun toggle(id: String) {
        val old = mutable.value ?: return
        if (store.images.value.none { it.id == id }) return
        val next = if (id in old.pending) old.pending - id else old.pending + id
        if (next.size > ImageLimits.MAX_BATCH) { failure.value = "Select up to 6 photos."; return }
        mutable.value = ImagePickerEdit(old.original, next, old.draft, old.token)
    }
    fun clearSelection() { mutable.value?.let { mutable.value = ImagePickerEdit(it.original, emptyList(), it.draft, it.token) } }
    fun cancel() { mutable.value = null; failure.value = null }
    fun apply(proof: InferenceViewModel, applied: () -> Unit) {
        val old = mutable.value ?: return
        viewModelScope.launch {
            try {
                store.load()
                val available = store.images.value.map { it.id }.toSet()
                // Resolve each selected file before accepting IDs into the composer.
                old.pending.forEach { store.content(it) }
                if (mutable.value !== old || proof.inputToken() != old.token || proof.chat.value.draft != old.draft ||
                    !proof.imageSelection.replace(old.original, old.pending, available)) {
                    failure.value = "Draft or library changed. Cancel and select again."; return@launch
                }
                cancel(); applied()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure.value = "A photo is unavailable. Cancel and select again." }
        }
    }
    fun refresh() { viewModelScope.launch {
        try { store.load(); if (!sync()) failure.value = "Phone sync unavailable. Saved photos remain available offline." }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure.value = "Photo library unavailable. Existing data was preserved." }
    } }
}
