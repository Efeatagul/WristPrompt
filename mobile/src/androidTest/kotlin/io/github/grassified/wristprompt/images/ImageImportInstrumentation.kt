package io.github.grassified.wristprompt.images

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking

/** Synthetic platform codec/file tests. Use only on the documented disposable emulator. */
class ImageImportInstrumentation : Instrumentation() {
    private var phase = "start"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(context.packageName.endsWith(".test"))
            runBlocking { verify() }
            result.putString("stream", "PASS: bounded multi-image import, orientation, thumbnails, corrupt/unsupported inputs, Assets validation, private reload/rollback/cleanup\n")
            finish(Activity.RESULT_OK, result)
        } catch (_: Exception) {
            result.putString("stream", "FAIL: $phase; details redacted\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun image(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            for (y in height / 2 until height) for (x in 0 until width) bitmap.setPixel(x, y, Color.BLUE)
            return ByteArrayOutputStream().also { check(bitmap.compress(format, 95, it)) }.toByteArray()
        } finally { bitmap.recycle() }
    }
    private inline fun rejected(block: () -> Unit) {
        try { block() } catch (_: ImageException) { return }
        error("invalid accepted")
    }
    private suspend fun verify() {
        val directory = File(targetContext.createDeviceProtectedStorageContext().cacheDir, "synthetic_image_import_gate")
        directory.deleteRecursively(); check(directory.mkdirs())
        try {
            phase = "portrait-landscape"
            val portrait = AndroidImages.normalize(image(60, 100))
            val landscape = AndroidImages.normalize(image(100, 60))
            check(portrait.width == 60 && portrait.height == 100)
            check(landscape.width == 100 && landscape.height == 60)
            for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP_LOSSLESS)) {
                AndroidImages.normalize(image(60, 100, format))
            }
            phase = "exif-rotation"
            val raw = image(100, 60)
            for (orientation in 1..8) {
                val file = File(directory, "synthetic.jpg").apply { writeBytes(raw) }
                ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                val rotated = AndroidImages.normalize(file.readBytes())
                check(rotated.width == if (orientation >= 5) 60 else 100)
                check(rotated.height == if (orientation >= 5) 100 else 60)
                if (orientation == 6) {
                    val bitmap = BitmapFactory.decodeByteArray(rotated.image, 0, rotated.image.size)!!
                    check(Color.blue(bitmap.getPixel(5, 50)) > Color.red(bitmap.getPixel(5, 50)))
                    check(Color.red(bitmap.getPixel(55, 50)) > Color.blue(bitmap.getPixel(55, 50)))
                    bitmap.recycle()
                }
                file.delete()
            }
            phase = "bounded-decode"
            val large = AndroidImages.normalize(image(3200, 1800))
            check(large.width == 1280 && large.height == 720)
            check(large.image.size <= ImageLimits.MAX_IMAGE_BYTES && large.thumbnail.size <= ImageLimits.MAX_THUMB_BYTES)
            val thumb = BitmapFactory.decodeByteArray(large.thumbnail, 0, large.thumbnail.size)!!
            check(thumb.width == 160 && thumb.height == 90); thumb.recycle()
            rejected { AndroidImages.validateDimensions(0, 1) }
            rejected { AndroidImages.validateDimensions(-1, 1) }
            rejected { AndroidImages.validateDimensions(20001, 1) }
            rejected { AndroidImages.validateDimensions(15000, 15000) }
            rejected { AndroidImages.normalize(ByteArray(ImageLimits.MAX_INPUT_BYTES + 1)) }
            rejected { AndroidImages.normalize(byteArrayOf(1, 2, 3)) }
            rejected { AndroidImages.normalize(raw.copyOf(raw.size / 2)) }
            val gif = android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT)
            rejected { AndroidImages.normalize(gif) }
            phase = "asset-integrity"
            AndroidImages.validate(large.record(), large)
            rejected { AndroidImages.validate(large.record(), portrait) }
            val corrupt = ImageContent(byteArrayOf(1, 2), portrait.thumbnail, 60, 100)
            rejected { AndroidImages.validate(corrupt.record(), corrupt) }
            phase = "batch-reload-rollback"
            val storage = File(directory, "library")
            val store = ImageFileStore(storage)
            val catalog = store.importBatch(2) { if (it == 0) portrait else landscape }
            check(ImageFileStore(storage).load() == catalog)
            check(store.thumbnail(catalog.records[0].id).contentEquals(portrait.thumbnail))
            try { store.importBatch(2) { if (it == 0) large else AndroidImages.normalize(byteArrayOf(1)) }; error("accepted") }
            catch (_: ImageException) { }
            check(store.load() == catalog)
            check(storage.listFiles()!!.size == 5)
        } finally { directory.deleteRecursively() }
    }
}
