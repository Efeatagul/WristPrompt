package io.github.grassified.wristprompt.ui

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import io.github.grassified.wristprompt.openai.*

internal const val CHAT_INPUT_KEY = "chat_text"

// Native Wear RemoteInput intent contract; avoids adding a library for two intent constants.
// https://developer.android.com/reference/androidx/wear/input/RemoteInputIntentHelper
internal fun chatInputIntent(): Intent = Intent("android.support.wearable.input.action.REMOTE_INPUT")
    .putExtra("android.support.wearable.input.extra.REMOTE_INPUTS",
        arrayOf(RemoteInput.Builder(CHAT_INPUT_KEY).setLabel("Message").setAllowFreeFormInput(true)
            .addExtras(Bundle().apply {
                // WearableRemoteInputExtender's documented DONE mapping (not EditorInfo's numeric value).
                putInt("android.support.wearable.input.extra.INPUT_ACTION_TYPE", 2)
                putBoolean("android.support.wearable.input.extra.DISALLOW_EMOJI", true)
            }).build()))
    .putExtra("android.support.wearable.input.extra.CONFIRM_LABEL", "Done")

internal fun TransformingLazyColumnScope.chatItems(chat: ChatState, messageChunks: Map<Long, List<String>>) {
    if (chat.messages.isEmpty()) item { ShellText("Start a conversation. History stays in memory for this session.") }
    for (message in chat.messages) {
        item(key = "message-${message.id}") {
            ShellText(when {
                message.role == ChatRole.ASSISTANT -> "Assistant"
                message.status == ChatMessageStatus.EXCLUDED -> "You · unfinished, excluded from context"
                else -> "You"
            }, heading = true)
        }
        for (chunk in messageChunks.getValue(message.id)) item { ShellText(chunk) }
    }
    if (chat.partial.isNotEmpty()) {
        item { ShellText(if (chat.busy) "Assistant · receiving" else "Assistant · partial, excluded from context", heading = true) }
        paragraphs(chat.partial)
    }
    item { ShellText(when (chat.stage) {
        ChatStage.EMPTY -> "Ready"
        ChatStage.COMPOSING -> "Draft ready to review"
        ChatStage.STARTING -> "Sending…"
        ChatStage.RECEIVING -> "Receiving…"
        ChatStage.COMPLETED -> "Completed"
        ChatStage.CANCELLED -> "Stopped · unfinished turn excluded from context"
        ChatStage.FAILED -> chat.problem?.let(::problemSummary) ?: "Request failed"
    }) }
    if (chat.stage == ChatStage.FAILED || chat.stage == ChatStage.CANCELLED) {
        item { ShellText("Unfinished exchange is not sent as future context.") }
        paragraphs(chatRecovery(chat.problem))
    }
    if (chat.limitReached) item { ShellText("Conversation limit reached. Start New Chat to continue.") }
    if (chat.problem in setOf(ApiProblem.USAGE_LIMIT, ApiProblem.USAGE_UNAVAILABLE, ApiProblem.NOT_ELIGIBLE))
        item { ShellText("Manage usage is available in Connection / settings. Billing mode has not changed.") }
}

// Guidance uses existing navigation/actions; never automatically retries or changes billing.
internal fun chatRecovery(problem: ApiProblem?): String = when (problem) {
    ApiProblem.REAUTHORIZE, ApiProblem.REAUTHORIZING -> "Open Connection / settings to finish sign-in."
    ApiProblem.API_KEY_MISSING, ApiProblem.INVALID_API_KEY -> "Open Connection / settings to configure your API key. No fallback."
    ApiProblem.PERMISSION, ApiProblem.PLAN_DISABLED -> "Check the selected account and plan permission in Connection / settings."
    ApiProblem.NO_MODELS, ApiProblem.STALE_ACCOUNT -> "Choose model, then Reload models before sending again."
    ApiProblem.USAGE_LIMIT, ApiProblem.USAGE_UNAVAILABLE, ApiProblem.NOT_ELIGIBLE -> "Open Connection / settings for Manage usage."
    ApiProblem.API_LIMIT -> "Check API usage and credits before trying again."
    ApiProblem.STORAGE -> "Protected storage is unavailable. Check Connection / settings before continuing."
    ApiProblem.NETWORK -> "Check the watch’s internet connection. Compose a new message to retry."
    ApiProblem.CONFIGURATION, ApiProblem.INVALID_REQUEST -> "Check connection and model settings before trying again."
    else -> "Compose a new message to try again when ready."
}
