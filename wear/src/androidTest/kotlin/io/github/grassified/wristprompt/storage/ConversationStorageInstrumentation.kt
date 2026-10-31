package io.github.grassified.wristprompt.storage

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Standalone synthetic storage gate; no credentials, network or owner-app data. */
class ConversationStorageInstrumentation : Instrumentation() {
    private var phase = "context"
    // Instrumentation executes with target UID; use its separate device-protected
    // namespace, never the production credential-protected conversation database.
    private val storageContext get() = targetContext.createDeviceProtectedStorageContext()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val outcome = Bundle()
        try {
            check(context.packageName.endsWith(".test"))
            runBlocking { verify() }
            outcome.putString("stream", "PASS: SQLite durable lifecycle, rollback, owner isolation, deletion, future schema, corruption preservation\n")
            finish(Activity.RESULT_OK, outcome)
        } catch (failure: Exception) {
            outcome.putString("stream", "FAIL: $phase (${failure.javaClass.simpleName}); payloads redacted\n")
            finish(Activity.RESULT_CANCELED, outcome)
        }
    }
    private suspend fun verify() {
        check(storageContext.isDeviceProtectedStorage)
        storageContext.deleteDatabase("conversations.db") // Only the disposable test namespace.
        phase = "create"
        var store = SqliteConversationStore(storageContext)
        val owner = store.owner("a".repeat(64)); val other = store.owner("b".repeat(64))
        check(owner == store.owner("a".repeat(64)) && owner != other)
        val chat = ChatConversation().apply { draft("synthetic question"); begin() }
        var entry = store.save(ConversationEntry(owner = owner, route = "CHATGPT_PLAN", model = "synthetic",
            title = "Synthetic", updated = 1, snapshot = chat.snapshot()))
        store.close(); store = SqliteConversationStore(storageContext)
        phase = "restore-interrupted"
        val interrupted = checkNotNull(store.load(entry.id, owner))
        val reopened = ChatConversation().apply { restore(interrupted.snapshot) }
        check(!reopened.state.busy && reopened.state.messages.single().status == ChatMessageStatus.EXCLUDED)
        check(store.load(entry.id, other) == null && store.list(other).isEmpty())
        chat.complete(StreamResult().apply { accept(ResponseEvent.Completed("synthetic answer")); end() })
        phase = "commit-completed"
        entry = store.save(entry.copy(snapshot = chat.snapshot()))
        store.close(); store = SqliteConversationStore(storageContext)
        phase = "restore-completed"
        check(store.load(entry.id, owner)?.snapshot?.replay == chat.snapshot().replay)
        try { store.save(entry.copy(revision = 0)); error("stale write accepted") } catch (_: ConversationStorageException) { }
        check(store.load(entry.id, owner)?.revision == entry.revision)
        try { store.save(entry.copy(model = "wrong")); error("model rebound") } catch (_: ConversationStorageException) { }
        phase = "rename-delete"
        store.rename(entry.id, owner, entry.revision, "Renamed")
        entry = checkNotNull(store.load(entry.id, owner)); check(entry.title == "Renamed")
        store.delete(entry.id, owner, entry.revision)
        check(store.list(owner).isEmpty() && store.load(entry.id, owner) == null)
        try { store.save(entry.copy(revision = 0)); error("deleted chat resurrected") } catch (_: ConversationStorageException) { }
        store.close()
        val file = storageContext.getDatabasePath("conversations.db")
        phase = "future-version"
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 99 }
        store = SqliteConversationStore(storageContext)
        try { store.owner("a".repeat(64)); error("future schema accepted") } catch (_: ConversationStorageException) { }
        store.close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { check(it.version == 99) }
        storageContext.deleteDatabase("conversations.db")
        phase = "corruption"
        file.parentFile!!.mkdirs(); file.writeText("synthetic corrupt data")
        store = SqliteConversationStore(storageContext)
        try { store.owner("a".repeat(64)); error("corruption accepted") } catch (_: ConversationStorageException) { }
        store.close(); check(file.exists() && file.readText() == "synthetic corrupt data")
        storageContext.deleteDatabase("conversations.db")
    }
}
