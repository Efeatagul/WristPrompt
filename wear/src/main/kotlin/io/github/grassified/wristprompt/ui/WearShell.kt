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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.wear.compose.material3.*
import io.github.grassified.wristprompt.R
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.WatchAuthViewModel
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.openai.*

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
    supplementalGuidance: String? = null, presentationMetrics: PresentationMetrics? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersion = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    val state by proof.state.collectAsStateWithLifecycle()
    // Only the low-frequency busy boundary belongs to the shell. Transcript text is read in ChatScreen.
    val busySource = remember(proof) { proof.chat.map { it.busy }.distinctUntilChanged() to proof.chat.value.busy }
    val chatBusy by busySource.first.collectAsStateWithLifecycle(initialValue = busySource.second)
    val navigation: ShellNavigationViewModel = viewModel(factory = viewModelFactory {
        initializer { ShellNavigationViewModel(initialSurface) }
    })
    val surface by navigation.surface
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
    val back = { navigation.back() }
    val interaction = ShellInteraction(proof, navigation, write)
    BackHandler(enabled = welcome || surface != Surface.CHAT) {
        if (welcome) actions.confirm() else back()
    }
    AppScaffold(timeText = {}) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f)) {
                when {
                    welcome -> ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, Surface.CHAT, welcome = true)) {
                        item { ShellText("ChatGPT plan usage", heading = true) }
                        paragraphs(messages.getValue(R.string.plan_welcome))
                        action(messages.getValue(R.string.got_it), onClick = actions.confirm)
                    }
                    surface == Surface.CHAT -> ChatScreen(proof, state, ui, navigation, supplementalGuidance, presentationMetrics)
                    surface == Surface.COMPOSER -> ComposerScreen(proof, state, ui, navigation,
                        interruptedInput, inputUnavailable, write)
                    surface == Surface.HISTORY -> HistoryScreen(proof, navigation)
                    else -> ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, surface)) {
                        action("Back", onClick = back)

                        when (surface) {
                            Surface.MENU -> {
                                item { ShellText("Menu", heading = true) }
                                action("New Chat", onClick = interaction::newChat)
                                action("Chats") { proof.refreshHistory(); navigation.open(Surface.HISTORY) }
                                action("Account & Connection") { navigation.open(Surface.CONNECTION) }
                                action("Settings & About") { navigation.open(Surface.SETTINGS) }
                            }
                            Surface.SETTINGS -> {
                                item { ShellText("Settings & About", heading = true) }
                                item { ShellText("About WristPrompt", heading = true) }
                                item { ShellText("WristPrompt · ${appVersion}") }
                                paragraphs("Chats are saved privately on this watch. Use Chats to reopen, rename or delete them. Unsent drafts are not saved after the app process ends.")
                                action("Diagnostics") { navigation.open(Surface.DETAILS) }
                            }
                            Surface.PROOF -> {
                                item { ShellText("Inference proof", heading = true) }
                                paragraphs("Diagnostic test only. This is separate from your chat replies.")
                                paragraphs("Expected output: ${ResponsesProtocol.expected(state.mode)}")
                                state.attemptId?.let { item { ShellText("Test attempt: $it") } }
                                item { ShellText(ui.status) }
                                ui.account?.let { item { ShellText(it, compact = true) } }
                                item { ShellText("Model: ${ui.model}", compact = true) }
                                if (ui.proofAvailable) {
                                    if (!state.busy && !chatBusy) {
                                        if (ui.canTest) action("Test inference", onClick = proof::test)
                                        action("Reload models", onClick = proof::loadModels)
                                        action("Choose model", enabled = state.models.isNotEmpty(),
                                            onClick = { navigation.open(Surface.MODELS) })
                                    }
                                } else if (key.loaded && !key.storageError && !changing) {
                                    if (key.mode == CredentialMode.CHATGPT_PLAN && ui.showContinue) {
                                        continueAction(ui.canContinue, actions.start)
                                    } else if (key.mode == CredentialMode.OPENAI_API_KEY && !key.configured) {
                                        action("Set up API key", setup != KeySetupStatus.REQUESTING, actions.request)
                                    }
                                }
                                if (ui.showOutput) {
                                    item { ShellText(if (state.completedObserved) "Test output · response.completed observed" else "Test output · not completed") }
                                    paragraphs(state.output)
                                }
                                if (key.mode == CredentialMode.CHATGPT_PLAN && state.problem in
                                    setOf(ApiProblem.USAGE_LIMIT, ApiProblem.USAGE_UNAVAILABLE, ApiProblem.NOT_ELIGIBLE)) {
                                    action("Manage usage", onClick = { proof.manageUsage(); navigation.open(Surface.DETAILS) })
                                }
                                if (setup != KeySetupStatus.IDLE) item { ShellText(keySetupText(setup)) }
                                action("Account & Connection", onClick = { navigation.open(Surface.CONNECTION) })
                                action("Diagnostics", onClick = { navigation.open(Surface.DETAILS) })
                            }
                            Surface.MODELS -> {
                                item { ShellText("Choose model", heading = true) }
                                val catalog = ui.recovery ?: modelCatalogPresentation(state)
                                catalog?.let { item { ShellText(it.text) } }
                                if (state.mode == key.mode) modelItems(state.models, state.selectedSlug,
                                    enabled = ui.proofAvailable && !state.busy && !chatBusy, onSelect = proof::select)
                                val recovery = catalog?.action ?: RecoveryAction.MODELS
                                recoveryAction(recovery, proof, navigation, !state.busy && !chatBusy &&
                                    (recovery != RecoveryAction.MODELS || ui.proofAvailable))

                            }
                            Surface.CONNECTION -> connectionItems(ui, accounts, key, setup, changing, actions, proof, { navigation.open(Surface.DETAILS) }) {
                                proof.manageUsage(); navigation.open(Surface.DETAILS)
                            }
                            Surface.DETAILS -> {
                                item { ShellText("Diagnostics", heading = true) }
                                action("Inference proof", !chatBusy) { navigation.open(Surface.PROOF) }
                                ui.account?.let { paragraphs(it) }
                                paragraphs(billingDisclosure(ui.billing))
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
                            Surface.CHAT, Surface.COMPOSER, Surface.HISTORY -> Unit
                        }
                    }
                }
            }
            PrimaryControl(surface, welcome, state, chatBusy, ui, proof, interaction, navigation)
        }
    }
}

