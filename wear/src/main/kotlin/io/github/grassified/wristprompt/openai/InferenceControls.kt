package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.CredentialMode
import io.github.grassified.wristprompt.bridge.SafeDiagnostics
import kotlinx.serialization.Serializable

/** Default is an empty controls list, never a provider-native string. */
@Serializable data class InferenceConfiguration(
    val version: Int = 1,
    val controls: List<NativeControl> = emptyList(),
) {
    fun validate() {
        require(version == 1 && controls.size <= 8)
        require(controls.map { it.key }.distinct().size == controls.size)
        controls.forEach { it.validate() }
    }
    override fun toString() = SafeDiagnostics.REDACTED
}

@Serializable data class NativeControl(
    val key: String, val value: String, val provider: String, val model: String, val route: String,
) {
    fun validate() {
        listOf(key, value, provider, model, route).forEach {
            require(it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl))
        }
    }
    override fun toString() = SafeDiagnostics.REDACTED
}

data class ControlCapability(
    val key: String, val provider: String, val model: String, val route: String,
    val values: List<String>, val source: String,
) {
    init {
        require(source.isNotBlank() && values.size <= 64 && values.distinct().size == values.size)
        values.forEach { NativeControl(key, it, provider, model, route).validate() }
    }
    fun selection(value: String) = NativeControl(key, value, provider, model, route)
    fun supports(control: NativeControl) = control.key == key && control.provider == provider &&
        control.model == model && control.route == route && control.value in values
    override fun toString() = SafeDiagnostics.REDACTED
}

object InferenceControls {
    const val THINKING = "thinking"
    const val OPENAI = "openai"
    fun validate(configuration: InferenceConfiguration, model: AiModel, mode: CredentialMode) {
        configuration.validate()
        if (configuration.controls.any { control ->
            control.provider != OPENAI || control.model != model.slug || control.route != mode.name ||
                control.key != THINKING || model.thinking?.supports(control) != true
        }) throw ApiFailure(ApiProblem.THINKING_UNSUPPORTED, requestNotSent = true)
    }
}

/** Audited exact-ID API contracts, not a model-family heuristic. See docs/D7_H_THINKING.md.
 * The SIWC catalog contract does not specify reasoning capabilities: plan stays Default-only.
 */
object OpenAiThinkingCapabilities {
    private val apiValues = buildMap {
        put("gpt-6-astra", listOf("low", "medium", "high", "xhigh", "max"))
        put("gpt-6.1-sol", listOf("low", "medium", "high", "xhigh", "max"))
        put("gpt-6-sol", listOf("none", "low", "medium", "high", "xhigh", "max"))
        put("gpt-6-luna", listOf("none", "low", "medium", "high", "xhigh", "max"))
        put("gpt-5.5", listOf("none", "low", "medium", "high", "xhigh"))
        put("gpt-5.4", listOf("none", "low", "medium", "high", "xhigh"))
        put("gpt-5.2", listOf("none", "low", "medium", "high", "xhigh"))
        put("gpt-5.1", listOf("none", "low", "medium", "high"))
        put("gpt-5", listOf("minimal", "low", "medium", "high"))
    }
    fun forModel(model: String, mode: CredentialMode): ControlCapability? {
        if (mode != CredentialMode.OPENAI_API_KEY) return null
        val values = apiValues[model] ?: return null
        return ControlCapability(InferenceControls.THINKING, InferenceControls.OPENAI, model, mode.name,
            values, "https://developers.openai.com/api/docs/models/$model")
    }
}
