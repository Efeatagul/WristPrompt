package io.github.grassified.wristprompt.presets

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PresetSyncStatus { WAITING, QUEUED, FAILED }

class PresetPublisher(
    private val load: suspend () -> PresetCatalog,
    private val source: suspend () -> String,
    private val reachable: suspend () -> Boolean,
    private val put: suspend (String, ByteArray) -> Unit,
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(PresetSyncStatus.WAITING)
    val status = mutable.asStateFlow()
    suspend fun publish() = mutex.withLock {
        try {
            if (!reachable()) { mutable.value = PresetSyncStatus.WAITING; return@withLock }
            val catalog = load() // Always reload inside the lock: an old queued job cannot publish an old snapshot.
            PresetCodec.encode(catalog)
            val authority = source()
            for (record in catalog.records) {
                put(PresetWire.path(record), PresetWire.encode(PresetPacket(source = authority, catalog = PresetCatalog(records = listOf(record)))))
            }
            mutable.value = PresetSyncStatus.QUEUED // GMS queued, not a watch storage ACK.
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = PresetSyncStatus.FAILED }
    }
}
