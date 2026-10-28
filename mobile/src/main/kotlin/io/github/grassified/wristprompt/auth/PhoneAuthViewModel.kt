package io.github.grassified.wristprompt.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.bridge.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class PhoneStage { WAITING, READY, AUTHORIZING, TRANSFERRING, RETRY_TRANSFER, CONNECTED, IDENTITY_ONLY, FAILED }
data class PhoneAuthState(val stage: PhoneStage = PhoneStage.WAITING, val problem: AuthProblem? = null)
private class PendingTransfer(val nodeId: String, val envelope: ProfileTransfer, val expiresAt: Long) {
    override fun toString() = SafeDiagnostics.REDACTED
}

class PhoneAuthViewModel(
    private val inbox: AuthRequestInbox,
    private val rpc: suspend (String, String, ByteArray) -> ByteArray,
    private val registrations: IssuedRegistrationStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val exchange: suspend (OAuthTransaction, AuthorizationCode) -> CredentialProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhoneAuthState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var expiryJob: Job? = null
    private var pending: PendingTransfer? = null
    private var currentRequestId: String? = null
    init {
        viewModelScope.launch {
            try {
                inbox.received.collect { received ->
                    if (received != null && received.request.requestId != currentRequestId) {
                        job?.cancel()
                        currentRequestId = received.request.requestId
                        pending = null
                        expiryJob?.cancel()
                        mutableState.value = PhoneAuthState(PhoneStage.READY)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = PhoneAuthState(PhoneStage.FAILED, AuthProblem.STORAGE) }
        }
    }
    fun start(openBrowser: (String) -> Unit) {
        if (job?.isActive == true) return
        pending = null
        expiryJob?.cancel()
        job = viewModelScope.launch {
            var received: ReceivedAuthRequest? = null
            try {
                received = inbox.received.first() ?: throw AuthException(AuthProblem.WATCH_UNAVAILABLE)
                currentRequestId = received.request.requestId
                val request = ContextRequest(1, received.request.requestId, received.request.hostId)
                mutableState.value = PhoneAuthState(PhoneStage.AUTHORIZING)
                val context = try {
                    ProfileProtocol.decode<LoginContext>(send(received.nodeId, ProfileProtocol.CONTEXT_PATH, ProfileProtocol.encode(request)))
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { throw AuthException(AuthProblem.WATCH_UNAVAILABLE) }
                requireCurrent(request.requestId)
                require(context.version == 1 && context.requestId == request.requestId)
                context.identity?.validate()
                require(context.idTokenHint == null || (context.identity != null && context.idTokenHint.length <= 24_000))
                val savedClient = if (context.identity == null && !context.hasSavedAccounts) registrations.client(request.hostId) else null
                // Bind before ACTION_VIEW. Browser launch happens only in response to the user's phone tap.
                val result: AuthorizationCode
                val transaction: OAuthTransaction
                LoopbackListener.bind().use { listener ->
                    transaction = OAuthTransaction(request.hostId, listener.redirectUri, context.identity, context.idTokenHint, savedClient)
                    try { openBrowser(transaction.authorizationUrl()) } catch (_: Exception) { throw AuthException(AuthProblem.BROWSER) }
                    result = listener.await(transaction)
                }
                requireCurrent(request.requestId)
                try { registrations.save(request.hostId, result.clientId) }
                catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { throw AuthException(AuthProblem.STORAGE) }
                val profile = exchange(transaction, result)
                requireCurrent(received.request.requestId)
                require(profile.identity.clientId == result.clientId && profile.hostId == request.hostId)
                pending = PendingTransfer(received.nodeId,
                    ProfileTransfer(1, request.requestId, BridgeProtocol.newRequestId(), profile),
                    received.request.createdAtEpochMs + OpenAiAuth.ATTEMPT_MS)
                val expiresAt = pending!!.expiresAt
                expiryJob = viewModelScope.launch {
                    delay((expiresAt - now()).coerceAtLeast(0))
                    pending = null
                    if (mutableState.value.stage == PhoneStage.RETRY_TRANSFER) {
                        mutableState.value = PhoneAuthState(PhoneStage.FAILED, AuthProblem.TIMEOUT)
                    }
                }
                transfer()
            } catch (_: TimeoutCancellationException) {
                received?.let { requireCurrent(it.request.requestId) }
                mutableState.value = PhoneAuthState(PhoneStage.FAILED, AuthProblem.WATCH_UNAVAILABLE)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: AuthException) {
                received?.let { requireCurrent(it.request.requestId) }
                mutableState.value = PhoneAuthState(PhoneStage.FAILED, e.problem)
                received?.let { notifyFailure(it) }
            } catch (_: Exception) {
                received?.let { requireCurrent(it.request.requestId) }
                mutableState.value = PhoneAuthState(PhoneStage.FAILED, AuthProblem.STORAGE)
                received?.let { notifyFailure(it) }
            }
        }
    }
    fun retryTransfer() {
        if (job?.isActive == true || pending == null) return
        job = viewModelScope.launch { transfer() }
    }
    private suspend fun transfer() {
        val value = pending ?: return
        if (value.expiresAt < now()) {
            pending = null
            mutableState.value = PhoneAuthState(PhoneStage.FAILED, AuthProblem.TIMEOUT)
            return
        }
        mutableState.value = PhoneAuthState(PhoneStage.TRANSFERRING)
        try {
            val bytes = ProfileProtocol.encode(value.envelope)
            val ack = try {
                ProfileProtocol.decode<ProfileAck>(send(value.nodeId, ProfileProtocol.TRANSFER_PATH, bytes))
            } finally { bytes.fill(0) }
            requireCurrent(value.envelope.requestId)
            require(value.expiresAt >= now() && pending === value)
            require(ack == ProfileAck(1, value.envelope.requestId, value.envelope.transferId))
            // ACK is the durable handoff boundary. The phone never refreshes/revokes this session.
            pending = null
            expiryJob?.cancel()
            mutableState.value = PhoneAuthState(if (value.envelope.profile.planUsageEnabled) PhoneStage.CONNECTED else PhoneStage.IDENTITY_ONLY)
            try { registrations.clear(value.envelope.profile.hostId) } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                BridgeLog.warn(BridgeDiagnostic.STORAGE_UNAVAILABLE)
            }
        } catch (_: TimeoutCancellationException) {
            requireCurrent(value.envelope.requestId)
            transferFailed(value)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            requireCurrent(value.envelope.requestId)
            transferFailed(value)
        }
    }
    private fun transferFailed(value: PendingTransfer) {
        if (pending !== value || value.expiresAt <= now()) {
            pending = null
            mutableState.value = PhoneAuthState(PhoneStage.FAILED, AuthProblem.TIMEOUT)
        } else mutableState.value = PhoneAuthState(PhoneStage.RETRY_TRANSFER, AuthProblem.TRANSFER)
    }
    private suspend fun requireCurrent(requestId: String) {
        currentCoroutineContext().ensureActive()
        if (currentRequestId != requestId) throw CancellationException("Setup superseded")
    }
    private suspend fun send(node: String, path: String, payload: ByteArray): ByteArray =
        withTimeout(BridgeProtocol.TIMEOUT_MS) { rpc(node, path, payload) }
    private suspend fun notifyFailure(received: ReceivedAuthRequest) {
        try {
            send(received.nodeId, ProfileProtocol.FAILURE_PATH,
                ProfileProtocol.encode(AuthFailure(1, received.request.requestId, received.request.hostId)))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { BridgeLog.warn(BridgeDiagnostic.CONNECTION_FAILED) }
    }
    override fun onCleared() { pending = null }
}
