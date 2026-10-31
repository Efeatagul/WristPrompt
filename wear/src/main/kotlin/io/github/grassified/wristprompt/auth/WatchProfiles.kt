package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.AuthStartRequest
import io.github.grassified.wristprompt.bridge.BridgeProtocolException
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import io.github.grassified.wristprompt.storage.SecretRecord
import io.github.grassified.wristprompt.storage.SecureStorageException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    val signOutPending: Set<String> = emptySet(),
    val signOutResults: Map<String, RevocationResult> = emptyMap(),
    val selectedModels: Map<String, String> = emptyMap(),
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
    val refreshing: Boolean = false, val sessionRenewed: Boolean = false,
    val signingOut: Boolean = false, val signedOut: Boolean = false, val revocation: RevocationResult? = null,
)

class WatchProfiles(private val record: SecretRecord, private val dispatcher: CoroutineDispatcher = Dispatchers.IO, private val now: SessionClock = SessionClock.SYSTEM) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(WatchAccountState())
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()
    // Guarded by the short vault mutex. Never hold it across network IO.
    private val refreshing = mutableSetOf<String>()
    private val signOutLocks = ConcurrentHashMap<String, Mutex>()
    private val activeSignOuts = mutableSetOf<String>()
    private val blockedSignOuts = mutableSetOf<String>()
    private val renewedClients = mutableSetOf<String>()
    val state = mutableState.asStateFlow()

    private fun read(): WatchVault {
        var bytes: ByteArray? = null
        return try {
            bytes = record.read() ?: return WatchVault()
            ProfileProtocol.json.decodeFromString<WatchVault>(bytes.decodeToString(throwOnInvalidSequence = true)).also {
                require(it.version == 1 && it.profiles.size <= 10)
                it.profiles.forEach(CredentialProfile::validate)
                val clients = it.profiles.map { p -> p.identity.clientId }
                require(clients.distinct().size == it.profiles.size)
                require(it.profiles.map { p -> p.hostId }.distinct().size <= 1)
                require(it.selectedClientId == null || it.selectedClientId in clients)
                require(it.signOutPending.all { client -> client in clients })
                require(it.signOutResults.keys.all { client -> client in clients })
                require(it.signOutResults.keys.all { client -> it.profiles.single { p -> p.identity.clientId == client }.let { p ->
                    p.reauthorizationRequired && p.accessToken == null && p.refreshToken == null && p.idToken.isEmpty()
                } })
                require(it.selectedModels.all { (client, slug) -> client in clients && validModel(slug) })
            }
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(storageError = true, authorizing = false)
            throw SecureStorageException()
        } finally { bytes?.fill(0) }
    }
    private fun publish(value: WatchVault, authorizing: Boolean = value.pending?.let { it.authorizing && it.expiresAt >= now() } == true) {
        val selected = value.profiles.singleOrNull { it.identity.clientId == value.selectedClientId }
        val signedOut = value.selectedClientId in value.signOutResults
        val signingOut = value.selectedClientId in value.signOutPending || value.selectedClientId in blockedSignOuts
        mutableState.value = WatchAccountState(
            value.profiles.map {
                val client = it.identity.clientId
                // Retained registration metadata is not a currently authenticated account.
                val signedOutRegistration = client in value.signOutResults ||
                    client in value.signOutPending || client in blockedSignOuts
                AccountLabel(client, if (signedOutRegistration) "Signed-out ChatGPT account" else
                    (it.name ?: it.email ?: "ChatGPT account") + " · " + client.takeLast(6))
            },
            value.selectedClientId, selected != null && !signedOut && !signingOut, selected?.planUsageEnabled == true && !signingOut, authorizing,
            pendingExpiresAt = value.pending?.expiresAt, expired = value.pending?.let { it.expiresAt < now() } == true,
            planWelcome = !signingOut && !signedOut && selected?.let { it.planUsageEnabled && it.identity.clientId !in value.confirmedPlanClients } == true,
            generation = value.generation, reauthorizationRequired = selected?.reauthorizationRequired == true || (value.selectedClientId in value.refreshPending && value.selectedClientId !in refreshing),
            refreshing = value.selectedClientId in refreshing, sessionRenewed = value.selectedClientId in renewedClients,
            signingOut = value.signOutPending.isNotEmpty(), signedOut = signedOut, revocation = value.signOutResults[value.selectedClientId],
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
    suspend fun load() = withContext(dispatcher) {
        mutex.withLock {
            try {
                val vault = read()
                val abandoned = (vault.signOutPending + blockedSignOuts) - activeSignOuts
                if (abandoned.isEmpty()) publish(vault) else {
                    save(vault.copy(
                        profiles = vault.profiles.map { if (it.identity.clientId in abandoned) cleared(it) else it },
                        signOutPending = vault.signOutPending - abandoned,
                        signOutResults = vault.signOutResults + abandoned.associateWith { RevocationResult.UNCONFIRMED },
                        refreshPending = vault.refreshPending - abandoned,
                    ))
                    blockedSignOuts.removeAll(abandoned)
                    publish(read())
                }
            } catch (_: Exception) {
                mutableState.value = WatchAccountState(storageError = true)
            }
        }
    }
    suspend fun confirmPlanNotice() = withContext(dispatcher) {
        mutex.withLock {
            val vault = read()
            val client = vault.selectedClientId ?: return@withLock
            save(vault.copy(confirmedPlanClients = vault.confirmedPlanClients + client))
        }
    }
    suspend fun select(clientId: String?) = withContext(dispatcher) {
        mutex.withLock {
            val vault = read()
            require(vault.signOutPending.isEmpty() && blockedSignOuts.isEmpty())
            require(clientId == null || vault.profiles.any { it.identity.clientId == clientId })
            save(vault.copy(selectedClientId = clientId, pending = null, generation = vault.generation + 1))
        }
    }
    private fun validModel(slug: String) = slug.isNotBlank() && slug.length <= 256 && slug.none(Char::isISOControl)

    suspend fun selectedModel(clientId: String): String? = withContext(dispatcher) {
        mutex.withLock { read().selectedModels[clientId] }
    }

    suspend fun selectModel(clientId: String, generation: Long, slug: String) = withContext(dispatcher) {
        mutex.withLock {
            require(validModel(slug))
            val vault = read()
            if (vault.selectedClientId != clientId || vault.generation != generation) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
            save(vault.copy(selectedModels = vault.selectedModels + (clientId to slug)))
        }
    }

    suspend fun prepare(nodeId: String, request: AuthStartRequest) = withContext(dispatcher) {
        mutex.withLock {
            val vault = read()
            val selected = vault.profiles.singleOrNull { it.identity.clientId == vault.selectedClientId }
            require(vault.signOutPending.isEmpty() && blockedSignOuts.isEmpty())
            require(vault.profiles.all { it.hostId == request.hostId })
            if (selected?.identity?.clientId in refreshing) throw ApiFailure(ApiProblem.REAUTHORIZING)
            val generation = vault.generation + 1
            save(vault.copy(generation = generation, pending = PendingLogin(nodeId, request.requestId, request.hostId,
                now() + OpenAiAuth.ATTEMPT_MS, selected?.identity, generation)))
        }
    }
    private val retryAfter = mutableMapOf<String, Long>()

    suspend fun credential(refresh: suspend (CredentialProfile) -> CredentialProfile): CredentialLease = withContext(dispatcher) {
        val selected = mutex.withLock {
            val vault = read()
            (vault.selectedClientId ?: throw ApiFailure(ApiProblem.REAUTHORIZE)) to vault.generation
        }
        val (client, generation) = selected
        refreshLocks.computeIfAbsent(client) { Mutex() }.withLock {
            val original = mutex.withLock {
                val vault = read()
                requireSelection(vault, client, generation)
                val profile = vault.profiles.single { it.identity.clientId == client }
                if (profile.reauthorizationRequired || client in vault.refreshPending) throw ApiFailure(ApiProblem.REAUTHORIZE)
                if (vault.pending?.let { it.expiresAt >= now() } == true) throw ApiFailure(ApiProblem.REAUTHORIZING)
                if (!profile.planUsageEnabled) throw ApiFailure(ApiProblem.PLAN_DISABLED)
                if (!TokenExpiry.needsRefresh(profile, now())) return@withContext lease(profile, generation)
                if (now() < (retryAfter[client] ?: 0)) throw ApiFailure(ApiProblem.TEMPORARY)
                if (profile.refreshToken == null) throw ApiFailure(ApiProblem.REAUTHORIZE)
                profile
            }
            // Shield the durable intent + bounded grant + atomic commit as one operation.
            // A cancelled caller must not abandon an already consumed rotating grant.
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                mutex.withLock {
                    val latest = read()
                    requireSelection(latest, client, generation)
                    if (latest.profiles.single { it.identity.clientId == client } != original) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
                    refreshing.add(client)
                    try { save(latest.copy(refreshPending = latest.refreshPending + client)) }
                    catch (failure: Exception) { refreshing.remove(client); throw failure }
                }
                try {
                    val updated = try {
                        withTimeout(35_000) { refresh(original) }.also {
                            require(it.identity == original.identity && it.hostId == original.hostId && it.idToken == original.idToken)
                            it.validate()
                        }
                    } catch (failure: Exception) {
                        mutex.withLock {
                            val latest = read()
                            // Explicit reauthorization may already have replaced this profile.
                            if (latest.profiles.singleOrNull { it.identity.clientId == client } != original) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
                            when {
                                failure is ApiFailure && failure.problem == ApiProblem.REAUTHORIZE && ApiErrors.unusableRefresh(failure.code) ->
                                    save(latest.copy(profiles = replace(latest, unusable(original)), refreshPending = latest.refreshPending - client))
                                failure is ApiFailure && (failure.requestNotSent || failure.status?.let { it in 400..599 } == true) -> {
                                    save(latest.copy(refreshPending = latest.refreshPending - client))
                                    retryAfter[client] = now() + 5000
                                }
                                // No trustworthy rejection: preserve ciphertext and durable intent;
                                // timeout, malformed success, cancellation and transport loss are uncertain.
                                else -> throw ApiFailure(ApiProblem.REAUTHORIZE)
                            }
                        }
                        throw failure
                    }
                    mutex.withLock {
                        val latest = read()
                        if (latest.profiles.singleOrNull { it.identity.clientId == client } != original || client !in latest.refreshPending) {
                            throw ApiFailure(ApiProblem.STALE_ACCOUNT)
                        }
                        // Patch the latest vault, never a pre-network snapshot: other accounts
                        // and preferences may have changed while this registration refreshed.
                        save(latest.copy(profiles = replace(latest, updated), refreshPending = latest.refreshPending - client))
                        renewedClients.add(client)
                    }
                } finally {
                    mutex.withLock {
                        refreshing.remove(client)
                        // read() failures retain the storage-error state; never mask them.
                        try {
                            if (!mutableState.value.storageError) publish(read())
                            else mutableState.value = mutableState.value.copy(refreshing = false)
                        } catch (_: SecureStorageException) { }
                    }
                }
            }
            mutex.withLock {
                val vault = read()
                requireSelection(vault, client, generation)
                val profile = vault.profiles.single { it.identity.clientId == client }
                if (!profile.planUsageEnabled) throw ApiFailure(ApiProblem.PLAN_DISABLED)
                lease(profile, generation)
            }
        }
    }

    private fun requireSelection(vault: WatchVault, client: String, generation: Long) {
        if (vault.selectedClientId != client || vault.generation != generation || client in vault.signOutPending || client in blockedSignOuts) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
    }
    private fun lease(profile: CredentialProfile, generation: Long) = CredentialLease(profile.identity.clientId, generation,
        profile.accessToken ?: throw ApiFailure(ApiProblem.REAUTHORIZE), profile.expiresAtEpochMs ?: throw ApiFailure(ApiProblem.REAUTHORIZE))

    suspend fun isCurrent(lease: CredentialLease): Boolean = withContext(dispatcher) {
        mutex.withLock {
            val vault = read()
            val profile = vault.profiles.singleOrNull { it.identity.clientId == lease.clientId }
            vault.selectedClientId == lease.clientId && vault.generation == lease.generation && lease.clientId !in vault.signOutPending && lease.clientId !in blockedSignOuts &&
                profile?.accessToken == lease.accessToken && profile.planUsageEnabled &&
                (vault.pending == null || vault.pending.expiresAt < now()) && lease.clientId !in vault.refreshPending
        }
    }
    suspend fun conversationIdentity(lease: CredentialLease): String = withContext(dispatcher) { mutex.withLock {
        val vault = read()
        require(vault.selectedClientId == lease.clientId && vault.generation == lease.generation)
        val identity = vault.profiles.single { it.identity.clientId == lease.clientId }.identity
        val bytes = kotlinx.serialization.json.Json.encodeToString(RegistrationIdentity.serializer(), identity).encodeToByteArray()
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    } }
    private fun replace(vault: WatchVault, profile: CredentialProfile): List<CredentialProfile> = vault.profiles.map {
        if (it.identity.clientId == profile.identity.clientId) profile else it
    }
    private fun unusable(profile: CredentialProfile) = profile.copy(accessToken = null, refreshToken = null,
        tokenType = null, expiresIn = null, reauthorizationRequired = true)

    private fun cleared(profile: CredentialProfile) = profile.copy(accessToken = null, refreshToken = null,
        idToken = "", tokenType = null, expiresIn = null, earliestRefreshAt = null, reauthorizationRequired = true)

    suspend fun signOut(
        stopRequests: (String) -> Unit = {},
        revoke: suspend (RevocationCredential) -> RevocationResult,
    ): RevocationResult = withContext(dispatcher + NonCancellable) {
        val target = mutex.withLock { read().let { vault -> vault.selectedClientId?.let { it to vault.generation } } }
            ?: return@withContext RevocationResult.UNCONFIRMED
        val (client, requestedGeneration) = target
        signOutLocks.computeIfAbsent(client) { Mutex() }.withLock {
            val previous = mutex.withLock { read().signOutResults[client] }
            if (previous != null) return@withContext previous
            mutex.withLock {
                val vault = read()
                require(vault.selectedClientId == client && vault.generation == requestedGeneration)
                activeSignOuts.add(client)
                blockedSignOuts.add(client)
                try {
                    save(vault.copy(signOutPending = vault.signOutPending + client,
                        pending = null, accepted = null, generation = vault.generation + 1))
                } catch (failure: Exception) { activeSignOuts.remove(client); throw failure }
            }
            // No vault lock is held while cancelling calls, waiting for a bounded
            // rotation, or contacting discovery/revocation. All new leases are blocked.
            try {
                try { stopRequests(client) } catch (_: Exception) { /* Durable gate still blocks credentials. */ }
                refreshLocks.computeIfAbsent(client) { Mutex() }.withLock {
                    val (credential, uncertain) = mutex.withLock {
                        val vault = read()
                        val profile = vault.profiles.single { it.identity.clientId == client }
                        profile.refreshToken?.let { RevocationCredential(client, it) } to (client in vault.refreshPending)
                    }
                    val remote = if (credential == null) RevocationResult.UNCONFIRMED else try {
                        withTimeout(15_000) { revoke(credential) }
                    } catch (_: Exception) { RevocationResult.UNCONFIRMED }
                    // A lost rotation may have produced an unknown replacement token.
                    // Revoking an older already-invalid token does not prove that session ended.
                    val result = if (uncertain) RevocationResult.UNCONFIRMED else remote
                    mutex.withLock {
                        val vault = read()
                        renewedClients.remove(client)
                        retryAfter.remove(client)
                        save(vault.copy(profiles = vault.profiles.map { if (it.identity.clientId == client) cleared(it) else it },
                            signOutPending = vault.signOutPending - client,
                            signOutResults = vault.signOutResults + (client to result),
                            refreshPending = vault.refreshPending - client))
                        blockedSignOuts.remove(client)
                        publish(read())
                    }
                    result
                }
            } finally { mutex.withLock { activeSignOuts.remove(client) } }
        }
    }

    private fun pending(vault: WatchVault, nodeId: String, requestId: String, hostId: String): PendingLogin =
        vault.pending?.takeIf {
            it.nodeId == nodeId && it.requestId == requestId && it.hostId == hostId &&
                it.expiresAt >= now() && it.generation == vault.generation
        } ?: throw BridgeProtocolException()

    suspend fun context(nodeId: String, request: ContextRequest): LoginContext = withContext(dispatcher) {
        mutex.withLock {
            ProfileProtocol.validate(request)
            val vault = read()
            val pending = pending(vault, nodeId, request.requestId, request.hostId)
            val selected = vault.profiles.singleOrNull { it.identity == pending.identity }
            save(vault.copy(pending = pending.copy(authorizing = true)), authorizing = true)
            LoginContext(1, request.requestId, pending.identity, selected?.idToken?.takeIf { it.isNotEmpty() }, vault.profiles.isNotEmpty())
        }
    }
    suspend fun failed(nodeId: String, failure: AuthFailure) = withContext(dispatcher) {
        mutex.withLock {
            ProfileProtocol.validate(ContextRequest(failure.version, failure.requestId, failure.hostId))
            val vault = read()
            pending(vault, nodeId, failure.requestId, failure.hostId)
            save(vault.copy(pending = null))
            mutableState.value = mutableState.value.copy(failed = true)
        }
    }
    suspend fun accept(nodeId: String, bytes: ByteArray): ProfileAck = withContext(dispatcher) {
        val transfer = ProfileProtocol.decodeTransfer(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).toList()
        mutex.withLock {
            val vault = read()
            require(transfer.profile.identity.clientId !in vault.signOutPending)
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
            require(existing == null || (existing.identity == transfer.profile.identity && existing.hostId == transfer.profile.hostId))
            val profiles = vault.profiles.filterNot { it.identity.clientId == transfer.profile.identity.clientId } + transfer.profile
            require(profiles.size <= 10)
            renewedClients.remove(transfer.profile.identity.clientId)
            save(vault.copy(profiles = profiles, selectedClientId = transfer.profile.identity.clientId,
                pending = null, generation = vault.generation + 1,
                refreshPending = vault.refreshPending - transfer.profile.identity.clientId,
                signOutResults = vault.signOutResults - transfer.profile.identity.clientId,
                accepted = AcceptedTransfer(nodeId, transfer.requestId, transfer.transferId, hash, p.expiresAt)))
            ProfileAck(1, transfer.requestId, transfer.transferId)
        }
    }
}
