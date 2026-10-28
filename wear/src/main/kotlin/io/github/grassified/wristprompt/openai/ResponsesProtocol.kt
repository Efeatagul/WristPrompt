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
    const val MAX_BODY_BYTES = 262_144L
    const val MAX_TEXT_CHARS = 65_536
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun models(body: String): List<AiModel> = ModelCatalog.read(body.byteInputStream())

    fun request(model: String): String {
        require(model.isNotBlank() && model.length <= 256 && model.none(Char::isISOControl))
        return json.encodeToString(ResponsesRequest(model, listOf(InputMessage("user", TEST_PROMPT))))
    }
}

data class AiModel(val slug: String, val displayName: String)
@Serializable private data class ResponsesRequest(val model: String, val input: List<InputMessage>, val store: Boolean = false, val stream: Boolean = true)
@Serializable private data class InputMessage(val role: String, val content: String)

sealed interface ResponseEvent {
    data class Delta(val text: String) : ResponseEvent
    data class Completed(val finalText: String?) : ResponseEvent
    data class Failed(val failure: ApiFailure) : ResponseEvent
    data object Incomplete : ResponseEvent
    data object Ignored : ResponseEvent
}

object ResponseEvents {
    fun decode(eventName: String?, data: String): ResponseEvent {
        var detail = ProtocolDetail.SSE_EVENT_JSON
        return try {
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
                    val text = response["output"]?.jsonArray?.flatMap { item ->
                        val obj = item.jsonObject
                        if (obj["type"]?.jsonPrimitive?.content == "message") obj["content"]?.jsonArray.orEmpty() else emptyList()
                    }?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "output_text" }
                        ?.joinToString("") { it.jsonObject.getValue("text").jsonPrimitive.content }
                    require(text == null || text.length <= ResponsesProtocol.MAX_TEXT_CHARS)
                    ResponseEvent.Completed(text)
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
