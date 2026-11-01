package io.github.grassified.wristprompt.images

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode only bounded private thumbnails; failures expose no paths or exception details. */
@Composable
fun LibraryThumbnail(store: ImageFileStore, image: LibraryImage, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, store, image) {
        value = withContext(Dispatchers.IO) {
            try {
                val bytes = store.thumbnail(image.id)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth !in 1..ImageLimits.THUMB_EDGE || options.outHeight !in 1..ImageLimits.THUMB_EDGE) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: ImageException) { null }
        }
    }
    bitmap?.let { Image(it, contentDescription = "Imported photo", modifier = modifier, contentScale = ContentScale.Fit) }
}
