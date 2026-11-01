package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

class PreparedCatalog(val credential: CredentialLease, val models: List<AiModel>) {
    override fun toString() = SafeDiagnostics.REDACTED
}

class InferenceSession(
    private val credentials: OpenAiCredentialSource,
    private val provider: AiProvider,
    private val isCurrent: suspend (CredentialLease) -> Boolean,
) {
    private val generation = Mutex()
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
    suspend fun validate(catalog: PreparedCatalog) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(catalog.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        currentCoroutineContext().ensureActive()
    }
    private suspend fun catalog(lease: CredentialLease): PreparedCatalog {
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        currentCoroutineContext().ensureActive()
        val models = provider.models(lease)
        currentCoroutineContext().ensureActive()
        if (!isCurrent(lease)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
        if (models.isEmpty()) throw ApiFailure(ApiProblem.NO_MODELS)
        return PreparedCatalog(lease, models)
    }
    suspend fun test(
        catalog: PreparedCatalog, selected: String,
        onCatalog: (PreparedCatalog) -> Unit,
        onProgress: (StreamResult) -> Unit,
    ): StreamResult = generate(catalog, selected, null, InferenceConfiguration(), onCatalog, { onProgress(it) })

    suspend fun chat(catalog: PreparedCatalog, selected: String, input: List<JsonObject>,
        configuration: InferenceConfiguration = InferenceConfiguration(), onProgress: suspend (StreamResult) -> Unit): StreamResult = generate(catalog, selected, input, configuration, {}, onProgress)

    suspend fun image(catalog: PreparedCatalog, selected: String, input: List<JsonObject>,
        onProgress: suspend (StreamResult) -> Unit): StreamResult =
        generate(catalog, selected, input, InferenceConfiguration(), {}, onProgress, image = true)

    private suspend fun generate(catalog: PreparedCatalog, selected: String, input: List<JsonObject>?, configuration: InferenceConfiguration,
        onCatalog: (PreparedCatalog) -> Unit, onProgress: suspend (StreamResult) -> Unit, image: Boolean = false): StreamResult = coroutineScope {
        val job = currentCoroutineContext().job
        // Track queued work too, so account/mode cancellation cannot miss a waiting generation.
        active[job] = catalog.credential.clientId
        try { generation.withLock {
            job.ensureActive()
            val current = credentials.current()
            job.ensureActive()
            // A stale action must never turn into a request for another account or bill.
            if (current.clientId != catalog.credential.clientId || current.generation != catalog.credential.generation ||
                current.mode != catalog.credential.mode || current.modeRevision != catalog.credential.modeRevision) {
                throw ApiFailure(ApiProblem.STALE_ACCOUNT)
            }
            // Discovery and inference must use the same bearer. Refresh invalidates the old catalog.
            val prepared = if (current.accessToken == catalog.credential.accessToken) catalog else catalog(current)
            job.ensureActive()
            onCatalog(prepared)
            if (prepared.models.none { it.slug == selected }) throw ApiFailure(ApiProblem.NO_MODELS)
            if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
            job.ensureActive()
            val result = StreamResult(ResponsesProtocol.expected(current.mode))
            val receive: suspend (ResponseEvent) -> Unit = receive@{ event ->
                // Check the owning Job, even if a misbehaving provider invokes us in NonCancellable.
                job.ensureActive()
                if (result.completed) return@receive
                if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
                job.ensureActive()
                result.accept(event)
                if (event != ResponseEvent.Ignored) onProgress(result)
                job.ensureActive()
            }
            val model = prepared.models.first { it.slug == selected }
            if (image) OpenAiGeneration.request(model, prepared.credential.mode, checkNotNull(input))
            InferenceControls.validate(configuration, model, prepared.credential.mode)
            if (input != null) OpenAiImageCapabilities.validate(selected, prepared.credential.mode, input)
            if (image) provider.generateImage(prepared.credential, model, checkNotNull(input), receive)
            else if (input == null) provider.stream(prepared.credential, selected, receive)
            else provider.stream(prepared.credential, model, input, configuration, receive)
            job.ensureActive()
            result.end()
            if (image && result.generated == null) GenerationOutput.invalid()
            if (!isCurrent(prepared.credential)) throw ApiFailure(ApiProblem.STALE_ACCOUNT)
            job.ensureActive()
            result
        } } finally { active.remove(job) }
    }
}
