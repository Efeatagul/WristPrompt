package io.github.grassified.wristprompt.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.*

@Composable
private fun generatedBitmap(proof: InferenceViewModel, image: GeneratedAttachment, thumbnail: Boolean): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, proof, image, thumbnail) {
        value = try {
            withContext(Dispatchers.IO) {
                val content = proof.generatedContent(image)
                val bytes = if (thumbnail) content.thumbnail else content.image
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { null }
    }
    return bitmap
}

@Composable
internal fun GeneratedImageRow(proof: InferenceViewModel, image: GeneratedAttachment, open: () -> Unit) {
    val bitmap = generatedBitmap(proof, image, true)
    Column(Modifier.fillMaxWidth().clickable(onClick = open)
        .semantics { contentDescription = "Open generated image" }) {
        if (bitmap == null) ShellText("Generated image · tap to open")
        else Image(bitmap.asImageBitmap(), "Generated image", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit)
        ShellText("Open image")
    }
}

@Composable
internal fun GeneratedImageViewer(proof: InferenceViewModel, image: GeneratedAttachment, close: () -> Unit) {
    val bitmap = generatedBitmap(proof, image, false)
    var viewport by remember(image.id) { mutableStateOf(ImageViewport()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            // Inscribed round-screen area keeps the complete aspect-fit image visible at 1x.
            Box(Modifier.fillMaxWidth(0.70f).fillMaxHeight(0.64f).clipToBounds()
                .onSizeChanged { size = it }
                .semantics { contentDescription = "Image viewer"; stateDescription = "Zoom ${viewport.scale}x; pan ${viewport.x}, ${viewport.y}" }
                .pointerInput(image.id, size) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport.transform(zoom, pan.x, pan.y, centroid.x - size.width / 2f,
                            centroid.y - size.height / 2f, size.width.toFloat(), size.height.toFloat(), image.width, image.height)
                    }
                }, contentAlignment = Alignment.Center) {
                if (bitmap == null) Text("Image unavailable", modifier = Modifier.fillMaxWidth())
                else Image(bitmap.asImageBitmap(), "Generated image", modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = viewport.scale; scaleY = viewport.scale
                    translationX = viewport.x; translationY = viewport.y
                }, contentScale = ContentScale.Fit)
            }
            TextButton(onClick = close, modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 12.dp).fillMaxWidth(0.60f).heightIn(min = 48.dp)) { Text("Close") }
        }
    }
}
