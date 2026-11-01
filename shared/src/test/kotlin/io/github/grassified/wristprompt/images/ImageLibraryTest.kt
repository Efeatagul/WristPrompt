package io.github.grassified.wristprompt.images

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageLibraryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun content(value: Int = 1, size: Int = 32) = ImageContent(ByteArray(size) { value.toByte() }, byteArrayOf(value.toByte()), 40, 60)
    private suspend fun rejects(problem: ImageProblem? = null, block: suspend () -> Unit) {
        try { block(); fail("accepted invalid operation") } catch (error: ImageException) { if (problem != null) assertEquals(problem, error.problem) }
    }
    @Test fun multiImportStableReloadAndThumbnailAssociation() = runBlocking {
        val directory = temporary.newFolder()
        val store = ImageFileStore(directory)
        val catalog = store.importBatch(3) { content(it + 1) }
        assertEquals(3, catalog.records.map { it.id }.distinct().size)
        assertTrue(catalog.records.all { ImageLimits.validId(it.id) })
        val reload = ImageFileStore(directory)
        assertEquals(catalog, reload.load())
        catalog.records.forEachIndexed { index, image -> assertArrayEquals(byteArrayOf((index + 1).toByte()), reload.thumbnail(image.id)) }
    }
    @Test fun failedBatchPreservesExistingAndRemovesOrphans() = runBlocking {
        val directory = temporary.newFolder()
        val store = ImageFileStore(directory)
        val before = store.importBatch(1) { content() }
        rejects { store.importBatch(3) { if (it == 1) throw ImageException(ImageProblem.INVALID) else content(2) } }
        assertEquals(before, store.load())
        assertEquals(3, directory.listFiles()!!.size)
        val orphan = File(directory, "${ImageLimits.newId()}-1-image.jpg.part").apply { writeText("synthetic") }
        val unrelated = File(directory, "leave-me.txt").apply { writeText("synthetic") }
        ImageFileStore(directory).load()
        assertFalse(orphan.exists()); assertTrue(unrelated.exists())
    }
    @Test fun limitsRejectWithoutEviction() = runBlocking {
        val store = ImageFileStore(temporary.newFolder())
        repeat(4) { store.importBatch(6) { content() } }
        val before = store.load()
        rejects(ImageProblem.CAPACITY) { store.importBatch(1) { content() } }
        assertEquals(before, store.load())
        val other = ImageFileStore(temporary.newFolder())
        rejects(ImageProblem.TOO_LARGE) { other.importBatch(1) { content(size = ImageLimits.MAX_IMAGE_BYTES + 1) } }
        assertTrue(other.load().records.isEmpty())
        repeat(3) { other.importBatch(6) { content(size = ImageLimits.MAX_IMAGE_BYTES) } }
        val full = other.load()
        rejects(ImageProblem.CAPACITY) { other.importBatch(6) { content(size = ImageLimits.MAX_IMAGE_BYTES) } }
        assertEquals(full, other.load())
    }
    @Test fun syncOrderingTrustAndTerminalDelete() = runBlocking {
        val store = ImageFileStore(temporary.newFolder())
        val source = ImageLimits.newId()
        val asset = content()
        val record = asset.record()
        val packet = ImagePacket(source = source, image = record)
        rejects(ImageProblem.PEER) { store.accept("bad", setOf("phone"), packet) { asset } }
        rejects(ImageProblem.PEER) { store.accept("phone", setOf("phone", "other"), packet) { asset } }
        val accepted = store.accept("phone", setOf("phone"), packet) { asset }
        assertEquals(accepted, store.accept("phone", setOf("phone"), packet) { error("duplicate fetched asset") })
        rejects(ImageProblem.PEER) { store.accept("other", setOf("other"), packet) { asset } }
        rejects(ImageProblem.PEER) { store.accept("phone", setOf("phone"), packet.copy(source = ImageLimits.newId())) { asset } }
        rejects(ImageProblem.CONFLICT) { store.accept("phone", setOf("phone"), packet.copy(image = record.copy(width = 50))) { asset } }
        val deleted = store.accept("phone", setOf("phone"), packet.copy(image = LibraryImage(record.id, 2, true))) { error("tombstone fetched asset") }
        assertEquals(deleted, store.accept("phone", setOf("phone"), packet) { error("stale fetched asset") })
        rejects(ImageProblem.CONFLICT) { store.accept("phone", setOf("phone"), packet.copy(image = record.copy(revision = 3))) { asset } }
        assertTrue(store.images.value.isEmpty())
    }
    @Test fun missingCorruptInterruptedAssetsAndRestartRecovery() = runBlocking {
        val directory = temporary.newFolder()
        val store = ImageFileStore(directory)
        val asset = content()
        val packet = ImagePacket(source = ImageLimits.newId(), image = asset.record())
        rejects { store.accept("phone", setOf("phone"), packet) { throw ImageException(ImageProblem.INVALID) } }
        rejects { store.accept("phone", setOf("phone"), packet) { content(2) } }
        assertTrue(store.load().records.isEmpty())
        store.accept("phone", setOf("phone"), packet) { asset }
        val reloaded = ImageFileStore(directory)
        assertEquals(listOf(packet.image), reloaded.load().records)
        assertArrayEquals(asset.image, reloaded.content(packet.image.id).image)
        directory.listFiles()!!.single { it.name.endsWith("-image.jpg") }.delete()
        reloaded.load(); assertTrue(reloaded.images.value.isEmpty())
        rejects { reloaded.content(packet.image.id) }
        reloaded.accept("phone", setOf("phone"), packet) { asset }
        assertEquals(1, reloaded.images.value.size)
        reloaded.accept("phone", setOf("phone"), packet.copy(image = LibraryImage(packet.image.id, 2, true))) { error("no asset") }
        assertEquals(1, directory.listFiles()!!.size)
        assertTrue(ImageFileStore(directory).load().records.single().deleted)
    }
    @Test fun malformedMetadataAndPrivacy() = runBlocking {
        val asset = content()
        val packet = ImagePacket(source = ImageLimits.newId(), image = asset.record())
        val encoded = ImageWire.encode(packet)
        assertEquals(packet, ImageWire.decode(ImageWire.path(packet.image), encoded))
        rejects { ImageWire.decode("wrong", encoded) }
        rejects { ImageWire.encode(packet.copy(version = 9)) }
        rejects { ImageWire.decode(ImageWire.path(packet.image), ByteArray(ImageLimits.MAX_PACKET_BYTES + 1)) }
        rejects { ImageWire.decode(ImageWire.path(packet.image), "private invalid payload".encodeToByteArray()) }
        assertFalse(packet.toString().contains(packet.source))
        assertFalse(packet.image.toString().contains(packet.image.id))
        assertFalse(asset.toString().contains("40"))
        rejects(ImageProblem.TOO_LARGE) { readImageBytes(ByteArray(5).inputStream(), 4) }
    }
    @Test fun assetOwnershipAndInterruptedPromotion() = runBlocking {
        val directory = temporary.newFolder()
        val store = ImageFileStore(directory)
        val before = store.load()
        val content = content()
        val packet = ImagePacket(source = ImageLimits.newId(), image = content.record())
        ImageWire.validateAssetKeys(packet, setOf("image", "thumbnail"))
        rejects { ImageWire.validateAssetKeys(packet, setOf("image")) }
        rejects { ImageWire.validateAssetKeys(packet, emptySet()) }
        val tombstone = packet.copy(image = LibraryImage(packet.image.id, 2, true))
        ImageWire.validateAssetKeys(tombstone, emptySet())
        rejects { ImageWire.validateAssetKeys(tombstone, setOf("image")) }
        val blocked = File(directory, "${packet.image.id}-1-thumb.jpg").apply { mkdir() }
        rejects(ImageProblem.STORAGE) { store.accept("phone", setOf("phone"), packet) { content } }
        assertEquals(before, store.load())
        assertEquals(listOf("catalog.json"), directory.listFiles()!!.filter { it.isFile }.map { it.name })
        blocked.delete()
        store.accept("phone", setOf("phone"), packet) { content }
        assertEquals(listOf(packet.image), store.images.value)
    }
    @Test fun corruptManifestFailsClosedWithoutDeletingFiles() = runBlocking {
        val directory = temporary.newFolder()
        val store = ImageFileStore(directory)
        store.importBatch(1) { content() }
        File(directory, "catalog.json").writeText("invalid")
        rejects { ImageFileStore(directory).load() }
        assertEquals(3, directory.listFiles()!!.size)
    }
}
