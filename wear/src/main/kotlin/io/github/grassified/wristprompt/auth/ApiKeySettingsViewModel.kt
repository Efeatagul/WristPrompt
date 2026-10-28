package io.github.grassified.wristprompt.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.openai.InferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class KeySetupStatus { IDLE, REQUESTING, PHONE_READY, UNAVAILABLE, ERROR }
class ApiKeySettingsViewModel(private val keys: ApiKeyRepository, private val bridge: ApiKeySetupBridge, private val inference: InferenceSession) : ViewModel() {
    val state = keys.state
    private val mutableSetup = MutableStateFlow(KeySetupStatus.IDLE)
    val setup = mutableSetup.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    init { viewModelScope.launch { keys.load() } }
    fun selectMode(mode: CredentialMode) {
        if (busy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
        inference.cancelAll()
        try { keys.selectMode(mode) } catch (_: Exception) { mutableSetup.value = KeySetupStatus.ERROR }
        finally { mutableBusy.value = false }
    } }
    fun remove() {
        if (busy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
        inference.cancel(ApiKeyRepository.API_CLIENT)
        try { keys.remove() } catch (_: Exception) { mutableSetup.value = KeySetupStatus.ERROR }
        finally { mutableBusy.value = false }
    } }
    fun request() {
        if (setup.value == KeySetupStatus.REQUESTING) return
        mutableSetup.value = KeySetupStatus.REQUESTING
        viewModelScope.launch {
            mutableSetup.value = try { if (bridge.request()) KeySetupStatus.PHONE_READY else KeySetupStatus.UNAVAILABLE }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { KeySetupStatus.ERROR }
        }
    }
}
