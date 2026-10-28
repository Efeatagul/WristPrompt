package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.AuthStartRequest
import io.github.grassified.wristprompt.bridge.BridgeProtocolException
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import io.github.grassified.wristprompt.storage.SecretRecord
import io.github.grassified.wristprompt.storage.SecureStorageException
import java.security.MessageDigest
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
private data class PendingLogin(
    val nodeId: String, val requestId: String, val hostId: String, val expiresAt: Long,
    val identity: RegistrationIdentity?, val generation: Long, val authorizing: Boolean = false,
) { override fun toString() = SafeDiagnostics.REDACTED }

@Serializable
private data class AcceptedTransfer(
    val nodeId: String, val requestId: String, val transferId: String, val digest: List<Byte>, val expiresAt: Long,
) { override fun toString() = SafeDiagnostics.REDACTED }

@Serializable
private data class WatchVault(
    val version: Int = 1,
    val refreshPending: Set<String> = emptySet(),
    val profiles: List<CredentialProfile> = emptyList(), val selectedClientId: String? = null,
    val generation: Long = 0, val pending: PendingLogin? = null,
    val accepted: AcceptedTransfer? = null, val confirmedPlanClients: Set<String> = emptySet(),
) { override fun toString() = SafeDiagnostics.REDACTED }

// Only display metadata escapes the encrypted repository into UI state.
data class AccountLabel(val clientId: String, val label: String)
data class WatchAccountState(
    val accounts: List<AccountLabel> = emptyList(), val activeClientId: String? = null,
    val connected: Boolean = false, val planEnabled: Boolean = false,
    val authorizing: Boolean = false, val storageError: Boolean = false, val failed: Boolean = false, val pendingExpiresAt: Long? = null,
    val expired: Boolean = false, val planWelcome: Boolean = false,
    val generation: Long = 0, val reauthorizationRequired: Boolean = false,
)

