package io.github.grassified.wristprompt.storage

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.runBlocking

/** Standalone synthetic storage gate; no credentials, network or owner-app data. */
class ConversationStorageInstrumentation : Instrumentation() {
    private var phase = "context"
    // Instrumentation executes with target UID; use its separate device-protected
    // namespace, never the production credential-protected conversation database.
    private val storageContext get() = targetContext.createDeviceProtectedStorageContext()
    private var checkpoint: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments); checkpoint = arguments?.getString("checkpoint"); start()
    }
    override fun onStart() {
        val outcome = Bundle()
        try {
            check(context.packageName.endsWith(".test"))
            runBlocking {
                when (checkpoint) {
                    null -> verify()
                    "seed" -> seedRestart()
                    "verify" -> verifyRestart()
                    else -> error("Unknown checkpoint mode")
                }
            }
            outcome.putString("stream", if (checkpoint != null) "PASS: restart checkpoint $checkpoint\n" else "PASS: SQLite durable lifecycle, rollback, owner isolation, deletion, limits, failed-write rollback, record recovery, future schema, corruption preservation\n")
            finish(Activity.RESULT_OK, outcome)
        } catch (failure: Exception) {
            outcome.putString("stream", "FAIL: $phase (${failure.javaClass.simpleName}); payloads redacted\n")
            finish(Activity.RESULT_CANCELED, outcome)
        }
    }
    private suspend fun seedRestart() {
        check(storageContext.isDeviceProtectedStorage)
        storageContext.deleteDatabase("conversations.db")
        val store = SqliteConversationStore(storageContext)
        try {
            val owner = store.owner("c".repeat(64))
            val chat = ChatConversation().apply {
                draft("durable question"); begin()
                complete(StreamResult().apply { accept(ResponseEvent.Completed("durable answer")); end() })
            }
            store.save(ConversationEntry(owner = owner, route = "CHATGPT_PLAN", model = "synthetic",
                title = "Completed", updated = 1, snapshot = chat.snapshot()))
            chat.draft("interrupted question"); chat.begin()
            store.save(ConversationEntry(owner = owner, route = "CHATGPT_PLAN", model = "synthetic",
                title = "Interrupted", updated = 2, snapshot = chat.snapshot()))
            chat.progress("excluded partial"); chat.fail(null, true)
            store.save(ConversationEntry(owner = owner, route = "CHATGPT_PLAN", model = "synthetic",
                title = "Cancelled", updated = 3, snapshot = chat.snapshot()))
        } finally { store.close() }
    }
    private suspend fun verifyRestart() {
        val store = SqliteConversationStore(storageContext)
        try {
            val owner = store.owner("c".repeat(64))
            val entries = store.list(owner)
            check(entries.size == 3)
            for (summary in entries) {
                val saved = checkNotNull(store.load(summary.id, owner))
                val chat = ChatConversation().apply { restore(saved.snapshot) }
                check(!chat.state.busy && chat.state.draft.isEmpty())
                check(chat.state.messages.take(2).map { it.text } == listOf("durable question", "durable answer"))
                if (summary.title != "Completed") check(chat.state.messages.last().status == ChatMessageStatus.EXCLUDED)
                if (summary.title == "Cancelled") check(chat.state.partial == "excluded partial")
                chat.draft("explicit follow up")
                check(chat.begin()!!.size == 3) // No interrupted/cancelled request enters replay.
            }
        } finally { store.close() }
    }
    private suspend fun expectRejected(action: suspend () -> Any?) {
        try { action() } catch (_: ConversationStorageException) { return }
        error("Invalid storage operation accepted")
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
        phase = "billing-and-owner-write-boundaries"
        expectRejected { store.save(entry.copy(route = "OPENAI_API_KEY")) }
        expectRejected { store.save(entry.copy(owner = other)) }
        expectRejected { store.rename(entry.id, other, entry.revision, "Wrong owner") }
        expectRejected { store.delete(entry.id, other, entry.revision) }
        check(store.load(entry.id, owner) == entry)
        phase = "rename-delete"
        store.rename(entry.id, owner, entry.revision, "Renamed")
        entry = checkNotNull(store.load(entry.id, owner)); check(entry.title == "Renamed")
        store.delete(entry.id, owner, entry.revision)
        check(store.list(owner).isEmpty() && store.load(entry.id, owner) == null)
        try { store.save(entry.copy(revision = 0)); error("deleted chat resurrected") } catch (_: ConversationStorageException) { }
        store.close()
        val file = storageContext.getDatabasePath("conversations.db")
        phase = "deletion-content-cleanup"
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT display,replay,bytes,deleted FROM conversations WHERE id=?", arrayOf(entry.id)).use {
                check(it.moveToFirst() && it.getString(0).isEmpty() && it.getString(1).isEmpty() && it.getLong(2) == 0L && it.getInt(3) == 1)
            }
        }
        store = SqliteConversationStore(storageContext)
        phase = "live-limit-and-recovery"
        val template = entry.copy(id = java.util.UUID.randomUUID().toString(), revision = 0)
        val live = (1..100).map { store.save(template.copy(id = java.util.UUID.randomUUID().toString(), updated = it.toLong())) }
        expectRejected { store.save(template) }
        check(store.list(owner).size == 100)
        // Replacing an existing row at capacity is valid; failed inserts must not evict.
        val updated = store.save(live.first().copy(title = "Updated at capacity"))
        store.delete(live.last().id, owner, live.last().revision)
        val recovered = store.save(template)
        check(store.list(owner).size == 100 && store.load(updated.id, owner) == updated)
        store.close()
        phase = "corrupt-record-preservation"
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE conversations SET display=? WHERE id=?", arrayOf("{broken", recovered.id))
        }
        store = SqliteConversationStore(storageContext)
        expectRejected { store.load(recovered.id, owner) }
        check(store.load(updated.id, owner) == updated)
        store.close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT display FROM conversations WHERE id=?", arrayOf(recovered.id)).use {
                check(it.moveToFirst() && it.getString(0) == "{broken")
            }
        }
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
