package io.github.grassified.wristprompt

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.openai.InferenceViewModel
import io.github.grassified.wristprompt.openai.inferenceProofItems
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.bridge.WatchAuthViewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val auth: WatchAuthViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = application as WristPromptApplication
                    WatchAuthViewModel(app.authBridge, app.profiles, app.revocation::revoke, app.inference::cancel)
                }
            })
            val settings: ApiKeySettingsViewModel = viewModel(factory = viewModelFactory {
                initializer { val app = application as WristPromptApplication; ApiKeySettingsViewModel(app.apiKeys, app.apiKeyBridge, app.inference) }
            })
            val proof: InferenceViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = application as WristPromptApplication
                    InferenceViewModel(app.credentials.state, app.inference, app.credentials::selectedModel, app.credentials::selectModel)
                }
            })
            val state by auth.state.collectAsStateWithLifecycle()
            val accounts by auth.accounts.collectAsStateWithLifecycle()
            MaterialTheme {
                OnboardingScreen(state, accounts, auth::start, auth::select, { if (settings.state.value.mode == CredentialMode.CHATGPT_PLAN) proof.stop(); auth.signOut() }, proof, settings)
                if (accounts.planWelcome) Dialog(onDismissRequest = auth::confirmPlanNotice) {
                    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.plan_welcome), textAlign = TextAlign.Center)
                        Button(onClick = auth::confirmPlanNotice, label = { Text(stringResource(R.string.got_it)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(state: WatchAuthState, accounts: io.github.grassified.wristprompt.auth.WatchAccountState, onContinue: () -> Unit, onSelect: (String?) -> Unit, onSignOut: () -> Unit, proof: InferenceViewModel, settings: ApiKeySettingsViewModel) {
    val proofState by proof.state.collectAsStateWithLifecycle()
    val keyState by settings.state.collectAsStateWithLifecycle()
    val setup by settings.setup.collectAsStateWithLifecycle()
    val changing by settings.busy.collectAsStateWithLifecycle()
    val scrollState = rememberTransformingLazyColumnState()
    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(contentPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    !keyState.loaded -> "Loading saved billing mode…"
                    keyState.storageError -> "Active mode unavailable"
                    keyState.mode == CredentialMode.CHATGPT_PLAN -> "Active mode: ChatGPT plan"
                    else -> "Active mode: OpenAI API · separate API billing"
                }, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                TransformingLazyColumn(
                    state = scrollState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.connection),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    item { Text("Use for AI requests:") }
                    for (mode in CredentialMode.entries) item {
                        Button(onClick = { proof.stop(); settings.selectMode(mode) }, enabled = keyState.loaded && !keyState.storageError && !changing,
                            modifier = Modifier.fillMaxWidth(), label = { Text((if (keyState.mode == mode && keyState.loaded && !keyState.storageError) "● " else "○ ") +
                                if (mode == CredentialMode.CHATGPT_PLAN) "ChatGPT plan" else "OpenAI API") })
                    }
                    if (keyState.storageError) item { Text(stringResource(R.string.setup_storage_error)) }
                    if (accounts.connected && !accounts.signedOut) {
                        item { Text("Connected account") }
                        item { Text(accounts.accounts.firstOrNull { it.clientId == accounts.activeClientId }?.label ?: "ChatGPT account", textAlign = TextAlign.Center) }
                        item { Text(if (keyState.loaded && !keyState.storageError && keyState.mode == CredentialMode.CHATGPT_PLAN && accounts.planEnabled && !accounts.reauthorizationRequired)
                            "Using ChatGPT plan" else if (accounts.planEnabled) "ChatGPT plan available" else "ChatGPT plan usage disabled") }
                        item { Button(onClick = proof::manageUsage, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.manage_usage)) }) }
                    }
                    if (proofState.usageHint) item { Text(stringResource(R.string.manage_usage_hint), textAlign = TextAlign.Center) }
                    if (accounts.signingOut) item { Text(stringResource(R.string.signing_out)) }
                    if (accounts.signedOut) item {
                        Text(stringResource(if (accounts.revocation == io.github.grassified.wristprompt.auth.RevocationResult.CONFIRMED)
                            R.string.signout_confirmed else R.string.signout_unconfirmed))
                    }
                    if (accounts.activeClientId != null && !accounts.signedOut) item {
                        Button(onClick = onSignOut, enabled = !accounts.signingOut && !accounts.storageError,
                            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sign_out)) })
                    }
                    for (account in accounts.accounts) item {
                        Button(onClick = { onSelect(account.clientId) }, enabled = !accounts.signingOut, modifier = Modifier.fillMaxWidth(),
                            label = { Text((if (accounts.activeClientId == account.clientId) "✓ " else "") + account.label) })
                    }
                    if (accounts.accounts.isNotEmpty()) item {
                        Button(onClick = { onSelect(null) }, enabled = !accounts.signingOut, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.add_account)) })
                    }
                    if (!accounts.connected || accounts.reauthorizationRequired) item {
                        Button(
                            onClick = onContinue,
                            enabled = state != WatchAuthState.LOOKING_FOR_PHONE && !accounts.signingOut && !accounts.storageError,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(painterResource(R.drawable.ic_chatgpt), contentDescription = null, modifier = Modifier.size(21.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.continue_with_chatgpt))
                                }
                            },
                        )
                    }
                    if (!accounts.connected || accounts.reauthorizationRequired) item { Text("Use an eligible ChatGPT plan", textAlign = TextAlign.Center) }
                    if (keyState.configured) {
                        item { Text("OpenAI API key") }
                        item { Text("Configured") }
                        item { Text("API billing available") }
                    }
                    item { Button(onClick = settings::request, enabled = setup != KeySetupStatus.REQUESTING && !changing,
                        modifier = Modifier.fillMaxWidth(), label = { Text(if (keyState.configured) "Change key" else stringResource(R.string.use_api_key)) }) }
                    item { Text("Advanced · billed through the OpenAI API", textAlign = TextAlign.Center) }
                    if (setup != KeySetupStatus.IDLE) item { Text(when (setup) {
                        KeySetupStatus.IDLE -> "API-key setup needs an explicit request from this watch."
                        KeySetupStatus.REQUESTING -> "Finding the paired phone…"
                        KeySetupStatus.PHONE_READY -> "Open WristPrompt on your phone and enter the key within two minutes."
                        KeySetupStatus.UNAVAILABLE -> "Paired phone unavailable. No credential or billing change."
                        KeySetupStatus.ERROR -> "API-key setup failed. No automatic billing fallback."
                    }) }
                    if (keyState.configured) item { Button(onClick = { if (keyState.mode == CredentialMode.OPENAI_API_KEY) proof.stop(); settings.remove() }, enabled = !changing,
                        modifier = Modifier.fillMaxWidth(), label = { Text("Remove key") }) }
                    if (proofState.mode == keyState.mode && keyState.loaded && keyState.mode == CredentialMode.OPENAI_API_KEY && !changing && !keyState.storageError) {
                        if (keyState.configured) inferenceProofItems(proofState, proof)
                        else item { Text(stringResource(R.string.api_key_missing)) }
                    }
                    if (proofState.mode == keyState.mode && keyState.loaded && keyState.mode == CredentialMode.CHATGPT_PLAN && !changing && !keyState.storageError && accounts.connected && accounts.planEnabled && !accounts.authorizing && !accounts.storageError) {
                        inferenceProofItems(proofState, proof)
                        if (accounts.sessionRenewed) item { Text(stringResource(R.string.session_renewed)) }
                    } else if (keyState.mode == CredentialMode.CHATGPT_PLAN && accounts.connected && (!accounts.planEnabled || accounts.reauthorizationRequired)) item {
                        Text(stringResource(if (accounts.reauthorizationRequired) R.string.proof_reauthorize else R.string.plan_disabled), textAlign = TextAlign.Center)
                    }
                    item {
                        Text(
                            text = stringResource(when {
                                keyState.storageError -> R.string.setup_storage_error
                                keyState.mode == CredentialMode.OPENAI_API_KEY -> if (keyState.configured) R.string.api_key_configured else R.string.api_key_missing
                                accounts.storageError -> R.string.setup_storage_error
                                accounts.signingOut -> R.string.signing_out
                                accounts.signedOut -> R.string.signed_out
                                accounts.expired -> R.string.authorization_expired
                                accounts.authorizing -> R.string.authorizing
                                accounts.failed -> R.string.authorization_failed
                                accounts.connected -> R.string.connected
                                else -> when (state) {
                                WatchAuthState.IDLE -> R.string.bridge_idle
                                WatchAuthState.LOOKING_FOR_PHONE -> R.string.looking_for_phone
                                WatchAuthState.REQUEST_SENT -> R.string.auth_request_sent
                                WatchAuthState.COMPANION_UNAVAILABLE -> R.string.companion_unavailable
                                WatchAuthState.ERROR -> R.string.bridge_error
                                WatchAuthState.MULTIPLE_PHONES -> R.string.multiple_phones
                                }
                            }),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
