package io.github.grassified.wristprompt.presets

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PresetSyncIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun phoneCrudReachesWatchAndRealDiskRestartKeepsAuthorityAndDeletion(): Unit = runBlocking {
        val phoneScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val phoneData = PreferenceDataStoreFactory.create(scope = phoneScope) { File(temporary.root, "phone.preferences_pb") }
        fun watchData() = PreferenceDataStoreFactory.create(scope = watchScope) { File(temporary.root, "watch.preferences_pb") }
        val phone = PresetRepository(phoneData)
        var watch = SyncedPresetStore(watchData())
        val items = mutableMapOf<String, ByteArray>()
        val publisher = PresetPublisher(phone::load, phone::source, { true }, { path, bytes -> items[path] = bytes })
        suspend fun sync() { publisher.publish(); if (items.isNotEmpty()) watch.accept("phone", setOf("phone"), items.map { PresetDelivery(it.key, it.value) }) }
        try {
            val first = phone.save(null, null, "Synthetic", "Initial\nText").records.single()
            sync(); assertEquals(phone.load().records.associateBy { it.id }, watch.load().records.associateBy { it.id })
            val second = phone.save(null, null, "Second", "Second text").records.last()
            sync(); assertEquals(2, watch.load().records.size)
            phone.save(first.id, 1, "Renamed", "Edited"); sync()
            assertEquals(phone.load().records.associateBy { it.id }, watch.load().records.associateBy { it.id })
            phone.delete(second.id, second.revision); sync()
            val before = watch.load()
            watchScope.coroutineContext.job.cancelAndJoin()
            watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            watch = SyncedPresetStore(watchData())
            assertEquals(before, watch.load())
            assertEquals("phone", watch.phone())
            sync(); assertEquals(before, watch.load())
            assertTrue(watch.load().records.single { it.id == second.id }.deleted)
            assertEquals(phone.source(), phone.source())
        } finally { phoneScope.coroutineContext.job.cancelAndJoin(); watchScope.coroutineContext.job.cancelAndJoin() }
    }
}
