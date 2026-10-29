package io.github.grassified.wristprompt.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import io.github.grassified.wristprompt.R
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.WatchAuthViewModel
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.openai.*

internal enum class Surface { CHAT, COMPOSER, PROOF, MODELS, CONNECTION, DETAILS, OUTPUT }

@Composable
internal fun WearShell(auth: WatchAuthViewModel, settings: ApiKeySettingsViewModel, proof: InferenceViewModel) {
    val accounts by auth.accounts.collectAsStateWithLifecycle()
    val bridge by auth.state.collectAsStateWithLifecycle()
    val key by settings.state.collectAsStateWithLifecycle()
    val setup by settings.setup.collectAsStateWithLifecycle()
    val changing by settings.busy.collectAsStateWithLifecycle()
    WearShellContent(accounts, bridge, key, setup, changing, proof,
        ConnectionActions(auth::start, auth::select, auth::signOut, auth::confirmPlanNotice,
            settings::selectMode, settings::request, settings::remove))
}

internal data class ConnectionActions(
    val start: () -> Unit, val select: (String?) -> Unit, val signOut: () -> Unit,
    val confirm: () -> Unit, val mode: (CredentialMode) -> Unit,
    val request: () -> Unit, val remove: () -> Unit,
)

@Composable
internal fun WearShellContent(
    accounts: WatchAccountState, bridge: WatchAuthState, key: ApiKeyState,
    setup: KeySetupStatus, changing: Boolean, proof: InferenceViewModel,
    actions: ConnectionActions, initialSurface: Surface = Surface.CHAT,
    supplementalGuidance: String? = null,
) {
    val state by proof.state.collectAsStateWithLifecycle()
    val chat by proof.chat.collectAsStateWithLifecycle()
    // Lazy content executes later: capture state and cached rows from the same snapshot.
    val renderedChat = chat
    val messageChunks = remember(renderedChat.messages) {
        renderedChat.messages.associate { it.id to readableChunks(it.text) }
    }
    val navigation: ShellNavigationViewModel = viewModel(factory = viewModelFactory {
        initializer { ShellNavigationViewModel(initialSurface) }
    })
    var surface by navigation.surface
    val composer = remember(proof) { ComposerInteraction(proof) }
    // Marker stays in the retained owner; a fresh process has neither marker nor token.
    var inputPending by navigation.inputPending
    val interruptedInput = inputPending && !composer.pending
    DisposableEffect(composer) { onDispose { composer.discard() } }
    var inputUnavailable by remember { mutableStateOf(false) }
    val input = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK)
            result.data?.let(RemoteInput::getResultsFromIntent)?.getCharSequence(CHAT_INPUT_KEY)?.toString() else null
        // A recreated composition deliberately has no pending interaction to consume.
        if (composer.pending) { composer.result(text); inputPending = false }
    }
    val write: () -> Unit = {
        if (composer.begin()) {
            inputPending = true
            inputUnavailable = false
            try { input.launch(chatInputIntent()) }
            catch (_: ActivityNotFoundException) { composer.discard(); inputPending = false; inputUnavailable = true }
        }
    }
    val ui = shellPresentation(accounts, key, state, bridge, changing)
    val messages = (ApiProblem.entries.map(::problemText) + listOf(
        R.string.setup_storage_error, R.string.signout_confirmed, R.string.signout_unconfirmed,
        R.string.authorization_failed, R.string.authorization_expired, R.string.proof_cancelled,
        R.string.proof_mismatch, R.string.api_model_warning, R.string.manage_usage_hint,
        R.string.plan_welcome, R.string.got_it, R.string.bridge_idle, R.string.looking_for_phone,
        R.string.auth_request_sent, R.string.companion_unavailable, R.string.bridge_error, R.string.multiple_phones,
    )).associateWith { stringResource(it) }
    val welcome = accounts.planWelcome
    val back = { surface = Surface.CHAT }
    BackHandler(enabled = welcome || surface != Surface.CHAT) {
        if (welcome) actions.confirm() else back()
    }
    AppScaffold(timeText = {}) {
        // A measured, non-overlaid header: long labels/font scaling take their own space.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (surface == Surface.CHAT || surface == Surface.COMPOSER) {
                    Text(when (ui.billing) {
                        "ChatGPT plan" -> "Using ChatGPT plan"
                        "OpenAI API" -> "OpenAI API · API billing"
                        else -> ui.billing
                    }, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                } else {
                    Text(ui.billing, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    Text(ui.billingNote, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
            // Stop stays outside the scroll area on every surface, including output/details.
            if (ui.showStop || chat.busy) Button(onClick = proof::stop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp), label = { Text("Stop") })
            if (!welcome && surface == Surface.CHAT) Button(
                onClick = { surface = Surface.COMPOSER }, enabled = !state.busy && !chat.busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp), label = { Text("Compose") })
            if (!welcome && surface == Surface.COMPOSER) Button(
                onClick = { proof.send(); if (proof.chat.value.draft.isEmpty()) surface = Surface.CHAT },
                enabled = ui.proofAvailable && state.selectedSlug != null && !state.busy && chat.canSend,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp), label = { Text("Send") })
            androidx.compose.runtime.key(surface, welcome) {
                ShellList(Modifier.weight(1f)) {
                    if (welcome) {
                        item { ShellText("ChatGPT plan usage", heading = true) }
                        paragraphs(messages.getValue(R.string.plan_welcome))
                        action(messages.getValue(R.string.got_it), onClick = actions.confirm)
                    } else {
                        if (surface != Surface.CHAT && surface != Surface.COMPOSER) action("Back", onClick = back)
                        if (surface == Surface.MODELS || surface == Surface.CONNECTION) item { ShellText(ui.status) }
                        when (surface) {
                            Surface.CHAT -> {
                                item { ShellText("Model: ${ui.model}", compact = true) }
                                if (!ui.proofAvailable || state.selectedSlug == null) item { ShellText(ui.status) }
                                chatItems(renderedChat, messageChunks)
                                supplementalGuidance?.let { paragraphs(it) }
                                action("New Chat", onClick = proof::newChat)
                                action("Choose model", ui.proofAvailable && !state.busy && !chat.busy) { surface = Surface.MODELS }
                                action("Connection / settings", onClick = { surface = Surface.CONNECTION })
                                action("Details", onClick = { surface = Surface.DETAILS })
                            }
                            Surface.COMPOSER -> {
                                action(if (chat.draft.isEmpty()) "Write message" else "Replace draft",
                                    !chat.busy && !state.busy, write)
                                item { ShellText("Model: ${ui.model}", compact = true) }
                                if (!ui.proofAvailable || state.selectedSlug == null) item { ShellText(ui.status) }
                                if (interruptedInput) item { ShellText("Text input was interrupted. Write your message again.") }
                                if (inputUnavailable) item { ShellText("System text input unavailable. Return and try again.") }
                                if (chat.draft.isEmpty()) item { ShellText("Enter text, review it here, then Send.") }
                                else paragraphs(chat.draft)
                                if (chat.draft.length > ChatConversation.MAX_PROMPT_CHARS)
                                    item { ShellText("Message too long. Limit: ${ChatConversation.MAX_PROMPT_CHARS} characters. Replace the draft with a shorter message.") }
                                if (chat.limitReached) item { ShellText("Conversation limit reached. Start New Chat.") }
                                action("Back to chat", onClick = back)
                                action("Discard draft") { proof.draft(""); surface = Surface.CHAT }
                            }
                            Surface.PROOF -> {
                                item { ShellText("WristPrompt", heading = true) }
                                item { ShellText(ui.status) }
                                ui.account?.let { item { ShellText(it, compact = true) } }
                                item { ShellText("Model: ${ui.model}", compact = true) }
                                if (ui.proofAvailable) {
                                    if (!state.busy && !chat.busy) {
                                        if (ui.canTest) action("Test inference", onClick = proof::test)
                                        action("Reload models", onClick = proof::loadModels)
                                        action("Choose model", enabled = state.models.isNotEmpty(),
                                            onClick = { surface = Surface.MODELS })
                                    }
                                } else if (key.loaded && !key.storageError && !changing) {
                                    if (key.mode == CredentialMode.CHATGPT_PLAN && ui.showContinue) {
                                        continueAction(ui.canContinue, actions.start)
                                    } else if (key.mode == CredentialMode.OPENAI_API_KEY && !key.configured) {
                                        action("Set up API key", setup != KeySetupStatus.REQUESTING, actions.request)
                                    }
                                }
                                if (ui.showOutput) {
                                    item { ShellText(if (state.completedObserved) "Output · response.completed observed" else "Output · not completed") }
                                    action("View output", onClick = { surface = Surface.OUTPUT })
                                }
                                if (key.mode == CredentialMode.CHATGPT_PLAN && state.problem in
                                    setOf(ApiProblem.USAGE_LIMIT, ApiProblem.USAGE_UNAVAILABLE, ApiProblem.NOT_ELIGIBLE)) {
                                    action("Manage usage", onClick = { proof.manageUsage(); surface = Surface.DETAILS })
                                }
                                if (setup != KeySetupStatus.IDLE) item { ShellText(keySetupText(setup)) }
                                action("Connection / settings", onClick = { surface = Surface.CONNECTION })
                                action("Details", onClick = { surface = Surface.DETAILS })
                            }
                            Surface.MODELS -> {
                                item { ShellText("Choose model", heading = true) }
                                item { ShellText("Selected: ${ui.model}", compact = true) }
                                if (state.mode == key.mode) for (model in state.models) action(
                                    (if (model.slug == state.selectedSlug) "✓ " else "") + model.displayName,
                                    enabled = !state.busy && !chat.busy, onClick = { proof.select(model.slug) })
                                action("Reload models", !state.busy && !chat.busy, proof::loadModels)
                                if (state.models.isEmpty()) item { ShellText("No models available. Reload to retry.") }
                            }
                            Surface.CONNECTION -> connectionItems(ui, accounts, key, setup, changing, actions, proof) {
                                proof.manageUsage(); surface = Surface.DETAILS
                            }
                            Surface.DETAILS -> {
                                item { ShellText("Details", heading = true) }
                                action("Inference proof", !chat.busy) { surface = Surface.PROOF }
                                ui.account?.let { paragraphs(it) }
                                paragraphs("Model: ${ui.model}")
                                item { ShellText(ui.status) }
                                if (key.storageError || accounts.storageError) paragraphs(messages.getValue(R.string.setup_storage_error))
                                if (accounts.signedOut) paragraphs(messages.getValue(if (accounts.revocation == RevocationResult.CONFIRMED)
                                    R.string.signout_confirmed else R.string.signout_unconfirmed))
                                if (!accounts.connected || accounts.reauthorizationRequired) paragraphs(messages.getValue(when (bridge) {
                                    WatchAuthState.IDLE -> R.string.bridge_idle
                                    WatchAuthState.LOOKING_FOR_PHONE -> R.string.looking_for_phone
                                    WatchAuthState.REQUEST_SENT -> R.string.auth_request_sent
                                    WatchAuthState.COMPANION_UNAVAILABLE -> R.string.companion_unavailable
                                    WatchAuthState.ERROR -> R.string.bridge_error
                                    WatchAuthState.MULTIPLE_PHONES -> R.string.multiple_phones
                                }))
                                if (accounts.failed) paragraphs(messages.getValue(R.string.authorization_failed))
                                if (accounts.expired) paragraphs(messages.getValue(R.string.authorization_expired))
                                if (state.mode == key.mode) {
                                    state.problem?.let { paragraphs(messages.getValue(problemText(it))) }
                                    if (state.stage == ProofStage.CANCELLED) paragraphs(messages.getValue(R.string.proof_cancelled))
                                    if (state.stage == ProofStage.OUTPUT_MISMATCH) paragraphs(messages.getValue(R.string.proof_mismatch))
                                    state.protocolDetail?.let { item { ShellText("Category: ${it.name}") } }
                                    state.httpStatus?.let { item { ShellText("HTTP status: $it") } }
                                    // Only existing allowlisted static categories/status are displayed. No raw bodies/exceptions.
                                    if (state.completedObserved) item { ShellText("Observed: response.completed") }
                                }
                                if (accounts.sessionRenewed) item { ShellText(stringResource(R.string.session_renewed)) }
                                if (key.mode == CredentialMode.OPENAI_API_KEY) paragraphs(messages.getValue(R.string.api_model_warning))
                                if (key.mode == CredentialMode.CHATGPT_PLAN && state.usageHint) paragraphs(messages.getValue(R.string.manage_usage_hint))
                                paragraphs(if (key.mode == CredentialMode.CHATGPT_PLAN) "Test inference sends: Reply with exactly: WristPrompt works."
                                    else "Test inference sends: Reply exactly: BYOK path works. API billing applies.")
                            }
                            Surface.OUTPUT -> {
                                item { ShellText("Proof output", heading = true) }
                                item { ShellText(ui.status) }
                                if (ui.showOutput) paragraphs(state.output)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShellList(modifier: Modifier, content: TransformingLazyColumnScope.() -> Unit) {
    val scroll = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = scroll, modifier = modifier.clipToBounds(), timeText = {},
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) { padding ->
        TransformingLazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
internal fun ShellText(text: String, heading: Boolean = false, compact: Boolean = false) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), textAlign = TextAlign.Center,
        style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
}

internal fun TransformingLazyColumnScope.action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    item { Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }) }
}

internal fun TransformingLazyColumnScope.continueAction(enabled: Boolean, onClick: () -> Unit) {
    item {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            icon = { Image(painterResource(R.drawable.ic_chatgpt), contentDescription = null, modifier = Modifier.size(21.dp)) },
            label = { Text(stringResource(R.string.continue_with_chatgpt)) })
    }
}

// Break long guidance/output into independently scrollable rows instead of one viewport-sized item.
internal fun TransformingLazyColumnScope.paragraphs(text: String) {
    for (part in readableChunks(text)) item { ShellText(part) }
}

internal fun readableChunks(text: String): List<String> = buildList {
    var offset = 0
    while (offset < text.length) {
        var end = minOf(offset + 80, text.length)
        if (end < text.length) {
            val space = text.lastIndexOf(' ', end - 1)
            if (space >= offset + 40) end = space + 1
            // Preserve Unicode surrogate pairs and all original output characters.
            if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        }
        add(text.substring(offset, end))
        offset = end
    }
}

