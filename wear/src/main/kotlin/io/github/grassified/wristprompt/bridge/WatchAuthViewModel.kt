package io.github.grassified.wristprompt.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class WatchAuthState { IDLE, LOOKING_FOR_PHONE, REQUEST_SENT, COMPANION_UNAVAILABLE, ERROR, MULTIPLE_PHONES }

class WatchAuthViewModel(private val bridge: WatchAuthBridge, private val profiles: io.github.grassified.wristprompt.auth.WatchProfiles) : ViewModel() {
    private val mutableState = MutableStateFlow(WatchAuthState.IDLE)
    val state = mutableState.asStateFlow()
    val accounts = profiles.state
    init {
        viewModelScope.launch { profiles.load() }
        viewModelScope.launch {
            accounts.collectLatest { value ->
                value.pendingExpiresAt?.let { expiry ->
                    if (!value.expired) { delay((expiry - System.currentTimeMillis() + 1).coerceAtLeast(1)); profiles.load() }
                }
            }
        }
    }
    fun confirmPlanNotice() {
        viewModelScope.launch {
            try { profiles.confirmPlanNotice() }
            catch (_: Exception) { mutableState.value = WatchAuthState.ERROR }
        }
    }
    fun select(clientId: String?) {
        viewModelScope.launch {
            try { profiles.select(clientId); mutableState.value = WatchAuthState.IDLE }
            catch (_: Exception) { mutableState.value = WatchAuthState.ERROR }
        }
    }
    private var requestJob: Job? = null

    fun start() {
        if (requestJob?.isActive == true) return
        mutableState.value = WatchAuthState.LOOKING_FOR_PHONE
        requestJob = viewModelScope.launch {
            mutableState.value = try {
                if (bridge.request()) WatchAuthState.REQUEST_SENT else WatchAuthState.COMPANION_UNAVAILABLE
            } catch (_: TimeoutCancellationException) {
                WatchAuthState.ERROR
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AmbiguousCompanionException) {
                WatchAuthState.MULTIPLE_PHONES
            } catch (_: Exception) {
                BridgeLog.warn(BridgeDiagnostic.CONNECTION_FAILED)
                WatchAuthState.ERROR
            }
        }
    }
}
