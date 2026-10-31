package io.github.grassified.wristprompt.storage

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** No destructive fallback on corruption, failed migration, full disk or future schema. */
class SqliteConversationStore(context: Context) : ConversationStore {
    private val mutex = Mutex()
    // Retain the supplied storage namespace (instrumentation has its own private app).
    // Production injection supplies the Application, never an Activity.
    private val helper = Database(context)
    suspend fun close() = withContext(Dispatchers.IO) { mutex.withLock { helper.close() } }
    private suspend fun <T> access(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block(helper.writableDatabase) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw ConversationStorageException() }
        }
    }
    private fun <T> transaction(db: SQLiteDatabase, block: () -> T): T {
        db.beginTransaction()
        try { val result = block(); db.setTransactionSuccessful(); return result }
        finally { db.endTransaction() }
    }
    override suspend fun owner(identityHash: String): String = access { db ->
        require(identityHash.matches(Regex("[a-f0-9]{64}")))
        transaction(db) {
            db.rawQuery("SELECT scope FROM owners WHERE identity_hash=?", arrayOf(identityHash)).use {
                if (it.moveToFirst()) it.getString(0) else {
                    db.rawQuery("SELECT COUNT(*) FROM owners", null).use { count -> count.moveToFirst(); require(count.getInt(0) < 1_000) }
                    val scope = UUID.randomUUID().toString()
                    db.insertOrThrow("owners", null, ContentValues().apply { put("identity_hash", identityHash); put("scope", scope) })
                    scope
                }
            }
        }
    }
    override suspend fun save(entry: ConversationEntry): ConversationEntry = access { db ->
        val (body, replay) = ConversationCodec.encode(entry)
        transaction(db) {
            val existing = db.rawQuery("SELECT owner,revision,deleted,route,model FROM conversations WHERE id=?", arrayOf(entry.id)).use {
                if (!it.moveToFirst()) false else {
                    require(it.getString(0) == entry.owner && it.getLong(1) == entry.revision && it.getInt(2) == 0)
                    require(it.getString(3) == entry.route && it.getString(4) == entry.model); true
                }
            }
            require(existing || entry.revision == 0L)
            if (!existing) db.rawQuery("SELECT COUNT(*) FROM conversations", null).use { it.moveToFirst(); require(it.getInt(0) < 10_000) }
            val bytes = body.encodeToByteArray().size + replay.encodeToByteArray().size
            val used = db.rawQuery("SELECT COALESCE(SUM(bytes),0),COUNT(*) FROM conversations WHERE deleted=0 AND id<>?", arrayOf(entry.id)).use {
                it.moveToFirst(); it.getLong(0) to it.getInt(1)
            }
            require(used.first + bytes <= 32L * 1024 * 1024 && used.second < 100)
            val saved = entry.copy(revision = Math.addExact(entry.revision, 1))
            val values = ContentValues().apply {
                put("id", entry.id); put("owner", entry.owner); put("route", entry.route); put("model", entry.model)
                put("title", entry.title); put("updated", entry.updated); put("revision", saved.revision)
                put("display", body); put("replay", replay); put("bytes", bytes); put("deleted", 0)
            }
            if (existing) check(db.update("conversations", values, "id=? AND revision=? AND deleted=0",
                arrayOf(entry.id, entry.revision.toString())) == 1)
            else db.insertOrThrow("conversations", null, values)
            saved
        }
    }
    override suspend fun load(id: String, owner: String): ConversationEntry? = access { db ->
        db.rawQuery("SELECT length(CAST(display AS BLOB))+length(CAST(replay AS BLOB)) FROM conversations WHERE id=? AND owner=? AND deleted=0",
            arrayOf(id, owner)).use { if (it.moveToFirst()) require(it.getLong(0) <= ConversationCodec.MAX_ENTRY_BYTES) }
        db.rawQuery("SELECT route,model,title,updated,revision,display,replay FROM conversations WHERE id=? AND owner=? AND deleted=0",
            arrayOf(id, owner)).use { c ->
            if (!c.moveToFirst()) null else ConversationEntry(id, owner, c.getString(0), c.getString(1), c.getString(2),
                c.getLong(3), c.getLong(4), ConversationCodec.decode(c.getString(5), c.getString(6))).also(ConversationCodec::validate)
        }
    }
    override suspend fun list(owner: String): List<ConversationSummary> = access { db ->
        db.rawQuery("SELECT id,title,model,route,updated,revision FROM conversations WHERE owner=? AND deleted=0 ORDER BY updated DESC,id LIMIT 100",
            arrayOf(owner)).use { c -> buildList {
                while (c.moveToNext()) add(ConversationSummary(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getLong(5)))
            } }
    }
    override suspend fun delete(id: String, owner: String, revision: Long) = access { db ->
        // Small tombstone prevents a stale completion from recreating a deleted UUID.
        val values = ContentValues().apply { put("deleted", 1); put("display", ""); put("replay", ""); put("bytes", 0); put("title", "") }
        check(db.update("conversations", values, "id=? AND owner=? AND revision=? AND deleted=0", arrayOf(id, owner, revision.toString())) == 1)
        Unit
    }
    override suspend fun rename(id: String, owner: String, revision: Long, title: String) = access { db ->
        require(title.isNotBlank() && title.length <= 120)
        val values = ContentValues().apply { put("title", title); put("revision", Math.addExact(revision, 1)) }
        check(db.update("conversations", values, "id=? AND owner=? AND revision=? AND deleted=0", arrayOf(id, owner, revision.toString())) == 1)
        Unit
    }
    private class Database(context: Context) : SQLiteOpenHelper(context, "conversations.db", null, 1,
        DatabaseErrorHandler { throw ConversationStorageException() }) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE owners(identity_hash TEXT PRIMARY KEY, scope TEXT NOT NULL UNIQUE)")
            db.execSQL("CREATE TABLE conversations(id TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES owners(scope),route TEXT NOT NULL,model TEXT NOT NULL,title TEXT NOT NULL,updated INTEGER NOT NULL,revision INTEGER NOT NULL,display TEXT NOT NULL,replay TEXT NOT NULL,bytes INTEGER NOT NULL,deleted INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX conversation_history ON conversations(owner,deleted,updated DESC,id)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { throw ConversationStorageException() }
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { throw ConversationStorageException() }
    }
}
