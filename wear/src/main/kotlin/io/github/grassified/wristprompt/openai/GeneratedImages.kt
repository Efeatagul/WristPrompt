package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.images.*
import io.github.grassified.wristprompt.storage.*
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PreparedGeneratedImage(val reference: GeneratedAttachment, val content: ImageContent) {
    override fun toString() = "PreparedGeneratedImage([redacted])"
}

/** SQLite's canonical completed snapshot is the ONLY ownership manifest.
 * Fsynced immutable files precede SQLite commit. A crash before SQLite leaves unowned
 * reservations; recovery reads canonical SQLite before deleting any recognized orphan.
 * No operation compensates by deleting a committed conversation/replay row.
 */
class GeneratedImages(private val root: File,
    private val normalize: suspend (GeneratedOutput) -> ImageContent = { GenerationOutput.normalize(it) },
    private val validate: (LibraryImage, ImageContent) -> ImageContent = AndroidImages::validate,
    private val syncDirectory: (File) -> Unit = { directory ->
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    },
) {
    companion object { const val MAX_TOTAL_BYTES = 64L * 1024 * 1024 }
    private val mutex = Mutex()
    private val owned = Regex("[0-9a-f-]{36}-(image|thumb)\\.jpg(\\.part)?")
    private fun directory(owner: String, chat: String): File {
        require(ImageLimits.validId(owner) && ImageLimits.validId(chat))
        return File(File(root, owner), chat)
    }
    suspend fun prepare(output: GeneratedOutput): PreparedGeneratedImage = withContext(Dispatchers.IO) {
        val content = normalize(output)
        val record = content.record()
        validate(record, content)
        PreparedGeneratedImage(GeneratedAttachment(record.id, record.width, record.height, record.imageBytes,
            record.imageDigest, record.thumbBytes, record.thumbDigest), content)
    }
    suspend fun commit(entry: ConversationEntry, image: PreparedGeneratedImage, save: suspend () -> Unit) =
        withContext(NonCancellable + Dispatchers.IO) { mutex.withLock {
            try {
                image.reference.validate()
                val directory = directory(entry.owner, entry.id)
                require(directory.exists() || directory.mkdirs())
                sync(checkNotNull(root.parentFile)); sync(root); sync(checkNotNull(directory.parentFile))
                val used = root.walkTopDown().maxDepth(3).filter { it.isFile }.sumOf { it.length() }
                require(used + image.content.image.size + image.content.thumbnail.size <= MAX_TOTAL_BYTES)
                val reference = image.reference
                require(image.content.record(reference.id).let { it.width == reference.width && it.height == reference.height &&
                    it.imageBytes == reference.bytes && it.imageDigest == reference.sha256 &&
                    it.thumbBytes == reference.thumbBytes && it.thumbDigest == reference.thumbSha256 })
                atomic(File(directory, "${reference.id}-image.jpg"), image.content.image)
                atomic(File(directory, "${reference.id}-thumb.jpg"), image.content.thumbnail)
                sync(directory)
                // Failure leaves an unowned reservation, never a completed attachment.
                save()
            } catch (failure: CancellationException) { throw failure
            } catch (_: Exception) { throw ConversationStorageException() }
        } }
    suspend fun content(owner: String, chat: String, reference: GeneratedAttachment): ImageContent = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                reference.validate()
                val directory = directory(owner, chat)
                fun read(kind: String, size: Int, hash: String) = File(directory, "${reference.id}-$kind.jpg").inputStream().use {
                    readImageBytes(it, size).also { bytes -> require(bytes.size == size && digest(bytes) == hash) }
                }
                val content = ImageContent(read("image", reference.bytes, reference.sha256),
                    read("thumb", reference.thumbBytes, reference.thumbSha256), reference.width, reference.height)
                validate(content.record(reference.id), content)
            } catch (_: Exception) { throw ApiFailure(ApiProblem.IMAGE_UNAVAILABLE) }
        }
    }
    /** Serialize with file promotion + SQLite save; unreadable SQLite is never interpreted as empty. */
    suspend fun recover(store: ConversationStore) = withContext(Dispatchers.IO) { mutex.withLock {
        root.listFiles().orEmpty().filter { it.isDirectory && ImageLimits.validId(it.name) }.take(1000).forEach { owner ->
            owner.listFiles().orEmpty().filter { it.isDirectory && ImageLimits.validId(it.name) }.take(10000).forEach { chat ->
                try {
                    val snapshot = store.load(chat.name, owner.name)?.snapshot
                    val keep = snapshot?.messages.orEmpty().flatMap { message -> message.parts.filterIsInstance<ContentPart.GeneratedImage>() }
                        .flatMap { listOf("${it.reference.id}-image.jpg", "${it.reference.id}-thumb.jpg") }.toSet()
                    cleanup(chat, keep)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { /* Preserve all files on unreadable/unknown canonical metadata. */ }
            }
        }
    } }
    /** Call only after the canonical chat tombstone succeeds. Imported library is unreachable. */
    suspend fun delete(owner: String, chat: String) = withContext(Dispatchers.IO) {
        mutex.withLock { cleanup(directory(owner, chat), emptySet()) }
    }
    private fun cleanup(directory: File, keep: Set<String>) {
        directory.listFiles().orEmpty().take(1024).forEach { file ->
            if (file.isFile && owned.matches(file.name) && file.name !in keep) check(file.delete())
        }
        if (directory.isDirectory) { sync(directory); if (directory.listFiles()?.isEmpty() == true) directory.delete() }
    }
    private fun atomic(target: File, bytes: ByteArray) {
        require(!target.exists()) // Fresh opaque IDs, never overwrite canonical content.
        val temp = File(target.parentFile, target.name + ".part")
        FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
    private fun sync(directory: File) = syncDirectory(directory)
}
