package io.github.grassified.wristprompt.images

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PhoneImagesUi(model: PhoneImagesViewModel, sync: ImageSyncStatus, retry: () -> Unit) {
    val images by model.store.records.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ImageLimits.MAX_BATCH), model::import)
    Text("Photo library", style = MaterialTheme.typography.titleLarge)
    Text("${images.size}/${ImageLimits.MAX_LIVE} photos · ${images.sumOf { it.imageBytes + it.thumbBytes } / 1024} / 16384 KiB")
    Text("Import up to 6 at once. JPEG/PNG/WebP input up to 20 MiB, 20,000 px per edge / 100 MP. Saved JPEGs: 1280 px / 768 KiB; thumbnails: 160 px / 24 KiB. Up to 256 total imports per installation, including deleted photos. Nothing is sent to OpenAI.")
    error?.let { Text(it) }
    Button(enabled = !busy, onClick = {
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: android.content.ActivityNotFoundException) { model.pickerUnavailable() }
        catch (_: SecurityException) { model.pickerUnavailable() }
    }) { Text(if (busy) "Working…" else "Import photos") }
    Text(when (sync) {
        ImageSyncStatus.QUEUED -> "Queued in Data Layer. Watch storage is not yet confirmed."
        ImageSyncStatus.FAILED -> "Sync incomplete. Open both apps and retry."
        else -> "Open the paired watch app to synchronize."
    })
    TextButton(onClick = retry, enabled = !busy) { Text("Retry photo sync") }
    images.forEachIndexed { index, image ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LibraryThumbnail(model.store, image, Modifier.size(80.dp))
            TextButton(onClick = { model.delete(image.id) }, enabled = !busy) { Text("Delete photo ${index + 1}") }
        }
    }
}
