package io.github.grassified.wristprompt.storage

import io.github.grassified.wristprompt.openai.ConversationSnapshot
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A per-chat revision owner. Lifecycle cancellation cannot split an atomic checkpoint. */
class ConversationWriter(private val store: ConversationStore, initial: ConversationEntry) {
    private val mutex = Mutex()
    var entry: ConversationEntry = initial; private set
    suspend fun write(snapshot: ConversationSnapshot) = withContext(NonCancellable) {
        mutex.withLock {
            // A late Stop snapshot cannot roll back an already committed completed exchange.
            if (snapshot.replay.size < entry.snapshot.replay.size) return@withLock
            entry = store.save(entry.copy(snapshot = snapshot, updated = System.currentTimeMillis()))
        }
    }
}
