package io.github.grassified.wristprompt

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
            val proof: InferenceViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = application as WristPromptApplication
                    InferenceViewModel(app.profiles.state, app.inference, app.profiles::selectedModel, app.profiles::selectModel)
                }
            })
            val state by auth.state.collectAsStateWithLifecycle()
            val accounts by auth.accounts.collectAsStateWithLifecycle()
            MaterialTheme {
                OnboardingScreen(state, accounts, auth::start, auth::select, { proof.stop(); auth.signOut() }, proof)
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
private fun OnboardingScreen(state: WatchAuthState, accounts: io.github.grassified.wristprompt.auth.WatchAccountState, onContinue: () -> Unit, onSelect: (String?) -> Unit, onSignOut: () -> Unit, proof: InferenceViewModel) {
    val proofState by proof.state.collectAsStateWithLifecycle()
    val scrollState = rememberTransformingLazyColumnState()
    AppScaffold {
        ScreenScaffold(scrollState = scrollState) { contentPadding ->
            TransformingLazyColumn(
                state = scrollState,
                contentPadding = contentPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
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
                        text = stringResource(R.string.tagline),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (accounts.connected && accounts.planEnabled && !accounts.authorizing && !accounts.storageError) {
                    inferenceProofItems(proofState, proof)
                    if (accounts.sessionRenewed) item { Text(stringResource(R.string.session_renewed)) }
                } else if (accounts.connected) item {
                    Text(stringResource(if (accounts.reauthorizationRequired) R.string.proof_reauthorize else R.string.plan_disabled), textAlign = TextAlign.Center)
                }
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
                item {
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
                item {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.use_api_key)) },
                    )
                }
                item {
                    Text(
                        text = stringResource(when {
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
