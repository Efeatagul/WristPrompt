package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.InferenceViewModel

/** Shell events only. Generation, reset and platform-result ownership stay in existing owners. */
internal class ShellInteraction(
    private val model: InferenceViewModel,
    private val navigation: ShellNavigationViewModel,
    private val write: () -> Unit,
) {
    fun ask() {
        if (model.state.value.busy || model.chat.value.busy) return
        navigation.open(Surface.COMPOSER)
        // A retained draft is reviewed, never replaced just by returning to Ask.
        if (model.chat.value.draft.isEmpty()) write()
    }
    fun send() {
        model.send()
        if (model.chat.value.draft.isEmpty()) navigation.open(Surface.CHAT)
    }
    fun newChat() { model.newChat(); navigation.open(Surface.CHAT) }
    fun stop() { model.stop() }
}

internal enum class PrimaryAction(val label: String) { ASK("Ask"), SEND("Send"), STOP("Stop") }
internal fun primaryAction(surface: Surface, busy: Boolean, welcome: Boolean = false): PrimaryAction? = when {
    busy -> PrimaryAction.STOP
    welcome -> null
    surface == Surface.CHAT -> PrimaryAction.ASK
    surface == Surface.COMPOSER -> PrimaryAction.SEND
    else -> null
}

internal fun billingDisclosure(billing: String): String = when (billing) {
    "ChatGPT plan" -> "Using ChatGPT plan"
    "OpenAI API" -> "OpenAI API · API billing"
    else -> billing
}
