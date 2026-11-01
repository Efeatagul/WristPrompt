package io.github.grassified.wristprompt.presets

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PresetRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val file get() = File(temporary.root, "presets.preferences_pb")
    private fun store(scope: CoroutineScope) = PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test fun createEditRenameDeleteSurviveActualStoreRecreation(): Unit = runBlocking {
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repository = PresetRepository(store(scope))
        suspend fun reopen() {
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = PresetRepository(store(scope))
        }
        try {
            val first = repository.save(null, null, "Synthetic", "First\nSecond").records.single()
            reopen()
            assertEquals(first, repository.load().records.single())
            val edited = repository.save(first.id, 1, first.name, "Edited\nMultiline").records.single()
            reopen()
            assertEquals(edited, repository.load().records.single())
            assertEquals(first.id, edited.id)
            val renamed = repository.save(first.id, 2, "Renamed", edited.text).records.single()
            reopen()
            assertEquals(renamed, repository.load().records.single())
            repository.delete(first.id, 3)
            reopen()
            val deleted = repository.load().records.single()
            assertTrue(deleted.deleted)
            assertEquals(4L, deleted.revision)
            assertEquals("", deleted.text)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun concurrentEditsRejectLostUpdateAndInvalidSavePreservesCommittedData(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = PresetRepository(store(scope))
            val first = repository.save(null, null, "Synthetic", "Original").records.single()
            val outcomes = (1..2).map { async { try { repository.save(first.id, 1, "Name", "Edit $it"); true } catch (_: PresetException) { false } } }.awaitAll()
            assertEquals(1, outcomes.count { it })
            val before = repository.load()
            try { repository.save(first.id, 2, "", "Text"); fail() } catch (failure: PresetException) { assertEquals(PresetProblem.NAME, failure.problem) }
            assertEquals(before, repository.load())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun malformedAndFutureDataArePreservedWithoutReset(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(scope)
            val repository = PresetRepository(store)
            val key = stringPreferencesKey("catalog_v1")
            for (raw in listOf("{private-synthetic", "{\"version\":2,\"records\":[]}")) {
                store.edit { it[key] = raw }
                try { repository.load(); fail() } catch (failure: PresetException) { assertNull(failure.cause) }
                try { repository.save(null, null, "Name", "Text"); fail() } catch (_: PresetException) { }
                assertEquals(raw, store.data.first()[key])
            }
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun storageFailuresAreSanitizedAndDoNotPublishSuccessfulEdits(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val failing = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.flowOf(androidx.datastore.preferences.core.emptyPreferences())
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
                throw java.io.IOException("private-synthetic")
            }
        }
        val repository = PresetRepository(failing)
        val model = PresetsViewModel(repository)
        suspend fun idle() { withTimeout(5000) { while (model.state.value.busy) delay(5) } }
        try {
            idle()
            model.open(); model.edit("Synthetic", "private-synthetic"); model.save(); idle()
            assertEquals(PresetProblem.STORAGE, model.state.value.problem)
            assertEquals("private-synthetic", model.state.value.editor?.text)
            assertTrue(model.state.value.catalog.records.isEmpty())
            try { repository.save(null, null, "Name", "Text"); fail() } catch (failure: PresetException) {
                assertNull(failure.cause)
                assertFalse(failure.toString().contains("private-synthetic"))
            }
        } finally { model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun capacityFailureDoesNotEvictOrPartiallyCommit(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(scope)
            val repository = PresetRepository(store)
            val full = PresetCatalog(records = (1..50).map {
                PromptPreset("00000000-0000-4000-8000-${it.toString().padStart(12, '0')}", 1, "Name", "Text")
            })
            store.edit { it[stringPreferencesKey("catalog_v1")] = PresetCodec.encode(full) }
            try { repository.save(null, null, "Extra", "Text"); fail() } catch (failure: PresetException) { assertEquals(PresetProblem.COUNT, failure.problem) }
            assertEquals(full, repository.load())
            repository.delete(full.records.first().id, 1)
            assertEquals(49, repository.load().records.count { !it.deleted })
            repository.save(null, null, "Replacement", "Text")
            assertEquals(51, repository.load().records.size)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun viewModelOperationsAreLocalAndReloadIsInert(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = PresetRepository(store(scope))
        val model = PresetsViewModel(repository)
        suspend fun idle() { withTimeout(5000) { while (model.state.value.busy) delay(5) } }
        try {
            idle()
            model.open(); model.edit("Synthetic", "private-synthetic\nText"); model.save(); idle()
            assertEquals(1, repository.load().records.size)
            assertNull(model.state.value.editor)
            val first = model.state.value.catalog.records.single()
            model.open(first); model.edit("Renamed", "Edited"); model.save(); idle()
            model.delete(model.state.value.catalog.records.single()); idle()
            model.reload(); idle()
            assertTrue(model.state.value.catalog.records.single().deleted)
            assertFalse(model.state.value.toString().contains("private-synthetic"))
            assertFalse(PresetEditor(first, "private-synthetic", "private-synthetic").toString().contains("private-synthetic"))
            // Neither this ViewModel nor its repository accepts an inference/network dependency.
            // Loading/mutating uses only the real isolated local DataStore above.
        } finally {
            model.viewModelScope.cancel()
            scope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }
}
