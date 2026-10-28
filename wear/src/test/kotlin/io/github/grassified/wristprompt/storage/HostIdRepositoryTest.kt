package io.github.grassified.wristprompt.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import io.github.grassified.wristprompt.bridge.BridgeProtocol
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HostIdRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun store() = PreferenceDataStoreFactory.create(scope = scope) {
        File(temporary.root, "installation.preferences_pb")
    }

    @After fun close() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    @Test fun concurrentRepositoriesCreateExactlyOnce() = runBlocking {
        val data = store()
        var creations = 0
        val create = { creations++; BridgeProtocol.newHostId() }
        val ids = (1..20).map {
            async(Dispatchers.Default) { HostIdRepository(data, create).getOrCreate() }
        }.awaitAll()
        assertEquals(1, ids.toSet().size)
        assertEquals(1, creations)
        assertTrue(BridgeProtocol.isHostId(ids.first()))
    }

    @Test fun hostIdSurvivesStorageAndRepositoryRecreation() = runBlocking {
        val expected = HostIdRepository(store()).getOrCreate()
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        assertEquals(expected, HostIdRepository(store()) { error("Must not regenerate") }.getOrCreate())
    }

    @Test fun corruptedHostIdFailsClosedWithoutRegeneration() = runBlocking {
        val data = store()
        data.edit { it[HostIdRepository.HOST_ID] = "corrupted" }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { HostIdRepository(data) { error("Must not regenerate") }.getOrCreate() }
        }
        assertEquals("corrupted", data.data.first()[HostIdRepository.HOST_ID])
    }

    @Test fun invalidGeneratorCannotWriteIdentity() = runBlocking {
        val data = store()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { HostIdRepository(data) { "user@example.com" }.getOrCreate() }
        }
        assertNull(data.data.first()[HostIdRepository.HOST_ID])
    }
}
