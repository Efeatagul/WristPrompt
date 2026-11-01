package io.github.grassified.wristprompt.presets

import io.github.grassified.wristprompt.bridge.WearConnectivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WatchPresetSync(private val connectivity: WearConnectivity, private val store: SyncedPresetStore) {
    private val mutex = Mutex()
    suspend fun refresh(): Boolean = mutex.withLock {
        try {
            val phones = connectivity.reachablePeers().map { it.id }.toSet()
            val bound = store.phone()
            val node = bound?.takeIf { it in phones } ?: if (bound == null) phones.singleOrNull() else null
            if (node == null) return@withLock false
            val items = connectivity.presetItems(node)
            if (items.isNotEmpty()) store.accept(node, phones, items)
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
