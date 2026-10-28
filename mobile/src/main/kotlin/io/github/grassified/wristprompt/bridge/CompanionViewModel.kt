package io.github.grassified.wristprompt.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class CompanionState { WAITING, REQUEST_RECEIVED, STORAGE_ERROR }

class CompanionViewModel(inbox: AuthRequestInbox) : ViewModel() {
    val state = inbox.received
        .map { if (it == null) CompanionState.WAITING else CompanionState.REQUEST_RECEIVED }
        .catch {
            BridgeLog.warn(BridgeDiagnostic.STORAGE_UNAVAILABLE)
            emit(CompanionState.STORAGE_ERROR)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanionState.WAITING)
}
