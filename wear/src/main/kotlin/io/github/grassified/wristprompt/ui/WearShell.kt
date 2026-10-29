package io.github.grassified.wristprompt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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

private enum class Surface { PROOF, MODELS, CONNECTION, DETAILS, OUTPUT }

@Composable
internal fun WearShell(auth: WatchAuthViewModel, settings: ApiKeySettingsViewModel, proof: InferenceViewModel) {
    val accounts by auth.accounts.collectAsStateWithLifecycle()
    val bridge by auth.state.collectAsStateWithLifecycle()
    val key by settings.state.collectAsStateWithLifecycle()
    val setup by settings.setup.collectAsStateWithLifecycle()
    val changing by settings.busy.collectAsStateWithLifecycle()
    val state by proof.state.collectAsStateWithLifecycle()
    var surface by remember { mutableStateOf(Surface.PROOF) }
    val ui = shellPresentation(accounts, key, state, bridge, changing)
    val messages = (ApiProblem.entries.map(::problemText) + listOf(
        R.string.setup_storage_error, R.string.signout_confirmed, R.string.signout_unconfirmed,
        R.string.authorization_failed, R.string.authorization_expired, R.string.proof_cancelled,
        R.string.proof_mismatch, R.string.api_model_warning, R.string.manage_usage_hint,
        R.string.plan_welcome, R.string.got_it, R.string.bridge_idle, R.string.looking_for_phone,
        R.string.auth_request_sent, R.string.companion_unavailable, R.string.bridge_error, R.string.multiple_phones,
    )).associateWith { stringResource(it) }
    val welcome = accounts.planWelcome
    val back = { surface = Surface.PROOF }
    BackHandler(enabled = welcome || surface != Surface.PROOF) {
        if (welcome) auth.confirmPlanNotice() else back()
    }
    AppScaffold(timeText = {}) {
        // A measured, non-overlaid header: long labels/font scaling take their own space.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.billing, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Text(ui.billingNote, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            // Stop stays outside the scroll area on every surface, including output/details.
            if (ui.showStop) Button(onClick = proof::stop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp), label = { Text("Stop") })
            androidx.compose.runtime.key(surface, welcome) {
                ShellList(Modifier.weight(1f)) {
                    if (welcome) {
                        item { ShellText("ChatGPT plan usage", heading = true) }
                        paragraphs(messages.getValue(R.string.plan_welcome))
                        action(messages.getValue(R.string.got_it), onClick = auth::confirmPlanNotice)
                    } else {
                        if (surface != Surface.PROOF) action("Back", onClick = back)
                        if (surface == Surface.MODELS || surface == Surface.CONNECTION) item { ShellText(ui.status) }
                        when (surface) {
                            Surface.PROOF -> {
                                item { ShellText("WristPrompt", heading = true) }
                                item { ShellText(ui.status) }
                                ui.account?.let { item { ShellText(it, compact = true) } }
                                item { ShellText("Model: ${ui.model}", compact = true) }
                                if (ui.proofAvailable) {
                                    if (!state.busy) {
                                        if (ui.canTest) action("Test inference", onClick = proof::test)
                                        action("Reload models", onClick = proof::loadModels)
                                        action("Choose model", enabled = state.models.isNotEmpty(),
                                            onClick = { surface = Surface.MODELS })
                                    }
                                } else if (key.loaded && !key.storageError && !changing) {
                                    if (key.mode == CredentialMode.CHATGPT_PLAN && ui.showContinue) {
                                        continueAction(ui.canContinue, auth::start)
                                    } else if (key.mode == CredentialMode.OPENAI_API_KEY && !key.configured) {
                                        action("Set up API key", setup != KeySetupStatus.REQUESTING, settings::request)
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
                                    enabled = !state.busy, onClick = { proof.select(model.slug) })
                                if (state.models.isEmpty()) item { ShellText("Reload models from the proof screen.") }
                            }
                            Surface.CONNECTION -> connectionItems(ui, accounts, key, setup, changing, auth, settings, proof) {
                                proof.manageUsage(); surface = Surface.DETAILS
                            }
                            Surface.DETAILS -> {
                                item { ShellText("Details", heading = true) }
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
private fun ShellList(modifier: Modifier, content: TransformingLazyColumnScope.() -> Unit) {
    val scroll = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = scroll, modifier = modifier, timeText = {},
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

