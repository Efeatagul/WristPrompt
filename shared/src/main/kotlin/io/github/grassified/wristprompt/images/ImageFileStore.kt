package io.github.grassified.wristprompt.images

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One application-owned instance per directory. The manifest is the only commit point.
 * Immutable revision files are fsynced before atomic manifest replacement. Orphans are
 * removed only after loading a valid manifest; unreadable metadata fails closed.
 */
class ImageFileStore(private val directory: File) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<List<LibraryImage>>(emptyList())
    val images = mutable.asStateFlow()
    private val allLive = MutableStateFlow<List<LibraryImage>>(emptyList())
    val records = allLive.asStateFlow()
    private val manifest get() = File(directory, "catalog.json")
    private fun file(record: LibraryImage, thumbnail: Boolean) = File(directory, "${record.id}-${record.revision}-${if (thumbnail) "thumb" else "image"}.jpg")

    suspend fun load(): ImageCatalog = locked { loadInternal().also(::publish) }

    /** A failed batch never commits any of its records. Input decode happens one at a time. */
    suspend fun importBatch(count: Int, normalize: suspend (Int) -> ImageContent): ImageCatalog = locked {
        if (count !in 1..ImageLimits.MAX_BATCH) fail(ImageProblem.CAPACITY)
        val old = loadInternal()
        if (old.node != null) fail(ImageProblem.PEER) // watch cannot author phone records
        if (old.records.count { !it.deleted } + count > ImageLimits.MAX_LIVE || old.records.size + count > ImageLimits.MAX_RECORDS) fail(ImageProblem.CAPACITY)
        var records = old.records
        try {
            repeat(count) { index ->
                val content = normalize(index)
                val record = content.record()
                records = records + record
                ImageWire.encodeCatalog(old.copy(records = records)) // Capacity before promotion.
                stage(record, content)
            }
            commit(old.copy(records = records))
        } finally { cleanup(loadInternal()) }
    }

    suspend fun delete(id: String): ImageCatalog = locked {
        val old = loadInternal()
        if (old.node != null) fail(ImageProblem.PEER)
        val record = old.records.singleOrNull { it.id == id } ?: fail()
        if (record.deleted) return@locked old
        if (record.revision == Long.MAX_VALUE) fail(ImageProblem.CONFLICT)
        val tombstone = LibraryImage(id, revision = record.revision + 1, deleted = true)
        commit(old.copy(records = old.records.map { if (it.id == id) tombstone else it })).also { cleanup(it) }
    }

    /** Validate trust and ordering BEFORE resolving any Asset. No application ACK is
     * claimed: durable DataItems are pulled again on reconnect/startup/manual refresh.
     */
    suspend fun accept(node: String, eligible: Set<String>, packet: ImagePacket, content: suspend () -> ImageContent): ImageCatalog = locked {
        ImageWire.encode(packet)
        if (node.isBlank() || node.length > 256 || node !in eligible) fail(ImageProblem.PEER)
        val old = loadInternal()
        if (old.node == null) {
            if (eligible != setOf(node) || old.records.isNotEmpty()) fail(ImageProblem.PEER)
        } else if (old.node != node || old.source != packet.source) fail(ImageProblem.PEER)
        val next = packet.image
        val current = old.records.find { it.id == next.id }
        if (current != null) {
            if (next.revision < current.revision) return@locked old
            if (next.revision == current.revision && next != current) fail(ImageProblem.CONFLICT)
            if (current.deleted && !next.deleted) fail(ImageProblem.CONFLICT)
            if (next == current && (next.deleted || available(next))) return@locked old
        }
        val result = ImageCatalog(source = packet.source, node = node, records = old.records.filterNot { it.id == next.id } + next)
        ImageWire.encodeCatalog(result)
        try {
            if (!next.deleted) stage(next, content())
            commit(result)
        } finally { cleanup(loadInternal()) }
    }

    suspend fun content(id: String): ImageContent = locked {
        val record = loadInternal().records.singleOrNull { it.id == id && !it.deleted } ?: fail()
        val content = ImageContent(read(file(record, false), ImageLimits.MAX_IMAGE_BYTES), read(file(record, true), ImageLimits.MAX_THUMB_BYTES), record.width, record.height)
        ImageWire.validateContent(record, content)
        content
    }
    suspend fun thumbnail(id: String): ByteArray = locked {
        val record = loadInternal().records.singleOrNull { it.id == id && !it.deleted } ?: fail()
        read(file(record, true), ImageLimits.MAX_THUMB_BYTES).also { if (digest(it) != record.thumbDigest) fail() }
    }
    private fun available(record: LibraryImage) = file(record, false).isFile && file(record, true).isFile &&
        file(record, false).length() == record.imageBytes.toLong() && file(record, true).length() == record.thumbBytes.toLong()
    private fun publish(catalog: ImageCatalog) {
        allLive.value = catalog.records.filterNot { it.deleted }
        mutable.value = allLive.value.filter(::available)
    }
    private fun read(file: File, cap: Int) = file.inputStream().use { readImageBytes(it, cap) }
    private fun loadInternal(): ImageCatalog {
        if (!directory.exists() && !directory.mkdirs()) fail(ImageProblem.STORAGE)
        val catalog = if (manifest.exists()) ImageWire.decodeCatalog(read(manifest, ImageLimits.MAX_MANIFEST_BYTES)) else {
            ImageCatalog(source = ImageLimits.newId()).also { commit(it) }
        }
        cleanup(catalog)
        return catalog
    }
    private fun stage(record: LibraryImage, content: ImageContent) {
        ImageWire.validateContent(record, content)
        // Bytes are fully validated by the normalizer/receiver before this storage boundary.
        atomic(file(record, false), content.image)
        atomic(file(record, true), content.thumbnail)
    }
    private fun commit(catalog: ImageCatalog): ImageCatalog {
        atomic(manifest, ImageWire.encodeCatalog(catalog))
        publish(catalog)
        return catalog
    }
    private fun atomic(target: File, bytes: ByteArray) {
        val temporary = File(directory, target.name + ".part")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    private fun cleanup(catalog: ImageCatalog) {
        val keep = catalog.records.filterNot { it.deleted }.flatMap { listOf(file(it, false).name, file(it, true).name) }.toSet()
        // Own directory only; recognize exact names, never recurse or follow subdirectories.
        val owned = Regex("[0-9a-f-]{36}-[1-9][0-9]{0,18}-(image|thumb)\\.jpg(\\.part)?")
        directory.listFiles()?.take(1024)?.forEach {
            if (it.isFile && (it.name == "catalog.json.part" || (owned.matches(it.name) && it.name !in keep))) it.delete()
        }
    }
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ImageException) { throw error }
            catch (_: Exception) { fail(ImageProblem.STORAGE) }
        }
    }
}