class WatchProfiles(private val record: SecretRecord, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(WatchAccountState())
    val state = mutableState.asStateFlow()

    private fun read(): WatchVault {
        var bytes: ByteArray? = null
        return try {
            bytes = record.read() ?: return WatchVault()
            ProfileProtocol.json.decodeFromString<WatchVault>(bytes.decodeToString(throwOnInvalidSequence = true)).also {
                require(it.version == 1 && it.profiles.size <= 10)
                it.profiles.forEach(CredentialProfile::validate)
                require(it.profiles.map { p -> p.identity.clientId }.distinct().size == it.profiles.size)
            }
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(storageError = true, authorizing = false)
            throw SecureStorageException()
        } finally { bytes?.fill(0) }
    }
    private fun publish(value: WatchVault, authorizing: Boolean = value.pending?.let { it.authorizing && it.expiresAt >= now() } == true) {
        val selected = value.profiles.singleOrNull { it.identity.clientId == value.selectedClientId }
        mutableState.value = WatchAccountState(
            value.profiles.map { AccountLabel(it.identity.clientId,
                (it.name ?: it.email ?: "ChatGPT account") + " · " + it.identity.clientId.takeLast(6)) },
            value.selectedClientId, selected != null, selected?.planUsageEnabled == true, authorizing,
            pendingExpiresAt = value.pending?.expiresAt, expired = value.pending?.let { it.expiresAt < now() } == true,
            planWelcome = selected?.let { it.planUsageEnabled && it.identity.clientId !in value.confirmedPlanClients } == true,
            generation = value.generation, reauthorizationRequired = selected?.reauthorizationRequired == true || value.selectedClientId in value.refreshPending,
        )
    }
    private fun save(value: WatchVault, authorizing: Boolean = value.pending?.let { it.authorizing && it.expiresAt >= now() } == true) {
        val bytes = ProfileProtocol.json.encodeToString(WatchVault.serializer(), value).encodeToByteArray()
        try { record.write(bytes) } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(storageError = true, authorizing = false)
            throw SecureStorageException()
        } finally { bytes.fill(0) }
        publish(value, authorizing)
    }
    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { publish(read()) } catch (_: Exception) {
                mutableState.value = WatchAccountState(storageError = true)
            }
        }
    }
    suspend fun confirmPlanNotice() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val vault = read()
            val client = vault.selectedClientId ?: return@withLock
            save(vault.copy(confirmedPlanClients = vault.confirmedPlanClients + client))
        }
    }
    suspend fun select(clientId: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val vault = read()
            require(clientId == null || vault.profiles.any { it.identity.clientId == clientId })
            save(vault.copy(selectedClientId = clientId, pending = null, generation = vault.generation + 1))
        }
    }
    suspend fun prepare(nodeId: String, request: AuthStartRequest) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val vault = read()
            val selected = vault.profiles.singleOrNull { it.identity.clientId == vault.selectedClientId }
            require(selected == null || selected.hostId == request.hostId)
            val generation = vault.generation + 1
            save(vault.copy(generation = generation, pending = PendingLogin(nodeId, request.requestId, request.hostId,
                now() + OpenAiAuth.ATTEMPT_MS, selected?.identity, generation)))
        }
    }
    private val retryAfter = mutableMapOf<String, Long>()

    suspend fun credential(refresh: suspend (CredentialProfile) -> CredentialProfile): CredentialLease = withContext(Dispatchers.IO) {
        mutex.withLock {
            var vault = read()
            var profile = vault.profiles.singleOrNull { it.identity.clientId == vault.selectedClientId }
                ?: throw ApiFailure(ApiProblem.REAUTHORIZE)
            val client = profile.identity.clientId
            if (profile.reauthorizationRequired) throw ApiFailure(ApiProblem.REAUTHORIZE)
            if (vault.pending?.let { it.expiresAt >= now() } == true) throw ApiFailure(ApiProblem.REAUTHORIZING)
            if (client in vault.refreshPending) {
                // A process died after a possibly consumed rotating grant. Never replay that token.
                throw ApiFailure(ApiProblem.REAUTHORIZE)
            }
            if (!profile.planUsageEnabled) throw ApiFailure(ApiProblem.PLAN_DISABLED)
            if (TokenExpiry.needsRefresh(profile, now())) {
                if (now() < (retryAfter[client] ?: 0)) throw ApiFailure(ApiProblem.TEMPORARY)
                if (profile.refreshToken == null) throw ApiFailure(ApiProblem.REAUTHORIZE)
                // Persist intent before contacting a rotating-token endpoint, for process-death safety.
                vault = vault.copy(refreshPending = vault.refreshPending + client)
                save(vault)
                withContext(NonCancellable) {
                    try {
                        val updated = withTimeout(35_000) { refresh(profile) }
                        require(updated.identity == profile.identity && updated.hostId == profile.hostId)
                        updated.validate()
                        vault = vault.copy(profiles = replace(vault, updated), refreshPending = vault.refreshPending - client)
                        save(vault)
                        profile = updated
                    } catch (_: TimeoutCancellationException) {
                        throw ApiFailure(ApiProblem.REAUTHORIZE)
                    } catch (failure: ApiFailure) {
                        if (failure.problem == ApiProblem.REAUTHORIZE) {
                            save(vault.copy(profiles = replace(vault, unusable(profile)), refreshPending = vault.refreshPending - client))
                        } else if (failure.status != null && failure.status != 200) {
                            // A definitive error response did not issue replacement tokens.
                            save(vault.copy(refreshPending = vault.refreshPending - client))
                            retryAfter[client] = now() + 5000
                        }
                        // Preserve tokens after network loss, but make them inert: an unknown
                        // rotating-grant outcome must never be retried with the old refresh token.
                        if (failure.status == null && failure.problem in setOf(ApiProblem.NETWORK, ApiProblem.PROTOCOL)) {
                            throw ApiFailure(ApiProblem.REAUTHORIZE)
                        }
                        throw failure
                    }
                }
            }
            if (!profile.planUsageEnabled) throw ApiFailure(ApiProblem.PLAN_DISABLED)
            CredentialLease(client, vault.generation, profile.accessToken ?: throw ApiFailure(ApiProblem.REAUTHORIZE),
                profile.expiresAtEpochMs ?: throw ApiFailure(ApiProblem.REAUTHORIZE))
        }
    }
    suspend fun isCurrent(lease: CredentialLease): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val vault = read()
            val profile = vault.profiles.singleOrNull { it.identity.clientId == lease.clientId }
            vault.selectedClientId == lease.clientId && vault.generation == lease.generation &&
                profile?.accessToken == lease.accessToken && profile.planUsageEnabled &&
                (vault.pending == null || vault.pending.expiresAt < now()) && lease.clientId !in vault.refreshPending
        }
    }
    private fun replace(vault: WatchVault, profile: CredentialProfile): List<CredentialProfile> = vault.profiles.map {
        if (it.identity.clientId == profile.identity.clientId) profile else it
    }
    private fun unusable(profile: CredentialProfile) = profile.copy(accessToken = null, refreshToken = null,
        tokenType = null, expiresIn = null, reauthorizationRequired = true)

    private fun pending(vault: WatchVault, nodeId: String, requestId: String, hostId: String): PendingLogin =
        vault.pending?.takeIf {
            it.nodeId == nodeId && it.requestId == requestId && it.hostId == hostId &&
                it.expiresAt >= now() && it.generation == vault.generation
        } ?: throw BridgeProtocolException()

    suspend fun context(nodeId: String, request: ContextRequest): LoginContext = withContext(Dispatchers.IO) {
        mutex.withLock {
            ProfileProtocol.validate(request)
            val vault = read()
            val pending = pending(vault, nodeId, request.requestId, request.hostId)
            val selected = vault.profiles.singleOrNull { it.identity == pending.identity }
            save(vault.copy(pending = pending.copy(authorizing = true)), authorizing = true)
            LoginContext(1, request.requestId, pending.identity, selected?.idToken, vault.profiles.isNotEmpty())
        }
    }
    suspend fun failed(nodeId: String, failure: AuthFailure) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ProfileProtocol.validate(ContextRequest(failure.version, failure.requestId, failure.hostId))
            val vault = read()
            pending(vault, nodeId, failure.requestId, failure.hostId)
            save(vault.copy(pending = null))
            mutableState.value = mutableState.value.copy(failed = true)
        }
    }
    suspend fun accept(nodeId: String, bytes: ByteArray): ProfileAck = withContext(Dispatchers.IO) {
        val transfer = ProfileProtocol.decodeTransfer(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).toList()
        mutex.withLock {
            val vault = read()
            val accepted = vault.accepted
            if (accepted != null && accepted.nodeId == nodeId && accepted.requestId == transfer.requestId &&
                accepted.transferId == transfer.transferId && accepted.expiresAt >= now() && accepted.digest == hash) {
                publish(vault)
                return@withLock ProfileAck(1, transfer.requestId, transfer.transferId)
            }
            val p = pending(vault, nodeId, transfer.requestId, transfer.profile.hostId)
            require(p.identity == null || p.identity == transfer.profile.identity)
            require(transfer.profile.savedAtEpochMs <= now() + 30_000 &&
                transfer.profile.savedAtEpochMs >= now() - OpenAiAuth.ATTEMPT_MS)
            // Never allow a new registration to overwrite an existing identity/client binding.
            val existing = vault.profiles.singleOrNull { it.identity.clientId == transfer.profile.identity.clientId }
            require(existing == null || existing.identity == transfer.profile.identity)
            val profiles = vault.profiles.filterNot { it.identity.clientId == transfer.profile.identity.clientId } + transfer.profile
            require(profiles.size <= 10)
            save(vault.copy(profiles = profiles, selectedClientId = transfer.profile.identity.clientId,
                pending = null, generation = vault.generation + 1,
                refreshPending = vault.refreshPending - transfer.profile.identity.clientId,
                accepted = AcceptedTransfer(nodeId, transfer.requestId, transfer.transferId, hash, p.expiresAt)))
            ProfileAck(1, transfer.requestId, transfer.transferId)
        }
    }
}
