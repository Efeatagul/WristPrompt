package io.github.grassified.wristprompt.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/** Retained UI state only: survives Activity recreation, never restored to a fresh process. */
internal class ShellNavigationViewModel(initialSurface: Surface = Surface.CHAT) : ViewModel() {
    val surface = mutableStateOf(initialSurface)
    val inputPending = mutableStateOf(false)
}
