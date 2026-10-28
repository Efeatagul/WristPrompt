package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics

class PreparedCatalog(val credential: CredentialLease, val models: List<AiModel>) {
    override fun toString() = SafeDiagnostics.REDACTED
}

class InferenceSession(
    private val credentials: OpenAiCredentialSource,
    private val provider: AiProvider,
    private val isCurrent: suspend (CredentialLease) -> Boolean,
) {
    suspend fun catalog(): PreparedCatalog = catalog(credentials.current())
    private suspend fun catalog(lease: CredentialLease): PreparedCatalog {
        val models = provider.models(lease)
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        if (models.isEmpty()) throw ApiFailure(ApiProblem.NO_MODELS)
        return PreparedCatalog(lease, models)
    }
    suspend fun test(
        catalog: PreparedCatalog, selected: String,
        onCatalog: (PreparedCatalog) -> Unit,
        onProgress: (StreamResult) -> Unit,
    ): StreamResult {
        val current = credentials.current()
        // Discovery and inference must use the same bearer. Refresh invalidates the old catalog.
        val prepared = if (current.accessToken == catalog.credential.accessToken && current.generation == catalog.credential.generation &&
            current.clientId == catalog.credential.clientId) catalog else catalog(current)
        onCatalog(prepared)
        if (prepared.models.none { it.slug == selected }) throw ApiFailure(ApiProblem.NO_MODELS)
        if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        val result = StreamResult()
        provider.stream(prepared.credential, selected) { event ->
            result.accept(event)
            onProgress(result)
        }
        result.end()
        if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        return result
    }
}