@Composable
private fun PrimaryControl(
    surface: Surface, welcome: Boolean, state: ProofState, chatBusy: Boolean,
    ui: ShellPresentation, proof: InferenceViewModel, interaction: ShellInteraction, navigation: ShellNavigationViewModel,
) {
    val action = primaryAction(surface, state.busy || chatBusy, welcome) ?: return
    val recoverySource = remember(proof) { proof.chat.map { if (it.limitReached) RecoveryAction.NEW_CHAT else chatError(it)?.action }.distinctUntilChanged() to chatError(proof.chat.value)?.action }
    val chatRecovery by recoverySource.first.collectAsStateWithLifecycle(initialValue = recoverySource.second)
    val recovery = if (surface == Surface.CHAT && action == PrimaryAction.ASK)
        ui.recovery?.action ?: chatRecovery ?: if (state.selectedSlug == null) modelCatalogPresentation(state)?.action else null
        else null
    val canSend = if (action == PrimaryAction.SEND) {
        val sendSource = remember(proof) { proof.chat.map { it.canSend }.distinctUntilChanged() to proof.chat.value.canSend }
        val ready by sendSource.first.collectAsStateWithLifecycle(initialValue = sendSource.second)
        ready && ui.proofAvailable && state.selectedSlug != null
    } else true
    if (action == PrimaryAction.SEND) Text(billingDisclosure(ui.billing),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    // Latest shares the existing measured control band. Stop retains its own full tap target.
    val latest = surface == Surface.CHAT && navigation.latestVisible.value
    val largeFont = LocalDensity.current.fontScale > 1.2f
    Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (latest) TextButton(onClick = navigation::latest,
            modifier = (if (largeFont) Modifier.width(48.dp) else Modifier.weight(1f)).heightIn(min = 48.dp)
                .semantics { contentDescription = "Latest" }) {
            Text(if (largeFont) "↓" else "Latest")
        }
        Button(onClick = if (recovery != null && recovery != RecoveryAction.ASK) {
            { performRecovery(recovery, proof, navigation) }
        } else when (action) {
            PrimaryAction.ASK -> interaction::ask
            PrimaryAction.SEND -> interaction::send
            PrimaryAction.STOP -> interaction::stop
        }, enabled = canSend, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), label = { Text(recovery?.primaryLabel(largeFont) ?: action.label) })
    }
}

