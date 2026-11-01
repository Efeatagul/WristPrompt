package io.github.grassified.wristprompt.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded platform decode. No original URI/EXIF/name survives normalization. */
object AndroidImages {
    fun normalize(bytes: ByteArray): ImageContent = safe {
        if (bytes.isEmpty() || bytes.size > ImageLimits.MAX_INPUT_BYTES) fail(ImageProblem.TOO_LARGE)
        val bounds = bounds(bytes)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) fail(ImageProblem.UNSUPPORTED)
        validateDimensions(bounds.outWidth, bounds.outHeight)
        if (bounds.outMimeType == "image/jpeg" && (bytes.size < 4 || bytes[bytes.lastIndex - 1] != 0xff.toByte() || bytes.last() != 0xd9.toByte())) fail()
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation !in 0..8) fail()
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > ImageLimits.MAX_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false
        }) ?: fail()
        try {
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try {
                val normalized = scaled(oriented, ImageLimits.MAX_EDGE)
                try {
                    // Flatten transparency against white; JPEG strips original EXIF/private metadata.
                    val opaque = Bitmap.createBitmap(normalized.width, normalized.height, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(normalized, 0f, 0f, null) }
                        val image = compress(opaque, ImageLimits.MAX_IMAGE_BYTES)
                        val thumb = scaled(opaque, ImageLimits.THUMB_EDGE)
                        try { ImageContent(image, compress(thumb, ImageLimits.MAX_THUMB_BYTES), opaque.width, opaque.height) }
                        finally { if (thumb !== opaque) thumb.recycle() }
                    } finally { opaque.recycle() }
                } finally { if (normalized !== oriented) normalized.recycle() }
            } finally { if (oriented !== decoded) oriented.recycle() }
        } finally { decoded.recycle() }
    }
    fun validateDimensions(width: Int, height: Int) {
        if (width !in 1..ImageLimits.MAX_INPUT_EDGE || height !in 1..ImageLimits.MAX_INPUT_EDGE || width.toLong() * height > ImageLimits.MAX_INPUT_PIXELS) fail(ImageProblem.DIMENSIONS)
    }
    /** Receiving canonical JPEG still requires actual decode, not just matching hashes. */
    fun validate(record: LibraryImage, content: ImageContent): ImageContent = safe {
        ImageWire.validateContent(record, content)
        checkJpeg(content.image, record.width, record.height, ImageLimits.MAX_EDGE)
        checkJpeg(content.thumbnail, null, null, ImageLimits.THUMB_EDGE)
        content
    }
    private fun checkJpeg(bytes: ByteArray, width: Int?, height: Int?, edge: Int) {
        if (bytes.size < 4 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte() || bytes[bytes.lastIndex - 1] != 0xff.toByte() || bytes.last() != 0xd9.toByte()) fail()
        val info = bounds(bytes)
        if (info.outMimeType != "image/jpeg" || info.outWidth !in 1..edge || info.outHeight !in 1..edge ||
            (width != null && info.outWidth != width) || (height != null && info.outHeight != height)) fail()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: fail()
        bitmap.recycle()
    }
    private fun bounds(bytes: ByteArray) = BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this) }
    private fun scaled(bitmap: Bitmap, edge: Int): Bitmap {
        val ratio = minOf(1.0, edge.toDouble() / max(bitmap.width, bitmap.height))
        if (ratio == 1.0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * ratio).roundToInt()), max(1, (bitmap.height * ratio).roundToInt()), true)
    }
    private fun compress(bitmap: Bitmap, cap: Int): ByteArray {
        for (quality in listOf(85, 70, 55, 40)) {
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) fail()
            if (out.size() <= cap) return out.toByteArray()
        }
        fail(ImageProblem.TOO_LARGE)
    }
    private inline fun <T> safe(block: () -> T): T = try { block() }
        catch (error: ImageException) { throw error }
        catch (_: OutOfMemoryError) { fail(ImageProblem.TOO_LARGE) }
        catch (_: Exception) { fail() }
}
