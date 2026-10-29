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

    @Test fun unreadablePersistedDataStoreNeverGeneratesAReplacementIdentity() = runBlocking {
        val expected = HostIdRepository(store()).getOrCreate()
        assertTrue(BridgeProtocol.isHostId(expected))
        scope.coroutineContext.job.cancelAndJoin()
        File(temporary.root, "installation.preferences_pb").writeBytes(byteArrayOf(0xff.toByte(), 0xff.toByte()))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var replacements = 0
        try {
            HostIdRepository(store()) { replacements++; BridgeProtocol.newHostId() }.getOrCreate()
            fail("Unreadable identity must not reset")
        } catch (_: androidx.datastore.core.CorruptionException) { }
        assertEquals(0, replacements)
    }

    @Test fun invalidGeneratorCannotWriteIdentity() = runBlocking {
        val data = store()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { HostIdRepository(data) { "user@example.com" }.getOrCreate() }
        }
        assertNull(data.data.first()[HostIdRepository.HOST_ID])
    }
    @Test fun unexpectedStoredIdentityValuesNeverRegenerateOrChangeOnReopening() = runBlocking {
        val data = store()
        for (value in listOf("", "urn:uuid:not-a-uuid", "user@example.test", "urn:uuid:00000000-0000-0000-0000-000000000000")) {
            data.edit { it[HostIdRepository.HOST_ID] = value }
            repeat(2) {
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { HostIdRepository(data) { error("Must not replace established identity") }.getOrCreate() }
                }
                assertEquals(value, data.data.first()[HostIdRepository.HOST_ID])
            }
        }
    }

}
