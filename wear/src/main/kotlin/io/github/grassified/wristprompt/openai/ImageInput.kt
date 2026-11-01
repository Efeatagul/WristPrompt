package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.images.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.util.Base64

/** Audited exact IDs; catalog membership still required. No prefix/family inference. */
object OpenAiImageCapabilities {
    val sources = setOf("gpt-6.1-sol", "gpt-6-astra", "gpt-5.5", "gpt-5.4")
        .associateWith { "https://developers.openai.com/api/docs/models/$it" }
    const val PLAN_SOURCE = "https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations"
    fun supports(model: String, mode: CredentialMode): Boolean = when (mode) {
        CredentialMode.CHATGPT_PLAN, CredentialMode.OPENAI_API_KEY -> model in sources
    }
    fun validate(model: String, mode: CredentialMode, input: List<JsonObject>) {
        if (ImageInput.hasImages(input) && !supports(model, mode))
            throw ApiFailure(ApiProblem.IMAGES_UNSUPPORTED, requestNotSent = true)
    }
}

/** Local replay contains UUID references. Only ephemeral HTTP input contains data URLs. */
object ImageInput {
    const val MAX_REQUEST_IMAGES = ImageLimits.MAX_BATCH // Across replay AND the new user turn.
    const val MAX_ENCODED_IMAGE_BYTES = MAX_REQUEST_IMAGES * (4 * ((ImageLimits.MAX_IMAGE_BYTES + 2) / 3) + 23)
    const val MAX_REQUEST_BYTES = MAX_ENCODED_IMAGE_BYTES + ResponsesProtocol.MAX_BODY_BYTES
    fun user(text: String, ids: List<String> = emptyList()): JsonObject {
        require(text.isNotBlank() && text.length <= ChatConversation.MAX_PROMPT_CHARS)
        require(ids.size <= ImageLimits.MAX_BATCH && ids.distinct().size == ids.size && ids.all(ImageLimits::validId))
        return buildJsonObject {
            put("role", "user")
            if (ids.isEmpty()) put("content", text)
            else put("content", buildJsonArray {
                add(buildJsonObject { put("type", "input_text"); put("text", text) })
                ids.forEach { id -> add(buildJsonObject { put("type", "image_attachment"); put("attachment_id", id) }) }
            })
        }
    }
    fun ids(item: JsonObject): List<String> = if (item["role"]?.jsonPrimitive?.content != "user") emptyList() else
        (item["content"] as? JsonArray).orEmpty().filter { it.jsonObject["type"]?.jsonPrimitive?.content == "image_attachment" }
            .map { it.jsonObject.getValue("attachment_id").jsonPrimitive.content }
    fun hasImages(input: List<JsonObject>) = input.any { item ->
        (item["content"] as? JsonArray).orEmpty().any { it.jsonObject["type"]?.jsonPrimitive?.content in setOf("image_attachment", "input_image") }
    }
    fun validateWire(input: List<JsonObject>): Int {
        var count = 0
        var encoded = 0L
        input.forEach { item ->
            val content = item["content"] as? JsonArray ?: return@forEach
            content.forEach { part ->
                val obj = part.jsonObject
                if (obj["type"]?.jsonPrimitive?.content == "input_image") {
                    require(item["role"]?.jsonPrimitive?.content == "user")
                    require(obj.keys == setOf("type", "image_url"))
                    val url = obj.getValue("image_url").jsonPrimitive
                    require(url.isString && url.content.startsWith("data:image/jpeg;base64,"))
                    val data = url.content.substring(23)
                    require(data.isNotEmpty() && data.length % 4 == 0 && data.length <= 4 * ((ImageLimits.MAX_IMAGE_BYTES + 2) / 3))
                    require(data.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+/=" })
                    count++; encoded += url.content.length
                }
            }
        }
        if (count > MAX_REQUEST_IMAGES || encoded > MAX_ENCODED_IMAGE_BYTES)
            throw ApiFailure(ApiProblem.IMAGE_LIMIT, requestNotSent = true)
        return count
    }
    suspend fun materialize(input: List<JsonObject>, read: suspend (String) -> ByteArray): List<JsonObject> {
        try {
            val count = input.sumOf { ids(it).size }
            if (count > MAX_REQUEST_IMAGES) throw ApiFailure(ApiProblem.IMAGE_LIMIT, requestNotSent = true)
            var encoded = 0L
            return input.map { item ->
                if (ids(item).isEmpty()) item else JsonObject(item + ("content" to JsonArray(item.getValue("content").jsonArray.map { part ->
                    val obj = part.jsonObject
                    if (obj["type"]?.jsonPrimitive?.content != "image_attachment") part else {
                        val id = obj.getValue("attachment_id").jsonPrimitive.content
                        require(ImageLimits.validId(id))
                        val bytes = read(id)
                        require(bytes.size in 1..ImageLimits.MAX_IMAGE_BYTES)
                        encoded += 4L * ((bytes.size + 2L) / 3L) + 23 // data:image/jpeg;base64,
                        if (encoded > MAX_ENCODED_IMAGE_BYTES) throw ApiFailure(ApiProblem.IMAGE_LIMIT, requestNotSent = true)
                        buildJsonObject { put("type", "input_image"); put("image_url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)) }
                    }
                })))
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: ApiFailure) { throw failure
        } catch (_: Exception) { throw ApiFailure(ApiProblem.IMAGE_UNAVAILABLE, requestNotSent = true) }
    }
}
