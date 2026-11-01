package io.github.grassified.wristprompt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.grassified.wristprompt.images.*
import io.github.grassified.wristprompt.openai.InferenceViewModel

@Composable
internal fun ImagesScreen(model: WatchImagesViewModel, proof: InferenceViewModel, navigation: ShellNavigationViewModel) {
    val images by model.store.images.collectAsStateWithLifecycle()
    val edit by model.edit.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val cancel = { model.cancel(); navigation.back() }
    BackHandler { cancel() }
    ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, Surface.IMAGES)) {
        item { ShellText("Photos", heading = true) }
        item { ShellText("${edit?.pending?.size ?: 0}/6 selected") }
        error?.let { item { ShellText(it) } }
        if (images.isEmpty()) item { ShellText("No saved photos. Import on your phone, then refresh sync.") }
        images.forEachIndexed { index, image ->
            item { LibraryThumbnail(model.store, image, Modifier.fillMaxWidth().height(96.dp)) }
            action(if (image.id in (edit?.pending ?: emptyList())) "Remove photo ${index + 1}" else "Select photo ${index + 1}") { model.toggle(image.id) }
        }
        action("Clear selection", enabled = edit?.pending?.isNotEmpty() == true, onClick = model::clearSelection)
        action("Use selection", enabled = edit != null) { model.apply(proof) { navigation.back() } }
        action("Cancel", onClick = cancel)
        action("Refresh sync", onClick = model::refresh)
    }
}
