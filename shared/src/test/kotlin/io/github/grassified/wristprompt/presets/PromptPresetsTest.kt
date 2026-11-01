package io.github.grassified.wristprompt.presets

import org.junit.Assert.*
import org.junit.Test

class PromptPresetsTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private fun record(revision: Long = 1, name: String = "Synthetic", text: String = "Line one\nLine two\t☃") = PromptPreset(id, revision, name, text)
    private fun catalog(record: PromptPreset = record()) = PresetCatalog(records = listOf(record))
    private fun fails(problem: PresetProblem, block: () -> Unit) {
        try { block(); fail("Expected sanitized failure") } catch (failure: PresetException) {
            assertEquals(problem, failure.problem)
            assertNull(failure.cause)
            assertFalse(failure.toString().contains("private-synthetic"))
        }
    }
    @Test fun exactSyncRoundTripAndRedaction() {
        val input = catalog(record(text = "private-synthetic\n\"quoted\"\\\t😀"))
        assertEquals(input, PresetCodec.decode(PresetCodec.encode(input)))
        assertFalse(input.toString().contains("private-synthetic"))
        assertFalse(input.records.first().toString().contains("Synthetic"))
        assertFalse(input.records.first().toString().contains(id))
    }
    @Test fun stableIdentityAndExpectedRevisionEdits() {
        val original = PresetCodec.change(PresetCatalog(), null, null, "Synthetic", "First")
        val first = original.records.single()
        val edited = PresetCodec.change(original, first.id, 1, "Renamed", "Second").records.single()
        assertEquals(first.id, edited.id)
        assertEquals(2L, edited.revision)
        fails(PresetProblem.CONFLICT) { PresetCodec.change(catalog(edited), edited.id, 1, "Old", "Old") }
        fails(PresetProblem.REVISION_EXHAUSTED) { PresetCodec.change(catalog(record(Long.MAX_VALUE)), id, Long.MAX_VALUE, "New", "Text") }
    }
    @Test fun staleReplayAndDeletionNeverResurrect() {
        val original = catalog()
        val updated = catalog(record(2, text = "Updated"))
        assertEquals(updated, PresetCodec.merge(updated, original))
        assertEquals(updated, PresetCodec.merge(original, updated))
        assertEquals(updated, PresetCodec.merge(updated, updated))
        fails(PresetProblem.CONFLICT) { PresetCodec.merge(updated, catalog(record(2, text = "Conflicting"))) }
        val deleted = PresetCodec.change(updated, id, 2, delete = true)
        assertTrue(deleted.records.single().deleted)
        assertEquals("", deleted.records.single().text)
        assertEquals(deleted, PresetCodec.decode(PresetCodec.encode(deleted)))
        assertEquals(deleted, PresetCodec.merge(deleted, updated))
        fails(PresetProblem.CONFLICT) { PresetCodec.merge(deleted, catalog(record(4))) }
        fails(PresetProblem.CONFLICT) { PresetCodec.change(deleted, id, 3, "Again", "Again") }
        fails(PresetProblem.CONFLICT) { PresetCodec.change(deleted, id, 3, delete = true) }
    }
    @Test fun contentLimitsAndUnicode() {
        PresetCodec.validateContent("N".repeat(80), "T".repeat(8192))
        listOf("", " ", " leading", "trailing ", "x\ny", "N".repeat(81), "\uD800").forEach {
            fails(PresetProblem.NAME) { PresetCodec.validateContent(it, "Text") }
        }
        listOf("", " \n\t", "T".repeat(8193), "private-synthetic\u0000", "\uDC00").forEach {
            fails(PresetProblem.TEXT) { PresetCodec.validateContent("Name", it) }
        }
    }
    @Test fun countAndStorageBounds() {
        fun records(count: Int, deleted: Boolean = false, text: String = "T") = (1..count).map {
            PromptPreset("00000000-0000-4000-8000-${it.toString().padStart(12, '0')}", 1, if (deleted) "" else "Name", if (deleted) "" else text, deleted)
        }
        PresetCodec.encode(PresetCatalog(records = records(50)))
        fails(PresetProblem.COUNT) { PresetCodec.encode(PresetCatalog(records = records(51))) }
        PresetCodec.encode(PresetCatalog(records = records(256, true)))
        fails(PresetProblem.COUNT) { PresetCodec.encode(PresetCatalog(records = records(257, true))) }
        fails(PresetProblem.STORAGE) { PresetCodec.encode(PresetCatalog(records = records(50, text = "☃".repeat(8192)))) }
        fails(PresetProblem.STORAGE) { PresetCodec.decode("x".repeat(PresetCodec.MAX_BYTES + 1)) }
    }
    @Test fun invalidSerializationFailsClosed() {
        val valid = PresetCodec.encode(catalog())
        listOf("{private-synthetic", valid.replace("\"version\":1", "\"version\":2"),
            valid.replace(id, "invalid"), valid.replace("\"revision\":1", "\"revision\":0"),
            valid.replace("\"version\":1", "\"version\":1,\"version\":1"),
            valid.replace("\"version\":1", "\"version\":1,\"extra\":true"),
            valid.replace("\"deleted\":false", "\"deleted\":true")).forEach {
            fails(PresetProblem.INVALID_DATA) { PresetCodec.decode(it) }
        }
        fails(PresetProblem.INVALID_DATA) { PresetCodec.encode(PresetCatalog(records = listOf(record(), record()))) }
    }
}
