package io.github.grassified.wristprompt.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object ResponsesProtocol {
    const val MODELS_URL = "https://api.openai.com/v1/models"
    const val RESPONSES_URL = "https://api.openai.com/v1/responses"
    const val MANAGE_USAGE_URL = "https://chatgpt.com/settings/usage"
    const val TEST_PROMPT = "Reply with exactly: WristPrompt works."
    const val EXPECTED_TEXT = "WristPrompt works."
    const val API_EXPECTED_TEXT = "BYOK path works."
    fun expected(mode: io.github.grassified.wristprompt.auth.CredentialMode) = if (mode == io.github.grassified.wristprompt.auth.CredentialMode.OPENAI_API_KEY) API_EXPECTED_TEXT else EXPECTED_TEXT
    const val MAX_BODY_BYTES = 262_144L
    const val MAX_TEXT_CHARS = 65_536
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun models(body: String): List<AiModel> = ModelCatalog.read(body.byteInputStream())

    fun request(model: String, input: List<JsonObject>, configuration: InferenceConfiguration = InferenceConfiguration(),
        capability: ControlCapability? = null, mode: io.github.grassified.wristprompt.auth.CredentialMode = io.github.grassified.wristprompt.auth.CredentialMode.CHATGPT_PLAN): String {
        InferenceControls.validate(configuration, AiModel(model, model, capability), mode)
        require(model.isNotBlank() && model.length <= 256 && model.none(Char::isISOControl))
        require(input.isNotEmpty())
        OpenAiImageCapabilities.validate(model, mode, input)
        require(input.none { ImageInput.ids(it).isNotEmpty() }) // Local references must never reach HTTP.
        val images = ImageInput.validateWire(input)
        val body = buildJsonObject {
            put("model", model); put("input", JsonArray(input)); put("store", false); put("stream", true)
            configuration.controls.singleOrNull()?.let { control ->
                put("reasoning", buildJsonObject { put("effort", control.value) })
            }
        }.toString()
        if (images == 0) require(body.encodeToByteArray().size <= MAX_BODY_BYTES)
        else if (body.encodeToByteArray().size > ImageInput.MAX_REQUEST_BYTES)
            throw ApiFailure(ApiProblem.IMAGE_LIMIT, requestNotSent = true)
        return body
    }

    fun request(model: String, mode: io.github.grassified.wristprompt.auth.CredentialMode = io.github.grassified.wristprompt.auth.CredentialMode.CHATGPT_PLAN): String {
        require(model.isNotBlank() && model.length <= 256 && model.none(Char::isISOControl))
        return json.encodeToString(ResponsesRequest(model, listOf(InputMessage("user", if (mode == io.github.grassified.wristprompt.auth.CredentialMode.OPENAI_API_KEY) "Reply exactly: BYOK path works." else TEST_PROMPT))))
    }
}

data class AiModel(val slug: String, val displayName: String, val thinking: ControlCapability? = null, val imageGeneration: GenerationCapability? = null)
@Serializable private data class ResponsesRequest(val model: String, val input: List<InputMessage>, val store: Boolean = false, val stream: Boolean = true)
@Serializable private data class InputMessage(val role: String, val content: String)

sealed interface ResponseEvent {
    data class Delta(val text: String) : ResponseEvent
    data class Completed(val finalText: String?, val outputItems: List<JsonObject>? = null, val generated: GeneratedOutput? = null) : ResponseEvent {
        override fun toString() = io.github.grassified.wristprompt.bridge.SafeDiagnostics.REDACTED
    }
    data class Failed(val failure: ApiFailure) : ResponseEvent
    data object Incomplete : ResponseEvent
    data object Ignored : ResponseEvent
}

object ResponseEvents {
    fun decode(eventName: String?, data: String, generation: Boolean = false): ResponseEvent {
        var detail = ProtocolDetail.SSE_EVENT_JSON
        return try {
            if (generation) GenerationOutput.checkFrame(data)
            val root = Json.parseToJsonElement(data).jsonObject
            detail = ProtocolDetail.SSE_EVENT_TYPE
            val typeValue = root["type"]?.jsonPrimitive
            require(typeValue?.isString == true)
            val type = typeValue.content
            detail = ProtocolDetail.SSE_EVENT_MISMATCH
            require(eventName.isNullOrEmpty() || eventName == "message" || eventName == type)
            detail = ProtocolDetail.SSE_EVENT_SHAPE
            when (type) {
                "response.output_text.delta" -> ResponseEvent.Delta(root.getValue("delta").jsonPrimitive.content.also { require(root.getValue("delta").jsonPrimitive.isString) })
                "response.completed" -> {
                    val response = root.getValue("response").jsonObject
                    require(response["status"]?.jsonPrimitive?.content == "completed")
                    val items = response["output"]?.jsonArray?.map { it.jsonObject }
                    val text = items?.flatMap { obj ->
                        if (obj["type"]?.jsonPrimitive?.content == "message") obj["content"]?.jsonArray.orEmpty() else emptyList()
                    }?.mapNotNull { part -> when (part.jsonObject["type"]?.jsonPrimitive?.content) {
                        "output_text" -> part.jsonObject.getValue("text").jsonPrimitive.content
                        "refusal" -> part.jsonObject.getValue("refusal").jsonPrimitive.content
                        else -> null
                    } }?.let { parts -> if (items.isNullOrEmpty()) null else parts.joinToString("") }
                    require(text == null || text.length <= ResponsesProtocol.MAX_TEXT_CHARS)
                    // A summary without output items is not an explicit empty replacement.
                    // Match the existing omitted-output contract: retain validated deltas.
                    if (generation) {
                        val call = items?.singleOrNull { it["type"]?.jsonPrimitive?.content == "image_generation_call" }
                            ?: GenerationOutput.invalid()
                        val reference = GenerationOutput.callReference(call)
                        val encoded = call["result"]?.jsonPrimitive?.takeIf { it.isString }?.content ?: GenerationOutput.invalid()
                        val output = items.map { if (it === call) reference else it }
                        validateReplayOutput(output)
                        require(JsonArray(output).toString().encodeToByteArray().size <= ResponsesProtocol.MAX_BODY_BYTES)
                        ResponseEvent.Completed(text, output, GenerationOutput.decode(encoded))
                    } else ResponseEvent.Completed(text, items?.takeIf { it.isNotEmpty() })
                }
                "response.failed" -> ResponseEvent.Failed(ApiErrors.parse(null, root.getValue("response").toString()))
                "response.incomplete" -> ResponseEvent.Incomplete
                "error" -> ResponseEvent.Failed(ApiErrors.parse(null, data))
                else -> ResponseEvent.Ignored
            }
        } catch (failure: ApiFailure) { throw failure
        } catch (_: Exception) { throw ApiFailure(ApiProblem.PROTOCOL, protocolDetail = detail) }
    }
}
