package io.github.grassified.wristprompt.openai

import android.graphics.BitmapFactory
import io.github.grassified.wristprompt.images.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.Base64

/** Memory-only provider bytes. Neither this object nor encoded output is a durable record. */
class GeneratedOutput(val bytes: ByteArray) {
    override fun toString() = "GeneratedOutput([redacted])"
}

@Serializable
data class GeneratedAttachment(val id: String, val width: Int, val height: Int,
    val bytes: Int, val sha256: String, val thumbBytes: Int, val thumbSha256: String) {
    fun validate() {
        require(ImageLimits.validId(id) && width in 1..ImageLimits.MAX_EDGE && height in 1..ImageLimits.MAX_EDGE)
        require(bytes in 1..ImageLimits.MAX_IMAGE_BYTES && thumbBytes in 1..ImageLimits.MAX_THUMB_BYTES)
        require(sha256.matches(Regex("[a-f0-9]{64}")) && thumbSha256.matches(Regex("[a-f0-9]{64}")))
    }
    override fun toString() = "GeneratedAttachment([redacted])"
}

object GenerationOutput {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_ENCODED = ((MAX_BYTES + 2) / 3) * 4
    const val MAX_FRAME = 3 * 1024 * 1024
    const val MAX_STREAM = 12L * 1024 * 1024
    fun checkFrame(data: String) {
        if (data.length > MAX_FRAME) invalid()
        var depth = 0; var quoted = false; var escaped = false
        for (c in data) {
            if (escaped) escaped = false
            else if (quoted && c == '\\') escaped = true
            else if (c == '"') quoted = !quoted
            else if (!quoted) when (c) {
                '{', '[' -> { depth++; if (depth > 32) invalid() }
                '}', ']' -> { depth--; if (depth < 0) invalid() }
            }
        }
        if (depth != 0 || quoted) invalid()
    }
    fun validCallId(id: String) = id.matches(Regex("ig_[A-Za-z0-9_-]{1,200}"))
    fun callReference(item: JsonObject): JsonObject {
        val id = item["id"]?.jsonPrimitive?.takeIf { it.isString }?.content ?: invalid()
        if (!validCallId(id) || item["status"]?.jsonPrimitive?.content != "completed" ||
            item["action"]?.jsonPrimitive?.content.let { it != null && it != "generate" } ||
            item["output_format"]?.jsonPrimitive?.content.let { it != null && it != "jpeg" } ||
            item["error"].let { it != null && it != JsonNull }) invalid()
        return buildJsonObject { put("type", "image_generation_call"); put("id", id) }
    }
    fun decode(encoded: String): GeneratedOutput {
        if (encoded.isEmpty() || encoded.length > MAX_ENCODED || encoded.length % 4 != 0 ||
            !encoded.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) invalid()
        val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { invalid() }
        if (bytes.size > MAX_BYTES || Base64.getEncoder().encodeToString(bytes) != encoded) invalid()
        return GeneratedOutput(bytes)
    }
    /** Bounds and actual MIME before full decode; D7-C strips metadata and bounds canonical JPEG. */
    fun normalize(output: GeneratedOutput): ImageContent {
        val bytes = output.bytes
        if (bytes.size !in 4..MAX_BYTES || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte() ||
            bytes[bytes.size - 2] != 0xff.toByte() || bytes.last() != 0xd9.toByte()) invalid()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this) }
        if (bounds.outMimeType != "image/jpeg" || bounds.outWidth !in 1..1280 || bounds.outHeight !in 1..1280 ||
            bounds.outWidth.toLong() * bounds.outHeight > 1_638_400) invalid()
        return try { AndroidImages.normalize(bytes).also { AndroidImages.validate(it.record(), it) } }
        catch (_: ImageException) { invalid() }
    }
    internal fun invalid(): Nothing = throw ApiFailure(ApiProblem.GENERATION_OUTPUT)
}
