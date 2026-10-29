package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.InferenceViewModel

/** Composition-owned, single-use platform result. Never restored into another composition. */
internal class ComposerInteraction(private val model: InferenceViewModel) {
    private var token: Long? = null
    val pending get() = token != null
    fun begin(): Boolean {
        if (pending) return false
        token = model.inputToken()
        return pending
    }
    fun result(text: String?) {
        val launched = token ?: return
        token = null
        if (text != null) model.inputResult(text, launched)
    }
    fun discard() { token = null }
}
