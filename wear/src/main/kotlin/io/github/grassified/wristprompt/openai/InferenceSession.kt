package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

class PreparedCatalog(val credential: CredentialLease, val models: List<AiModel>) {
    override fun toString() = SafeDiagnostics.REDACTED
}

class InferenceSession(
    private val credentials: OpenAiCredentialSource,
    private val provider: AiProvider,
    private val isCurrent: suspend (CredentialLease) -> Boolean,
) {
    private val active = ConcurrentHashMap<Job, String>()
    fun cancelAll() { active.keys.forEach { it.cancel(CancellationException("Credential mode changed")) } }
    fun cancel(clientId: String) {
        active.forEach { (job, client) -> if (client == clientId) job.cancel(CancellationException("ChatGPT session stopped")) }
    }
    suspend fun catalog(): PreparedCatalog = coroutineScope {
        val lease = credentials.current()
        val job = currentCoroutineContext().job
        active[job] = lease.clientId
        try { catalog(lease) } finally { active.remove(job) }
    }
    private suspend fun catalog(lease: CredentialLease): PreparedCatalog {
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        val models = provider.models(lease)
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        if (models.isEmpty()) throw ApiFailure(ApiProblem.NO_MODELS)
        return PreparedCatalog(lease, models)
    }
    suspend fun test(
        catalog: PreparedCatalog, selected: String,
        onCatalog: (PreparedCatalog) -> Unit,
        onProgress: (StreamResult) -> Unit,
    ): StreamResult = coroutineScope {
        val current = credentials.current()
        // A stale action must never turn into a request for another account or bill.
        if (current.clientId != catalog.credential.clientId || current.generation != catalog.credential.generation ||
            current.mode != catalog.credential.mode || current.modeRevision != catalog.credential.modeRevision) {
            throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        }
        val job = currentCoroutineContext().job
        active[job] = current.clientId
        try {
            // Discovery and inference must use the same bearer. Refresh invalidates the old catalog.
            val prepared = if (current.accessToken == catalog.credential.accessToken && current.generation == catalog.credential.generation &&
                current.clientId == catalog.credential.clientId && current.mode == catalog.credential.mode && current.modeRevision == catalog.credential.modeRevision) catalog else catalog(current)
            onCatalog(prepared)
            if (prepared.models.none { it.slug == selected }) throw ApiFailure(ApiProblem.NO_MODELS)
            if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
            val result = StreamResult(ResponsesProtocol.expected(current.mode))
            provider.stream(prepared.credential, selected) { event ->
                if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
                result.accept(event)
                onProgress(result)
            }
            result.end()
            if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
            result
        } finally { active.remove(job) }
    }
}
