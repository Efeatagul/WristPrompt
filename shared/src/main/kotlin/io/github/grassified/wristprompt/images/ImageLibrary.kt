package io.github.grassified.wristprompt.images

import java.io.InputStream
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ImageLimits {
    const val MAX_EDGE = 1280
    const val MAX_IMAGE_BYTES = 768 * 1024
    const val THUMB_EDGE = 160
    const val MAX_THUMB_BYTES = 24 * 1024
    const val MAX_LIVE = 24
    const val MAX_TOTAL_BYTES = 16 * 1024 * 1024
    const val MAX_BATCH = 6
    const val MAX_RECORDS = 256 // Includes terminal tombstones; never discard replay protection.
    const val MAX_MANIFEST_BYTES = 128 * 1024
    const val MAX_PACKET_BYTES = 2048
    const val MAX_INPUT_BYTES = 20 * 1024 * 1024
    const val MAX_INPUT_EDGE = 20000
    const val MAX_INPUT_PIXELS = 100_000_000L
    fun newId() = UUID.randomUUID().toString()
    fun validId(id: String) = id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
}

enum class ImageProblem { INVALID, UNSUPPORTED, DIMENSIONS, TOO_LARGE, CAPACITY, CONFLICT, PEER, STORAGE }
class ImageException(val problem: ImageProblem) : IllegalArgumentException("Image operation failed: ${problem.name}")
internal fun fail(problem: ImageProblem = ImageProblem.INVALID): Nothing = throw ImageException(problem)

@Serializable
data class LibraryImage(
    val id: String, val revision: Long = 1, val deleted: Boolean = false,
    val width: Int = 0, val height: Int = 0, val imageBytes: Int = 0, val thumbBytes: Int = 0,
    // Integrity only, never identity/display/diagnostics. Digests bind each Asset to its metadata.
    val imageDigest: String = "", val thumbDigest: String = "",
) { override fun toString() = "LibraryImage([redacted])" }

@Serializable
data class ImageCatalog(
    val version: Int = 1, val source: String, val node: String? = null,
    val records: List<LibraryImage> = emptyList(),
) { override fun toString() = "ImageCatalog([redacted])" }

@Serializable
data class ImagePacket(val version: Int = 1, val source: String, val image: LibraryImage) {
    override fun toString() = "ImagePacket([redacted])"
}

class ImageContent(val image: ByteArray, val thumbnail: ByteArray, val width: Int, val height: Int) {
    override fun toString() = "ImageContent([redacted])"
    fun record(id: String = ImageLimits.newId()) = LibraryImage(id, width = width, height = height,
        imageBytes = image.size, thumbBytes = thumbnail.size, imageDigest = digest(image), thumbDigest = digest(thumbnail))
}

fun digest(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** All parsers erase input/cause details. No credential/preset/inference dependencies. */
object ImageWire {
    const val PREFIX = "/wristprompt/images/v1/"
    private val json = Json { encodeDefaults = true }
    fun path(image: LibraryImage) = PREFIX + image.id
    fun validate(image: LibraryImage) {
        if (!ImageLimits.validId(image.id) || image.revision <= 0) fail()
        if (image.deleted) {
            if (image.width != 0 || image.height != 0 || image.imageBytes != 0 || image.thumbBytes != 0 || image.imageDigest != "" || image.thumbDigest != "") fail()
        } else {
            if (image.width !in 1..ImageLimits.MAX_EDGE || image.height !in 1..ImageLimits.MAX_EDGE) fail(ImageProblem.DIMENSIONS)
            if (image.imageBytes !in 1..ImageLimits.MAX_IMAGE_BYTES || image.thumbBytes !in 1..ImageLimits.MAX_THUMB_BYTES) fail(ImageProblem.TOO_LARGE)
            if (!image.imageDigest.matches(Regex("[a-f0-9]{64}")) || !image.thumbDigest.matches(Regex("[a-f0-9]{64}"))) fail()
        }
    }
    fun validateAssetKeys(packet: ImagePacket, keys: Set<String>) {
        if (keys != if (packet.image.deleted) emptySet<String>() else setOf("image", "thumbnail")) fail()
    }
    fun encode(packet: ImagePacket): ByteArray {
        if (packet.version != 1 || !ImageLimits.validId(packet.source)) fail()
        validate(packet.image)
        return json.encodeToString(packet).encodeToByteArray().also { if (it.size > ImageLimits.MAX_PACKET_BYTES) fail() }
    }
    fun decode(path: String, bytes: ByteArray): ImagePacket = sanitized {
        if (bytes.size > ImageLimits.MAX_PACKET_BYTES) fail()
        val packet = json.decodeFromString<ImagePacket>(bytes.decodeToString(throwOnInvalidSequence = true))
        if (!encode(packet).contentEquals(bytes) || path != path(packet.image)) fail()
        packet
    }
    fun encodeCatalog(catalog: ImageCatalog): ByteArray {
        if (catalog.version != 1 || !ImageLimits.validId(catalog.source) || catalog.node?.let { it.isBlank() || it.length > 256 } == true) fail()
        val records = catalog.records
        if (records.size > ImageLimits.MAX_RECORDS || records.count { !it.deleted } > ImageLimits.MAX_LIVE ||
            records.sumOf { it.imageBytes.toLong() + it.thumbBytes } > ImageLimits.MAX_TOTAL_BYTES) fail(ImageProblem.CAPACITY)
        if (records.map { it.id }.distinct().size != records.size) fail()
        records.forEach(::validate)
        return json.encodeToString(catalog).encodeToByteArray().also { if (it.size > ImageLimits.MAX_MANIFEST_BYTES) fail(ImageProblem.CAPACITY) }
    }
    fun decodeCatalog(bytes: ByteArray): ImageCatalog = sanitized {
        if (bytes.size > ImageLimits.MAX_MANIFEST_BYTES) fail()
        val catalog = json.decodeFromString<ImageCatalog>(bytes.decodeToString(throwOnInvalidSequence = true))
        if (!encodeCatalog(catalog).contentEquals(bytes)) fail()
        catalog
    }
    fun validateContent(record: LibraryImage, content: ImageContent) {
        validate(record)
        if (record.deleted || record.width != content.width || record.height != content.height || record.imageBytes != content.image.size ||
            record.thumbBytes != content.thumbnail.size || digest(content.image) != record.imageDigest || digest(content.thumbnail) != record.thumbDigest) fail()
    }
    internal inline fun <T> sanitized(block: () -> T): T = try { block() }
        catch (error: ImageException) { throw error }
        catch (_: Exception) { fail() }
}

fun readImageBytes(input: InputStream, maximum: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, maximum - total + 1))
        if (count < 0) break
        if (count == 0) fail()
        total += count
        if (total > maximum) fail(ImageProblem.TOO_LARGE)
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
