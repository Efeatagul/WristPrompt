package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.json.*

/** Reviewed operation contract; provider/model/route identity is independent of thinking/input. */
data class GenerationCapability(val provider: String, val model: String, val route: String, val source: String) {
    override fun toString() = SafeDiagnostics.REDACTED
}

object OpenAiGeneration {
    const val SOURCE = "https://developers.openai.com/api/docs/guides/tools-image-generation"
    // Deliberately limited review set, intersected with the active credential's catalog.
    private val models = setOf("gpt-6.1-sol", "gpt-6-astra", "gpt-5.5", "gpt-5.4")
    const val TOOL_MODEL = "gpt-image-2.5-flare"
    fun capability(model: String, mode: CredentialMode): GenerationCapability? =
        if (mode == CredentialMode.OPENAI_API_KEY && model in models)
            GenerationCapability("openai", model, mode.name, SOURCE) else null
    fun validate(model: AiModel, mode: CredentialMode) {
        val reviewed = capability(model.slug, mode)
        if (reviewed == null || model.imageGeneration != reviewed)
            throw ApiFailure(ApiProblem.GENERATION_UNSUPPORTED, requestNotSent = true)
    }
    fun request(model: AiModel, mode: CredentialMode, input: List<JsonObject>): String {
        validate(model, mode)
        val last = input.lastOrNull()
        val content = last?.get("content") as? JsonPrimitive
        val prompt = content?.takeIf { it.isString && (last["role"] as? JsonPrimitive)?.contentOrNull == "user" }?.content
        if (prompt.isNullOrBlank() || prompt.length > ChatConversation.MAX_PROMPT_CHARS)
            throw ApiFailure(ApiProblem.GENERATION_PROMPT, requestNotSent = true)
        // Generation is text-only. Never reinterpret selected or historical input attachments.
        if (ImageInput.hasImages(input)) throw ApiFailure(ApiProblem.GENERATION_PROMPT, requestNotSent = true)
        val ordinary = Json.parseToJsonElement(ResponsesProtocol.request(model.slug, input, mode = mode)).jsonObject
        return JsonObject(ordinary + mapOf(
            "tools" to buildJsonArray { add(buildJsonObject {
                put("type", "image_generation"); put("model", TOOL_MODEL); put("action", "generate")
                put("size", "1024x1024"); put("quality", "low"); put("output_format", "jpeg"); put("output_compression", 80)
            }) },
            "tool_choice" to buildJsonObject { put("type", "image_generation") },
        )).toString().also { require(it.encodeToByteArray().size <= ResponsesProtocol.MAX_BODY_BYTES) }
    }
}
