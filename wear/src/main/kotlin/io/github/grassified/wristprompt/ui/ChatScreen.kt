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
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.awaitCancellation
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

/** Plain-text projection is observed only in this transcript subtree. */
@Composable
internal fun ChatScreen(
    proof: InferenceViewModel, state: ProofState, ui: ShellPresentation,
    navigation: ShellNavigationViewModel, supplementalGuidance: String?, metrics: PresentationMetrics? = null,
) {
    val owner: ChatPresentationOwner = viewModel(factory = viewModelFactory {
        initializer { ChatPresentationOwner(proof.chat, metrics) }
    })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(owner, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            owner.visible(true)
            try { awaitCancellation() } finally { owner.visible(false) }
        }
    }
    val presentation by owner.state.collectAsStateWithLifecycle()
    val largeFont = LocalDensity.current.fontScale > 1.2f
    val keys = buildList {
        if (largeFont) addAll(listOf("chat-title", "chat-menu", "chat-model", "chat-billing"))
        else add("chat-header")
        if (!ui.proofAvailable || state.selectedSlug == null) addAll(listOf("chat-unavailable", "chat-setup"))
        if (presentation.rows.isEmpty()) add("chat-empty") else addAll(presentation.rows.map { it.key })
    }
    val scroll = rememberChatScroll(navigation, keys)
    val latest by remember(presentation.rows, scroll) {
        derivedStateOf { latestAvailable(presentation.rows.isNotEmpty(), scroll.canScrollForward) }
    }
    LaunchedEffect(latest, navigation) { navigation.latestVisible.value = latest }
    DisposableEffect(navigation) { onDispose { navigation.latestVisible.value = false } }
    LaunchedEffect(navigation, scroll) {
        navigation.latestRequests.collect { scrollToLatest(scroll) }
    }
    ShellList(Modifier.fillMaxSize(), scroll, spacing = 0.dp) {
        if (largeFont) {
            item(key = "chat-title", contentType = "header") { ShellText("WristPrompt", heading = true) }
            item(key = "chat-menu", contentType = "header") {
                TextButton(onClick = { navigation.open(Surface.MENU) }) { Text("Menu") }
            }
            item(key = "chat-model", contentType = "header") { ModelAffordance(ui, navigation, false) }
            item(key = "chat-billing", contentType = "header") { BillingText(ui) }
        } else item(key = "chat-header", contentType = "header") {
            ChatHeader(ui, navigation)
        }
        if (!ui.proofAvailable || state.selectedSlug == null) {
            item(key = "chat-unavailable", contentType = "status") { ShellText(ui.status) }
            item(key = "chat-setup", contentType = "action") {
                Button(onClick = { navigation.open(if (ui.proofAvailable) Surface.MODELS else Surface.CONNECTION) },
                    label = { Text(if (ui.proofAvailable) "Choose model" else "Account & Connection") })
            }
        }
        if (presentation.rows.isEmpty()) {
            item(key = "chat-empty", contentType = "status") { ShellText("Ask anything") }
        } else chatItems(presentation)
        supplementalGuidance?.let { text ->
            item(key = "chat-guidance", contentType = "recovery") { ShellText(text) }
        }
    }
}

@Composable
private fun ChatHeader(ui: ShellPresentation, navigation: ShellNavigationViewModel) {
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