@Composable
internal fun rememberShellScroll(
    navigation: ShellNavigationViewModel, surface: Surface, welcome: Boolean = false,
): TransformingLazyColumnState {
    val scroll = remember(navigation, surface, welcome) {
        navigation.position(surface, welcome)?.let { TransformingLazyColumnState(it.index, it.offset) }
            ?: TransformingLazyColumnState()
    }
    DisposableEffect(navigation, surface, welcome, scroll) {
        onDispose { navigation.rememberPosition(surface, scroll.anchorItemIndex, scroll.anchorItemScrollOffset, welcome) }
    }
    return scroll
}

@Composable
internal fun ShellList(modifier: Modifier, scroll: TransformingLazyColumnState, spacing: androidx.compose.ui.unit.Dp = 10.dp, content: ShellListScope.() -> Unit) {
    ScreenScaffold(scrollState = scroll, modifier = modifier.clipToBounds(), timeText = {},
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)) { padding ->
        TransformingLazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally) { content(ShellListScope(this, spacing)) }
    }
}

@Composable
internal fun ShellText(text: String, heading: Boolean = false, compact: Boolean = false, paragraph: Boolean = false) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), textAlign = if (paragraph) TextAlign.Start else TextAlign.Center,
        style = if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
}

internal fun ShellListScope.action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    item { Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }) }
}

internal fun ShellListScope.continueAction(enabled: Boolean, onClick: () -> Unit) {
    item {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            icon = { Image(painterResource(R.drawable.ic_chatgpt), contentDescription = null, modifier = Modifier.size(21.dp)) },
            label = { Text(stringResource(R.string.continue_with_chatgpt)) })
    }
}

// Real paragraphs keep their separation; bounded continuations share zero item spacing.
internal fun ShellListScope.paragraphs(text: String) {
    for (part in shellTextSegments(text)) item(contentType = if (part.continuation) SHELL_CONTINUATION else "shell-paragraph") {
        ShellText(part.text, paragraph = true)
    }
}

internal fun readableChunks(text: String): List<String> = shellTextSegments(text).map { it.text }


// Explicit existing commands only; no resend, automatic discovery, OAuth or credential changes.
private fun performRecovery(action: RecoveryAction, proof: InferenceViewModel, navigation: ShellNavigationViewModel) {
    when (action) {
        RecoveryAction.ACCOUNT -> navigation.open(Surface.CONNECTION)
        RecoveryAction.MODELS -> proof.loadModels()
        RecoveryAction.USAGE -> { proof.manageUsage(); navigation.open(Surface.DETAILS) }
        RecoveryAction.DIAGNOSTICS -> navigation.open(Surface.DETAILS)
        RecoveryAction.NEW_CHAT -> { proof.newChat(); navigation.open(Surface.CHAT) }
        RecoveryAction.ASK -> navigation.open(Surface.COMPOSER)
    }
}

private fun ShellListScope.recoveryAction(
    recovery: RecoveryAction, proof: InferenceViewModel, navigation: ShellNavigationViewModel, enabled: Boolean,
) { action(recovery.label, enabled) { performRecovery(recovery, proof, navigation) } }
