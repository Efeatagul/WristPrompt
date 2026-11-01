package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialProfile
import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.auth.OpenAiAuth
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface AiProvider {
    suspend fun generateImage(credential: CredentialLease, model: AiModel, input: List<JsonObject>,
        onEvent: suspend (ResponseEvent) -> Unit) { throw ApiFailure(ApiProblem.CONFIGURATION) }
    suspend fun stream(credential: CredentialLease, model: AiModel, input: List<JsonObject>,
        configuration: InferenceConfiguration, onEvent: suspend (ResponseEvent) -> Unit) {
        InferenceControls.validate(configuration, model, credential.mode)
        if (configuration.controls.isNotEmpty()) throw ApiFailure(ApiProblem.CONFIGURATION)
        stream(credential, model.slug, input, onEvent)
    }
    suspend fun models(credential: CredentialLease): List<AiModel>
    suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit)
    suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
        throw ApiFailure(ApiProblem.CONFIGURATION)
    }
}

class OpenAiProvider(private val http: OpenAiHttp) : AiProvider {
    override suspend fun generateImage(credential: CredentialLease, model: AiModel, input: List<JsonObject>,
        onEvent: suspend (ResponseEvent) -> Unit) =
        streamRequest(credential, OpenAiGeneration.request(model, credential.mode, input), onEvent, generation = true)
    override suspend fun models(credential: CredentialLease): List<AiModel> = http.execute(
        Request.Builder().url(ResponsesProtocol.MODELS_URL).header("Authorization", "Bearer ${credential.accessToken}")
            .header("Accept", "application/json").build(), 30,
    ) { response ->
        if (response.code != 200) throw http.failure(response, mode = credential.mode)
        val context = currentCoroutineContext()
        when (credential.mode) {
            CredentialMode.CHATGPT_PLAN -> ModelCatalog.read(response.body.byteStream()) { context.ensureActive() }
            CredentialMode.OPENAI_API_KEY -> ApiModelCatalog.read(http.body(response)).map {
                it.copy(thinking = OpenAiThinkingCapabilities.forModel(it.slug, credential.mode),
                    imageGeneration = OpenAiGeneration.capability(it.slug, credential.mode))
            }
        }
    }

    override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) =
        streamRequest(credential, ResponsesProtocol.request(model, credential.mode), onEvent)

    override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) =
        streamRequest(credential, ResponsesProtocol.request(model, input, mode = credential.mode), onEvent)

    override suspend fun stream(credential: CredentialLease, model: AiModel, input: List<JsonObject>,
        configuration: InferenceConfiguration, onEvent: suspend (ResponseEvent) -> Unit) =
        streamRequest(credential, ResponsesProtocol.request(model.slug, input, configuration,
            OpenAiThinkingCapabilities.forModel(model.slug, credential.mode), credential.mode), onEvent)

    private suspend fun streamRequest(credential: CredentialLease, body: String, onEvent: suspend (ResponseEvent) -> Unit, generation: Boolean = false) {
        var started = false
        try {
            http.execute(Request.Builder().url(ResponsesProtocol.RESPONSES_URL)
                .header("Authorization", "Bearer ${credential.accessToken}").header("Accept", "text/event-stream")
                .post(body.toRequestBody("application/json".toMediaType())).build()) { response ->
                if (response.code != 200) throw http.failure(response, mode = credential.mode)
                val mediaType = response.body.contentType()?.let { "${it.type}/${it.subtype}" }
                // An observed 200 response omitted Content-Type. In that case validate
                // the body as bounded SSE; missing metadata never establishes success.
                val hasContentType = mediaType != null || response.header("Content-Type") != null
                if (hasContentType && mediaType != "text/event-stream") {
                    val detail = when (mediaType) {
                        "application/json" -> ProtocolDetail.RESPONSE_JSON
                        "text/html" -> ProtocolDetail.RESPONSE_HTML
                        else -> ProtocolDetail.RESPONSE_OTHER_TYPE
                    }
                    throw ApiFailure(ApiProblem.PROTOCOL, status = response.code, protocolDetail = detail)
                }
                started = true
                val events = SseReader(response.body.byteStream(), generation)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val event = events.next() ?: throw ApiFailure(ApiProblem.INTERRUPTED)
                    onEvent(if (event is ResponseEvent.Failed) ResponseEvent.Failed(ApiErrors.forMode(ApiErrors.withResponseContext(event.failure, response.code,
                        response.header("openai-request-id") ?: response.header("x-request-id")), credential.mode)) else event)
                    if (event is ResponseEvent.Completed || event is ResponseEvent.Failed || event == ResponseEvent.Incomplete) break
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: ApiFailure) {
            currentCoroutineContext().ensureActive()
            if (started && failure.problem == ApiProblem.NETWORK) throw ApiFailure(ApiProblem.INTERRUPTED)
            throw failure
        }
    }
}

class OpenAiRefresh(private val http: OpenAiHttp, private val clock: SessionClock = SessionClock.SYSTEM) {
    suspend fun refresh(profile: CredentialProfile): CredentialProfile {
        val refreshToken = profile.refreshToken ?: throw ApiFailure(ApiProblem.REAUTHORIZE)
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", profile.identity.clientId)
            .add("refresh_token", refreshToken).add("resource", OpenAiAuth.RESOURCE).build()
        return http.execute(Request.Builder().url(OpenAiAuth.TOKEN).header("Accept", "application/json").post(body).build(), 30, trackTransmission = true) { response ->
            if (response.code != 200) throw http.failure(response, refresh = true)
            RefreshProtocol.update(profile, http.body(response), clock())
        }
    }
}
