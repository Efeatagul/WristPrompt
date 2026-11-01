package io.github.grassified.wristprompt.presets

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// A phone-authoritative catalog. Deletions retain only identity/revision, never text.
@Serializable
data class PromptPreset(val id: String, val revision: Long, val name: String = "", val text: String = "", val deleted: Boolean = false) {
    override fun toString() = "PromptPreset([redacted])"
}

@Serializable
data class PresetCatalog(val version: Int = 1, val records: List<PromptPreset> = emptyList()) {
    override fun toString() = "PresetCatalog([redacted])"
}

enum class PresetProblem {
    NAME, TEXT, COUNT, STORAGE, INVALID_DATA, CONFLICT, REVISION_EXHAUSTED
}
class PresetException(val problem: PresetProblem) : IllegalArgumentException("Preset operation failed: ${problem.name}")

object PresetCodec {
    const val VERSION = 1
    const val MAX_NAME = 80
    const val MAX_TEXT = 8192
    const val MAX_LIVE = 50
    const val MAX_RECORDS = 256
    const val MAX_BYTES = 524288
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val json = Json { encodeDefaults = true }

    fun newId(): String = UUID.randomUUID().toString()

    fun validateContent(name: String, text: String) {
        if (name.isBlank() || name.length > MAX_NAME || name != name.trim() || !validUnicode(name) || name.any { it.isISOControl() }) fail(PresetProblem.NAME)
        if (text.isBlank() || text.length > MAX_TEXT || !validUnicode(text) || text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) fail(PresetProblem.TEXT)
    }

    private fun validUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char.isHighSurrogate()) {
                if (index == value.length || !value[index++].isLowSurrogate()) return false
            } else if (char.isLowSurrogate()) return false
        }
        return true
    }

    private fun validate(catalog: PresetCatalog) {
        if (catalog.version != VERSION || catalog.records.map { it.id }.distinct().size != catalog.records.size) fail(PresetProblem.INVALID_DATA)
        if (catalog.records.size > MAX_RECORDS || catalog.records.count { !it.deleted } > MAX_LIVE) fail(PresetProblem.COUNT)
        catalog.records.forEach {
            if (!uuid.matches(it.id) || it.revision <= 0) fail(PresetProblem.INVALID_DATA)
            if (it.deleted) {
                if (it.name.isNotEmpty() || it.text.isNotEmpty()) fail(PresetProblem.INVALID_DATA)
            } else validateContent(it.name, it.text)
        }
    }

    fun encode(catalog: PresetCatalog): String {
        validate(catalog)
        return json.encodeToString(catalog).also { if (it.toByteArray(Charsets.UTF_8).size > MAX_BYTES) fail(PresetProblem.STORAGE) }
    }

    fun decode(value: String): PresetCatalog {
        if (value.length > MAX_BYTES || value.toByteArray(Charsets.UTF_8).size > MAX_BYTES) fail(PresetProblem.STORAGE)
        val catalog = try { json.decodeFromString<PresetCatalog>(value) } catch (_: Exception) { fail(PresetProblem.INVALID_DATA) }
        validate(catalog)
        // Reject unknown/duplicate fields, omitted defaults and ambiguous encodings.
        if (encode(catalog) != value) fail(PresetProblem.INVALID_DATA)
        return catalog
    }

    // Reference merge semantics for future sync; no transport or receiver is wired here.
    // Equal revisions are idempotent only for identical data. Tombstones are terminal.
    fun merge(current: PresetCatalog, incoming: PresetCatalog): PresetCatalog {
        encode(current)
        encode(incoming)
        val result = current.records.associateBy { it.id }.toMutableMap()
        incoming.records.forEach { next ->
            val old = result[next.id]
            when {
                old == null -> result[next.id] = next
                next.revision < old.revision -> Unit
                next.revision == old.revision -> if (next != old) fail(PresetProblem.CONFLICT)
                old.deleted && !next.deleted -> fail(PresetProblem.CONFLICT)
                else -> result[next.id] = next
            }
        }
        return PresetCatalog(records = result.values.toList()).also { encode(it) }
    }

    fun change(current: PresetCatalog, id: String?, expectedRevision: Long?, name: String = "", text: String = "", delete: Boolean = false): PresetCatalog {
        encode(current)
        val old = current.records.find { it.id == id }
        if (id != null && (old == null || old.deleted || old.revision != expectedRevision)) fail(PresetProblem.CONFLICT)
        if (id == null && (delete || expectedRevision != null)) fail(PresetProblem.CONFLICT)
        if (!delete) validateContent(name, text)
        if (old?.revision == Long.MAX_VALUE) fail(PresetProblem.REVISION_EXHAUSTED)
        val next = PromptPreset(id ?: newId(), (old?.revision ?: 0) + 1, if (delete) "" else name, if (delete) "" else text, delete)
        return PresetCatalog(records = current.records.filterNot { it.id == next.id } + next).also { encode(it) }
    }

    private fun fail(problem: PresetProblem): Nothing = throw PresetException(problem)
}
