package io.github.grassified.wristprompt.storage

import android.app.Activity
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteFullException
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
                    "limits" -> verifyLimits()
                    "interrupt-write" -> interruptWrite()
                    "verify-interrupted-write" -> verifyInterruptedWrite()
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
    /** Test-only access to the helper connection; no production fault-injection API. */
    private fun connection(store: SqliteConversationStore): SQLiteDatabase {
        val field = SqliteConversationStore::class.java.getDeclaredField("helper")
        field.isAccessible = true
        return (field.get(store) as SQLiteOpenHelper).writableDatabase
    }
    private fun sizedEntry(owner: String, bytes: Int): ConversationEntry {
        // Valid excluded display data; UTF-8 bytes, not character count, define the budget.
        fun candidate(tail: String) = ConversationEntry(owner = owner, route = "CHATGPT_PLAN",
            model = "synthetic", title = "Limit", updated = 1,
            snapshot = ConversationSnapshot(messages = listOf(StoredMessage(1, "USER", "EXCLUDED",
                List(8) { ContentPart.Text("é".repeat(65_000)) } + ContentPart.Text(tail))),
                replay = emptyList(), attempts = 1, nextId = 1, terminal = "CANCELLED"))
        val base = candidate("")
        val body = ConversationCodec.json.encodeToString(ConversationSnapshot.serializer(), base.snapshot)
        return candidate("x".repeat(bytes - body.encodeToByteArray().size - 2))
    }
    private suspend fun verifyLimits() {
        check(storageContext.isDeviceProtectedStorage)
        storageContext.deleteDatabase("conversations.db")
        val store = SqliteConversationStore(storageContext)
        try {
            phase = "owner-limit"
            val owners = (1..1000).map { store.owner(it.toString(16).padStart(64, '0')) }
            expectRejected { store.owner("f".repeat(64)) }
            check(store.owner("1".padStart(64, '0')) == owners.first())
            val owner = owners.first()
            phase = "entry-byte-limit"
            val exact = sizedEntry(owner, ConversationCodec.MAX_ENTRY_BYTES)
            check(ConversationCodec.encode(exact).let { it.first.encodeToByteArray().size + it.second.encodeToByteArray().size } == 1_048_576)
            val saved = store.save(exact)
            val oversized = sizedEntry(owner, ConversationCodec.MAX_ENTRY_BYTES + 1)
            expectRejected { store.save(oversized) }
            check(store.load(saved.id, owner) == saved)
            phase = "total-byte-limit"
            val entries = mutableListOf(saved)
            repeat(31) { entries += store.save(exact.copy(id = java.util.UUID.randomUUID().toString())) }
            expectRejected { store.save(exact.copy(id = java.util.UUID.randomUUID().toString())) }
            check(store.list(owner).size == 32)
            store.delete(entries.last().id, owner, entries.last().revision)
            val replacement = store.save(exact.copy(id = java.util.UUID.randomUUID().toString()))
            check(store.load(replacement.id, owner) == replacement)
            // Remove large content before exercising the independent UUID/tombstone budget.
            (entries.dropLast(1) + replacement).forEach { store.delete(it.id, owner, it.revision) }
            phase = "tombstone-limit"
            val db = connection(store)
            val small = exact.copy(id = java.util.UUID.randomUUID().toString(), snapshot = ChatConversation().snapshot())
            // Bulk fixture: valid minimal tombstones, same schema as production deletion.
            db.beginTransaction()
            try {
                db.compileStatement("INSERT INTO conversations VALUES(?,?,?,?,?,?,?,?,?,?,1)").use { statement ->
                    repeat(9_999 - 33) {
                        statement.clearBindings()
                        statement.bindString(1, java.util.UUID.randomUUID().toString()); statement.bindString(2, owner)
                        statement.bindString(3, "CHATGPT_PLAN"); statement.bindString(4, "synthetic")
                        statement.bindString(5, ""); statement.bindLong(6, 1); statement.bindLong(7, 1)
                        statement.bindString(8, ""); statement.bindString(9, ""); statement.bindLong(10, 0)
                        // Deleted is the literal final column.
                        statement.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            db.rawQuery("SELECT COUNT(*) FROM conversations", null).use { check(it.moveToFirst() && it.getInt(0) == 9_999) }
            val last = store.save(small)
            expectRejected { store.save(small.copy(id = java.util.UUID.randomUUID().toString())) }
            val changed = store.save(last.copy(title = "Update at UUID cap"))
            store.delete(changed.id, owner, changed.revision)
            expectRejected { store.save(last.copy(revision = 0)) }
            expectRejected { store.save(small.copy(id = java.util.UUID.randomUUID().toString())) }
            phase = "disk-full-recovery"
            // Separate fixture; SQLite's page cap produces real SQLITE_FULL without filling a device.
        } finally { store.close() }
        storageContext.deleteDatabase("conversations.db")
        val fullStore = SqliteConversationStore(storageContext)
        try {
            val owner = fullStore.owner("a".repeat(64))
            val baseline = fullStore.save(ConversationEntry(owner = owner, route = "CHATGPT_PLAN", model = "synthetic",
                title = "Before full", updated = 1, snapshot = ChatConversation().snapshot()))
            val db = connection(fullStore)
            db.execSQL("CREATE TABLE full_probe(payload BLOB)")
            val pages = db.rawQuery("PRAGMA page_count", null).use { it.moveToFirst(); it.getLong(0) }
            val pageSize = db.rawQuery("PRAGMA page_size", null).use { it.moveToFirst(); it.getLong(0) }
            db.setMaximumSize(pages * pageSize)
            try { db.execSQL("INSERT INTO full_probe VALUES(zeroblob(2097152))"); error("Expected SQLITE_FULL") }
            catch (_: SQLiteFullException) { }
            val large = sizedEntry(owner, ConversationCodec.MAX_ENTRY_BYTES).copy(id = baseline.id, revision = baseline.revision)
            expectRejected { fullStore.save(large) }
            check(fullStore.load(baseline.id, owner) == baseline)
            db.setMaximumSize(128L * 1024 * 1024)
            val recovered = fullStore.save(large)
            check(recovered.revision == baseline.revision + 1 && fullStore.load(recovered.id, owner) == recovered)
        } finally { fullStore.close() }
        storageContext.deleteDatabase("conversations.db")
    }
    private suspend fun interruptWrite() {
        seedRestart()
        val store = SqliteConversationStore(storageContext)
        val owner = store.owner("c".repeat(64))
        val target = store.list(owner).single { it.title == "Completed" }
        connection(store).beginTransaction()
        connection(store).execSQL("UPDATE conversations SET display=?,replay=?,revision=revision+1 WHERE id=?",
            arrayOf("uncommitted display", "uncommitted replay", target.id))
        // Force dirty pages into the rollback journal before external SIGKILL.
        connection(store).execSQL("CREATE TABLE interrupted_probe(payload BLOB)")
        connection(store).execSQL("INSERT INTO interrupted_probe VALUES(zeroblob(4194304))")
        check(java.io.File(storageContext.getDatabasePath("conversations.db").path + "-journal").length() > 0)
        sendStatus(0, Bundle().apply { putString("stream", "READY: uncommitted synthetic transaction\n") })
        java.util.concurrent.CountDownLatch(1).await() // Host must kill this emulator process.
    }
    private suspend fun verifyInterruptedWrite() {
        verifyRestart()
        val store = SqliteConversationStore(storageContext)
        try {
            val owner = store.owner("c".repeat(64))
            check(store.list(owner).all { it.revision == 1L })
        } finally { store.close() }
        SQLiteDatabase.openDatabase(storageContext.getDatabasePath("conversations.db").path, null,
            SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE name='interrupted_probe'", null).use { check(!it.moveToFirst()) }
            db.rawQuery("PRAGMA integrity_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
        }
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
