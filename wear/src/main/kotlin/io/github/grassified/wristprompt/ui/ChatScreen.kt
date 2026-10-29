package io.github.grassified.wristprompt.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import io.github.grassified.wristprompt.openai.*

/** D5-A shell only. Existing centered text, chunking, terminal and recovery rendering remain. */
@Composable
internal fun ChatScreen(
    proof: InferenceViewModel, state: ProofState, ui: ShellPresentation,
    navigation: ShellNavigationViewModel, supplementalGuidance: String?,
) {
    val chat by proof.chat.collectAsStateWithLifecycle()
    val renderedChat = chat
    // Capture matching messages/cache together for the deferred lazy builder (D3-D).
    val chunks = remember(renderedChat.messages) {
        renderedChat.messages.associate { it.id to readableChunks(it.text) }
    }
    val largeFont = LocalDensity.current.fontScale > 1.2f
    ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, Surface.CHAT)) {
        if (largeFont) {
            item(key = "chat-title") { ShellText("WristPrompt", heading = true) }
            action("Menu") { navigation.open(Surface.MENU) }
            item(key = "chat-model") { ModelAffordance(ui, navigation, false) }
            item(key = "chat-billing") { BillingText(ui) }
        } else item(key = "chat-header") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("WristPrompt", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { navigation.open(Surface.MENU) },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Menu") }
                }
                ModelAffordance(ui, navigation, true)
            }
        }
        if (!ui.proofAvailable || state.selectedSlug == null) {
            item { ShellText(ui.status) }
            action(if (ui.proofAvailable) "Choose model" else "Account & Connection") {
                navigation.open(if (ui.proofAvailable) Surface.MODELS else Surface.CONNECTION)
            }
        }
        if (renderedChat.messages.isEmpty() && renderedChat.stage == ChatStage.EMPTY) {
            if (ui.proofAvailable && state.selectedSlug != null) item { ShellText("Ask anything") }
        } else chatItems(renderedChat, chunks)
        supplementalGuidance?.let { paragraphs(it) }
    }
}

@Composable
private fun ModelAffordance(ui: ShellPresentation, navigation: ShellNavigationViewModel, showBilling: Boolean) {
    Button(onClick = { navigation.open(Surface.MODELS) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { contentDescription = "Current model: ${ui.model}. Choose model" },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground),
        label = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${ui.model} ▾", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                if (showBilling) BillingText(ui)
            }
        })
}

@Composable
private fun BillingText(ui: ShellPresentation) {
    Text(billingDisclosure(ui.billing), style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun ComposerScreen(
    proof: InferenceViewModel, state: ProofState, ui: ShellPresentation,
    navigation: ShellNavigationViewModel, interruptedInput: Boolean, inputUnavailable: Boolean,
    write: () -> Unit,
) {
    val chat by proof.chat.collectAsStateWithLifecycle()
    ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, Surface.COMPOSER)) {
        item { ShellText("Review message", heading = true) }
        item { ShellText("Model: ${ui.model}", compact = true) }
        if (!ui.proofAvailable || state.selectedSlug == null) item { ShellText(ui.status) }
        action(if (chat.draft.isEmpty()) "Write message" else "Replace draft", !chat.busy && !state.busy, write)
        if (interruptedInput) item { ShellText("Text input was interrupted. Write your message again.") }
        if (inputUnavailable) item { ShellText("System text input unavailable. Return and try again.") }
        if (chat.draft.isEmpty()) item { ShellText("Enter text, review it here, then Send.") }
        else paragraphs(chat.draft)
        if (chat.draft.length > ChatConversation.MAX_PROMPT_CHARS)
            item { ShellText("Message too long. Limit: ${ChatConversation.MAX_PROMPT_CHARS} characters. Replace the draft with a shorter message.") }
        if (chat.limitReached) item { ShellText("Conversation limit reached. Start New Chat.") }
        action("Back to chat") { navigation.open(Surface.CHAT) }
        action("Discard draft") { proof.draft(""); navigation.open(Surface.CHAT) }
    }
}
