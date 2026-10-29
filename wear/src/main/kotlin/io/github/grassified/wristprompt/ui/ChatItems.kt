package io.github.grassified.wristprompt.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

internal fun TransformingLazyColumnScope.chatItems(presentation: ChatPresentation) {
    for (row in presentation.rows) item(key = row.key, contentType = row.type) { TranscriptText(row) }
}

@androidx.compose.runtime.Composable
private fun TranscriptText(row: TranscriptRow) {
    val user = row.type == TranscriptType.USER
    // A lazy row boundary supplies the final newline's line break. Other whitespace is literal.
    val visible = transcriptDisplayText(row)
    androidx.wear.compose.material3.Text(
        visible,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            .clearAndSetSemantics {
                text = AnnotatedString(row.text)
                if (user) stateDescription = "Your message"
            }
            .padding(top = if (row.exchangeStart) 10.dp else 0.dp)
            .then(if (user) androidx.compose.ui.Modifier
                .background(androidx.wear.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(8.dp)
                else androidx.compose.ui.Modifier),
        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
        style = androidx.wear.compose.material3.MaterialTheme.typography.bodyMedium.copy(
            fontSize = if (user) 15.sp else 16.sp, lineHeight = 20.sp),
    )
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
