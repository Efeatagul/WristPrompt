package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.openai.*

/** Static display metadata only. Actions are explicit UI events, never retries or route selection. */
internal enum class RecoveryAction(val label: String) {
    ASK("Ask"), ACCOUNT("Account & Connection"), MODELS("Reload models"),
    USAGE("Manage usage"), DIAGNOSTICS("Diagnostics"), NEW_CHAT("New Chat"),
}
// The fixed large-font control can share its band with Latest; keep its destination recognizable.
internal fun RecoveryAction.primaryLabel(largeFont: Boolean): String =
    if (largeFont && this == RecoveryAction.ACCOUNT) "Account" else label
internal data class ErrorPresentation(val title: String, val explanation: String? = null,
    val action: RecoveryAction? = RecoveryAction.ASK) {
    val text: String get() = listOfNotNull(title, explanation).joinToString("\n")
}
internal const val EXCLUDED_RESPONSE = "This response won't be used for future replies."

internal fun errorPresentation(problem: ApiProblem): ErrorPresentation = when (problem) {
    ApiProblem.REAUTHORIZE -> ErrorPresentation("Sign in again to continue", action = RecoveryAction.ACCOUNT)
    ApiProblem.REAUTHORIZING -> ErrorPresentation("Finish sign-in on phone", action = RecoveryAction.ACCOUNT)
    ApiProblem.API_KEY_MISSING -> ErrorPresentation("Set up an API key to continue", action = RecoveryAction.ACCOUNT)
    ApiProblem.INVALID_API_KEY -> ErrorPresentation("API key rejected", "Check your OpenAI API key.", RecoveryAction.ACCOUNT)
    ApiProblem.API_LIMIT -> ErrorPresentation("API usage unavailable", "Check your OpenAI API account or billing.", RecoveryAction.ACCOUNT)
    ApiProblem.USAGE_LIMIT -> ErrorPresentation("ChatGPT usage limit reached", action = RecoveryAction.USAGE)
    ApiProblem.USAGE_UNAVAILABLE -> ErrorPresentation("ChatGPT usage unavailable", action = RecoveryAction.USAGE)
    ApiProblem.NOT_ELIGIBLE -> ErrorPresentation("ChatGPT plan not eligible", action = RecoveryAction.USAGE)
    ApiProblem.PLAN_DISABLED -> ErrorPresentation("ChatGPT plan usage disabled", action = RecoveryAction.ACCOUNT)
    ApiProblem.PERMISSION -> ErrorPresentation("Account access unavailable", "Check the selected account's access.", RecoveryAction.ACCOUNT)
    ApiProblem.TEMPORARY -> ErrorPresentation("Service unavailable", "Try again with a new message later.")
    ApiProblem.NETWORK -> ErrorPresentation("Couldn't connect", "Check the watch's internet connection.")
    ApiProblem.GENERATION_UNSUPPORTED -> ErrorPresentation("Image generation unsupported", "Requires a reviewed model on the OpenAI API billing route. No request sent.", action = null)
    ApiProblem.GENERATION_OUTPUT -> ErrorPresentation("Image not saved", "Generated image invalid, missing or too large. Retry only with an explicit new request.", action = null)
    ApiProblem.GENERATION_PROMPT -> ErrorPresentation("Review image prompt", "Use non-empty text up to 8192 characters and remove input photos. No request sent.", action = null)
    ApiProblem.IMAGE_TEXT_REQUIRED -> ErrorPresentation("Text required", "Add text to describe what you want to ask about these photos.", action = null)
    ApiProblem.IMAGES_UNSUPPORTED -> ErrorPresentation("Photos unsupported", "Image capability is unavailable for this model and billing route. Choose a supported model or remove photos explicitly.", action = null)
    ApiProblem.IMAGE_UNAVAILABLE -> ErrorPresentation("Photo unavailable", "A selected or saved photo is missing or damaged. Restore it, remove the selection, or start a new chat.", action = null)
    ApiProblem.IMAGE_LIMIT -> ErrorPresentation("Photo limit reached", "Use at most 6 photos across this request and its history. Check storage or start a new chat.", action = null)
    ApiProblem.THINKING_UNSUPPORTED -> ErrorPresentation("Thinking level unsupported", "Choose Default or a supported value in Models.", action = null)
    ApiProblem.NO_MODELS -> ErrorPresentation("Model unavailable", action = RecoveryAction.MODELS)
    ApiProblem.STALE_ACCOUNT -> ErrorPresentation("Account changed", "Reload models to continue.", RecoveryAction.MODELS)
    ApiProblem.STORAGE -> ErrorPresentation("Secure storage unavailable", "Requests are blocked to protect your account.", RecoveryAction.DIAGNOSTICS)
    ApiProblem.CHAT_STORAGE -> ErrorPresentation("Chat could not be saved", "Previous saved messages are unchanged. Check available storage and try again.", RecoveryAction.DIAGNOSTICS)
    ApiProblem.INTERRUPTED -> ErrorPresentation("Response interrupted", EXCLUDED_RESPONSE)
    ApiProblem.INCOMPLETE -> ErrorPresentation("Response incomplete", EXCLUDED_RESPONSE)
    ApiProblem.REFRESH_NOT_READY -> ErrorPresentation("Account temporarily unavailable", "Start a new message later.")
    ApiProblem.CONFIGURATION -> ErrorPresentation("Account needs attention", action = RecoveryAction.ACCOUNT)
    ApiProblem.INVALID_REQUEST -> ErrorPresentation("Message or model unavailable", "Review a new message or choose a model.")
    ApiProblem.PROTOCOL -> ErrorPresentation("Couldn't read the response", "Start a new message later.")
}

