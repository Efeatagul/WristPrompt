package io.github.grassified.wristprompt.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

internal enum class Surface { CHAT, COMPOSER, MENU, HISTORY, MODELS, CONNECTION, SETTINGS, DETAILS, PROOF, OUTPUT }

/** Retained UI state only; no saved-state restoration into a fresh process. */
internal class ShellNavigationViewModel(initialSurface: Surface = Surface.CHAT) : ViewModel() {
    val surface = mutableStateOf(initialSurface)
    var chatPosition: ChatReadingPosition? = null
    val latestVisible = mutableStateOf(false)
    val latestRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun latest() { latestRequests.tryEmit(Unit) }
    val inputPending = mutableStateOf(false)
    private var welcomePosition: ReadingPosition? = null
    private val parents = mutableListOf<Surface>()
    private val positions = mutableMapOf<Surface, ReadingPosition>()

    fun open(destination: Surface) {
        // Proof output is part of proof diagnostics, never a separate top-level route.
        val target = if (destination == Surface.OUTPUT) Surface.PROOF else destination
        if (target == surface.value) return
        if (target == Surface.CHAT) { parents.clear(); surface.value = target; return }
        val previous = parents.indexOf(target)
        if (previous >= 0) {
            while (parents.size > previous) parents.removeAt(parents.lastIndex)
        } else {
            parents.add(surface.value)
        }
        surface.value = target
    }

    fun back() {
        surface.value = if (parents.isEmpty()) Surface.CHAT else parents.removeAt(parents.lastIndex)
    }

    // Store primitives, not lazy-layout objects which may still reference a disposed UI.
    fun position(surface: Surface, welcome: Boolean = false) = if (welcome) welcomePosition else positions[surface]
    fun rememberPosition(surface: Surface, index: Int, offset: Int, welcome: Boolean = false) {
        if (index < 0) return // No measured position yet; retain the library's first-layout policy.
        val position = ReadingPosition(index, offset)
        if (welcome) welcomePosition = position else positions[surface] = position
    }
}

internal data class ReadingPosition(val index: Int, val offset: Int)
