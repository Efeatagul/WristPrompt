package io.github.grassified.wristprompt.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class PhoneKeyStage { WAITING, READY, SENDING, RETRY, ACKNOWLEDGED, EXPIRED, FORMAT_ERROR }
class PhoneApiKeyViewModel(private val inbox: ApiKeyInbox, private val send: suspend (String, String, ByteArray) -> ByteArray,
    private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val mutableKey = MutableStateFlow("")
    val key = mutableKey.asStateFlow()
    private val mutableStage = MutableStateFlow(PhoneKeyStage.WAITING)
    val stage = mutableStage.asStateFlow()
    private var envelope: ByteArray? = null
    private var transferId: String? = null
    private var operation: Job? = null
    init { viewModelScope.launch {
        inbox.state.collectLatest { context ->
            clearSecrets(); mutableStage.value = if (context == null) PhoneKeyStage.WAITING else PhoneKeyStage.READY
            if (context != null) {
                delay((context.request.createdAtEpochMs + BridgeProtocol.REQUEST_LIFETIME_MS - now()).coerceAtLeast(1))
                clearSecrets(); mutableStage.value = PhoneKeyStage.EXPIRED
            }
        }
    } }
    fun edit(value: String) {
        if (stage.value in setOf(PhoneKeyStage.READY, PhoneKeyStage.FORMAT_ERROR)) {
            mutableKey.value = value.take(8193); mutableStage.value = PhoneKeyStage.READY
        }
    }
    fun transfer() {
        if (operation?.isActive == true || stage.value !in setOf(PhoneKeyStage.READY, PhoneKeyStage.RETRY, PhoneKeyStage.FORMAT_ERROR)) return
        val context = inbox.state.value ?: return
        if (!context.request.isFresh(now())) { clearSecrets(); mutableStage.value = PhoneKeyStage.EXPIRED; return }
        if (envelope == null) {
            if (!ApiKeyProtocol.sane(key.value)) { mutableStage.value = PhoneKeyStage.FORMAT_ERROR; return }
            transferId = BridgeProtocol.newRequestId()
            val bytes = ProfileProtocol.encode(ApiKeyTransfer(1, context.request.requestId, transferId!!, context.request.hostId, key.value))
            if (bytes.size > ApiKeyProtocol.MAX_BYTES) { bytes.fill(0); transferId = null; mutableStage.value = PhoneKeyStage.FORMAT_ERROR; return }
            envelope = bytes
            mutableKey.value = ""
        }
        mutableStage.value = PhoneKeyStage.SENDING
        operation = viewModelScope.launch {
            try {
                val response = withTimeout(BridgeProtocol.TIMEOUT_MS) { send(context.node, ApiKeyProtocol.TRANSFER_PATH, envelope!!) }
                currentCoroutineContext().ensureActive()
                if (inbox.state.value != context || !context.request.isFresh(now())) {
                    clearSecrets(); mutableStage.value = PhoneKeyStage.EXPIRED; return@launch
                }
                ApiKeyProtocol.ack(response, context.request.requestId, transferId!!)
                envelope?.fill(0); envelope = null; transferId = null; mutableKey.value = ""
                mutableStage.value = PhoneKeyStage.ACKNOWLEDGED
            } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); mutableStage.value = PhoneKeyStage.RETRY }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); mutableStage.value = PhoneKeyStage.RETRY }
        }
    }
    private fun clearSecrets() { operation?.cancel(); operation = null; envelope?.fill(0); envelope = null; transferId = null; mutableKey.value = "" }
    override fun onCleared() { clearSecrets() }
}