internal fun chatError(chat: ChatState): ErrorPresentation? = when {
    chat.problem in setOf(ApiProblem.GENERATION_OUTPUT, ApiProblem.GENERATION_UNSUPPORTED, ApiProblem.GENERATION_PROMPT, ApiProblem.IMAGES_UNSUPPORTED, ApiProblem.IMAGE_UNAVAILABLE, ApiProblem.IMAGE_LIMIT, ApiProblem.IMAGE_TEXT_REQUIRED) -> errorPresentation(chat.problem!!)
    chat.problem == ApiProblem.THINKING_UNSUPPORTED -> errorPresentation(ApiProblem.THINKING_UNSUPPORTED)
    chat.problem == ApiProblem.CHAT_STORAGE -> errorPresentation(ApiProblem.CHAT_STORAGE)
    chat.limitReached && chat.stage !in setOf(ChatStage.FAILED, ChatStage.CANCELLED) -> ErrorPresentation("Start a new chat to continue", action = RecoveryAction.NEW_CHAT)
    chat.stage == ChatStage.CANCELLED -> ErrorPresentation("Response stopped", EXCLUDED_RESPONSE)
    chat.stage == ChatStage.FAILED -> {
        val error = chat.problem?.let(::errorPresentation) ?: ErrorPresentation("Couldn't send message")
        if (chat.partial.isNotEmpty() && chat.problem !in setOf(ApiProblem.INTERRUPTED, ApiProblem.INCOMPLETE))
            error.copy(explanation = listOfNotNull(error.explanation, EXCLUDED_RESPONSE).joinToString("\n"))
        else error
    }
    else -> null
}

internal fun modelCatalogPresentation(state: ProofState): ErrorPresentation? = when {
    state.stage == ProofStage.LOADING -> ErrorPresentation("Loading models…", action = null)
    state.problem == ApiProblem.NO_MODELS -> errorPresentation(ApiProblem.NO_MODELS)
    state.problem != null -> when (state.problem) {
        ApiProblem.NETWORK, ApiProblem.TEMPORARY, ApiProblem.PROTOCOL ->
            ErrorPresentation("Couldn't load models", action = RecoveryAction.MODELS)
        else -> errorPresentation(state.problem)
    }
    state.models.isEmpty() -> ErrorPresentation("Model unavailable", action = RecoveryAction.MODELS)
    else -> null
}
