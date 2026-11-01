package io.github.grassified.wristprompt.images

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhoneImagesViewModel(val store: ImageFileStore, private val resolver: ContentResolver, private val changed: () -> Unit) : ViewModel() {
    private val mutable = MutableStateFlow<String?>(null)
    val error = mutable.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    init { operation { store.load() } }
    fun pickerUnavailable() { mutable.value = "Android Photo Picker is unavailable. Enable or update the system photo picker, then try again." }
    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        operation {
            store.importBatch(uris.size) { index ->
                withContext(Dispatchers.IO) {
                    // Read immediately under the temporary grant. No persistent URI reference.
                    val bytes = resolver.openInputStream(uris[index])?.use { readImageBytes(it, ImageLimits.MAX_INPUT_BYTES) } ?: throw ImageException(ImageProblem.INVALID)
                    AndroidImages.normalize(bytes)
                }
            }
            changed()
        }
    }
    fun delete(id: String) = operation { store.delete(id); changed() }
    private fun operation(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try { mutable.value = null; block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ImageException) { mutable.value = when (error.problem) {
                ImageProblem.CAPACITY -> "Library limit reached (24 photos, 16 MiB, 256 total imports including deleted photos). Reduce the batch or delete photos if the live storage is full. Existing photos were kept."
                ImageProblem.TOO_LARGE -> "A photo exceeds the input or compressed size limit. Choose a smaller image. No photos in this batch were added."
                ImageProblem.DIMENSIONS -> "A photo has invalid or excessive dimensions. No photos in this batch were added."
                ImageProblem.UNSUPPORTED -> "Choose JPEG, PNG or WebP images. No photos in this batch were added."
                else -> "Photo operation failed. Existing photos were preserved. Try again."
            } }
            catch (_: Exception) { mutable.value = "Photos unavailable. Existing data was preserved. Try again." }
            finally { working.value = false }
        }
    }
}
