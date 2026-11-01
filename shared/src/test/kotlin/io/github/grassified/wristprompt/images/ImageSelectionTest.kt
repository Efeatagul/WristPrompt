package io.github.grassified.wristprompt.images

import org.junit.Assert.*
import org.junit.Test

class ImageSelectionTest {
    @Test fun zeroOneMultipleDeselectCancelAndDeletion() {
        val state = ImageSelection()
        val first = ImageLimits.newId(); val second = ImageLimits.newId()
        val live = setOf(first, second)
        assertTrue(state.selected.value.isEmpty())
        assertTrue(state.replace(emptyList(), listOf(first), live))
        // Editing a separate picker list and cancelling never mutates the owner.
        val pending = state.selected.value + second
        assertEquals(listOf(first), state.selected.value)
        assertTrue(state.replace(listOf(first), pending, live))
        assertTrue(state.replace(pending, listOf(second), live))
        state.prune(setOf(first))
        assertTrue(state.selected.value.isEmpty())
        assertFalse(state.replace(emptyList(), listOf(second), setOf(first)))
        assertTrue(state.selected.value.isEmpty())
    }
    @Test fun boundedUniqueAndStaleSelection() {
        val state = ImageSelection()
        val ids = List(7) { ImageLimits.newId() }
        assertFalse(state.replace(emptyList(), ids, ids.toSet()))
        assertFalse(state.replace(emptyList(), listOf(ids[0], ids[0]), ids.toSet()))
        assertTrue(state.replace(emptyList(), ids.take(6), ids.toSet()))
        assertFalse(state.replace(emptyList(), emptyList(), ids.toSet()))
        state.clear()
        assertTrue(state.selected.value.isEmpty())
    }
}
