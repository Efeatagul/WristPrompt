package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

data class InferenceAccessState(val mode: CredentialMode = CredentialMode.CHATGPT_PLAN,
    val client: String? = null, val generation: Long = 0, val modeRevision: Long = 0,
    val usable: Boolean = false, val problem: ApiProblem? = null)

class CredentialRouter(
    private val profiles: WatchProfiles, private val keys: ApiKeyRepository,
    private val refresh: suspend (CredentialProfile) -> CredentialProfile, scope: CoroutineScope,
) : OpenAiCredentialSource {
    val state = combine(profiles.state, keys.state) { p, k ->
        val problem = when {
            !k.loaded -> null
            k.storageError -> ApiProblem.STORAGE
            k.mode == CredentialMode.OPENAI_API_KEY -> if (k.configured) null else ApiProblem.API_KEY_MISSING
            p.storageError -> ApiProblem.STORAGE
            p.signingOut || p.signedOut || p.reauthorizationRequired -> ApiProblem.REAUTHORIZE
            p.pendingExpiresAt?.let { it >= System.currentTimeMillis() } == true -> ApiProblem.REAUTHORIZING
            p.connected && !p.planEnabled -> ApiProblem.PLAN_DISABLED
            !p.connected -> ApiProblem.REAUTHORIZE
            else -> null
        }
        InferenceAccessState(k.mode, if (k.mode == CredentialMode.OPENAI_API_KEY) ApiKeyRepository.API_CLIENT else p.activeClientId,
            if (k.mode == CredentialMode.OPENAI_API_KEY) k.keyRevision else p.generation, k.modeRevision,
            k.loaded && problem == null && (k.mode == CredentialMode.OPENAI_API_KEY || p.connected), problem)
    }.stateIn(scope, SharingStarted.Eagerly, InferenceAccessState())

    override suspend fun current(): CredentialLease {
        val selected = keys.snapshot()
        // This is the only route decision. Credential presence and errors never choose a mode.
        val lease = when (selected.mode) {
            CredentialMode.CHATGPT_PLAN -> profiles.credential(refresh).let {
                CredentialLease(it.clientId, it.generation, it.accessToken, it.expiresAt, selected.mode, selected.modeRevision)
            }
            CredentialMode.OPENAI_API_KEY -> keys.credential()
        }
        if (!keys.modeCurrent(selected.mode, selected.modeRevision)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        return lease
    }
    suspend fun isCurrent(lease: CredentialLease): Boolean {
        if (!keys.modeCurrent(lease.mode, lease.modeRevision)) return false
        return when (lease.mode) {
            CredentialMode.CHATGPT_PLAN -> profiles.isCurrent(lease) && keys.modeCurrent(lease.mode, lease.modeRevision)
            CredentialMode.OPENAI_API_KEY -> keys.current(lease)
        }
    }
    suspend fun selectedModel(lease: CredentialLease): String? = when (lease.mode) {
        CredentialMode.CHATGPT_PLAN -> profiles.selectedModel(lease.clientId)
        CredentialMode.OPENAI_API_KEY -> keys.selectedModel()
    }
    suspend fun conversationIdentity(lease: CredentialLease): String {
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        return when (lease.mode) {
            CredentialMode.CHATGPT_PLAN -> profiles.conversationIdentity(lease)
            CredentialMode.OPENAI_API_KEY -> keys.conversationIdentity(lease)
        }
    }
    suspend fun selectModel(lease: CredentialLease, slug: String) {
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        when (lease.mode) {
            CredentialMode.CHATGPT_PLAN -> profiles.selectModel(lease.clientId, lease.generation, slug)
            CredentialMode.OPENAI_API_KEY -> keys.selectModel(lease, slug)
        }
    }
}
