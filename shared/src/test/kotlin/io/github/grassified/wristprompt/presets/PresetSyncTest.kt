package io.github.grassified.wristprompt.presets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

internal class PresetMemoryStore : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())
    private val lock = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = lock.withLock {
        transform(data.value).also { data.value = it }
    }
}

class PresetSyncTest {
    private val source = "10000000-0000-4000-8000-000000000001"
    private val id = "20000000-0000-4000-8000-000000000001"
    private fun record(revision: Long = 1, text: String = "Synthetic", deleted: Boolean = false) = PromptPreset(id, revision, if (deleted) "" else "Name", if (deleted) "" else text, deleted)
    private fun delivery(record: PromptPreset = record(), source: String = this.source): PresetDelivery {
        val packet = PresetPacket(source = source, catalog = PresetCatalog(records = listOf(record)))
        return PresetDelivery(PresetWire.path(record), PresetWire.encode(packet))
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (failure: PresetException) { assertNull(failure.cause); assertFalse(failure.toString().contains("Synthetic")) }
    }
    @Test fun staleDuplicatesConflictsAndTerminalDeletionsAreAtomic(): Unit = runBlocking {
        val memory = PresetMemoryStore(); val store = SyncedPresetStore(memory)
        store.accept("phone", setOf("phone"), listOf(delivery()))
        store.accept("phone", setOf("phone"), listOf(delivery()))
        store.accept("phone", setOf("phone"), listOf(delivery(record(2, "Edited"))))
        store.accept("phone", setOf("phone"), listOf(delivery()))
        assertEquals("Edited", store.load().records.single().text)
        rejected { store.accept("phone", setOf("phone"), listOf(delivery(record(2, "Conflict")))) }
        assertEquals("Edited", store.load().records.single().text)
        store.accept("phone", setOf("phone"), listOf(delivery(record(3, deleted = true))))
        val restarted = SyncedPresetStore(memory)
        restarted.accept("phone", setOf("phone"), listOf(delivery(record(2))))
        assertTrue(restarted.load().records.single().deleted)
        rejected { restarted.accept("phone", setOf("phone"), listOf(delivery(record(4)))) }
        assertTrue(restarted.load().records.single().deleted)
    }
    @Test fun validatesPeerAndPinsNodeAndSourceWithoutAuthDTOs(): Unit = runBlocking {
        val store = SyncedPresetStore(PresetMemoryStore())
        rejected { store.accept("wrong", setOf("phone"), listOf(delivery())) }
        rejected { store.accept("phone", setOf("phone", "other"), listOf(delivery())) }
        assertTrue(store.load().records.isEmpty())
        store.accept("phone", setOf("phone"), listOf(delivery()))
        rejected { store.accept("other", setOf("other"), listOf(delivery())) }
        rejected { store.accept("phone", setOf("phone"), listOf(delivery(source = PresetCodec.newId()))) }
        assertEquals("phone", store.phone())
        assertEquals(1L, store.load().records.single().revision)
    }
    @Test fun wireRejectsMalformedFutureOversizeCountPathAndDuplicateRecords(): Unit = runBlocking {
        val valid = delivery()
        val store = SyncedPresetStore(PresetMemoryStore())
        val variants = listOf(
            PresetDelivery(valid.path, "{Synthetic".encodeToByteArray()),
            PresetDelivery(valid.path, valid.bytes.decodeToString().replaceFirst("\"version\":1", "\"version\":2").encodeToByteArray()),
            PresetDelivery(valid.path, ByteArray(PresetWire.MAX_PACKET_BYTES + 1)),
            PresetDelivery(PresetWire.PREFIX + PresetCodec.newId(), valid.bytes),
            PresetDelivery(valid.path, byteArrayOf(0xC3.toByte(), 0x28)),
        )
        variants.forEach { item -> rejected { store.accept("phone", setOf("phone"), listOf(item)) } }
        rejected { store.accept("phone", setOf("phone"), List(257) { valid }) }
        rejected { store.accept("phone", setOf("phone"), listOf(valid, valid)) }
        rejected { PresetWire.encode(PresetPacket(source = source, catalog = PresetCatalog(records = List(2) { record() }))) }
        assertTrue(store.load().records.isEmpty())
        assertNull(store.phone())
        assertFalse(valid.toString().contains(id))
        assertFalse(PresetWire.decode(valid.path, valid.bytes).toString().contains(source))
    }
    @Test fun fullBatchHandlesDeletionAndCreationAtLiveLimitWithoutOrderDependence(): Unit = runBlocking {
        val store = SyncedPresetStore(PresetMemoryStore())
        val records = (1..50).map { PromptPreset(PresetCodec.newId(), 1, "Name", "Text") }
        store.accept("phone", setOf("phone"), records.map(::delivery))
        val next = records.drop(1) + PromptPreset(PresetCodec.newId(), 1, "New", "Text") + records.first().copy(revision = 2, name = "", text = "", deleted = true)
        store.accept("phone", setOf("phone"), next.map(::delivery))
        assertEquals(50, store.load().records.count { !it.deleted })
        assertEquals(51, store.load().records.size)
        // Missing records are not implicit deletions.
        store.accept("phone", setOf("phone"), listOf(delivery(next.first())))
        assertEquals(50, store.load().records.count { !it.deleted })
    }
    @Test fun publisherWaitsForCapabilityAndReconnectReloadsLatestDurableState(): Unit = runBlocking {
        var catalog = PresetCatalog(records = listOf(record()))
        var reachable = false
        val dataItems = mutableMapOf<String, ByteArray>()
        val publisher = PresetPublisher({ catalog }, { source }, { reachable }, { path, bytes -> dataItems[path] = bytes })
        publisher.publish(); assertTrue(dataItems.isEmpty())
        reachable = true; publisher.publish()
        val watch = SyncedPresetStore(PresetMemoryStore())
        watch.accept("phone", setOf("phone"), dataItems.map { PresetDelivery(it.key, it.value) })
        assertEquals(catalog, watch.load())
        catalog = PresetCatalog(records = listOf(record(2, deleted = true)))
        reachable = false; publisher.publish()
        reachable = true; publisher.publish()
        watch.accept("phone", setOf("phone"), dataItems.map { PresetDelivery(it.key, it.value) })
        assertTrue(watch.load().records.single().deleted)
        watch.accept("phone", setOf("phone"), listOf(delivery()))
        assertTrue(watch.load().records.single().deleted)
    }
}
