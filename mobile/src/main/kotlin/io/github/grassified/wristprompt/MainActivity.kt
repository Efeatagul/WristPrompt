package io.github.grassified.wristprompt

import android.content.Intent
import android.graphics.Color
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.presets.PresetsViewModel
import io.github.grassified.wristprompt.presets.PresetsUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            val auth: PhoneAuthViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = application as WristPromptApplication
                    PhoneAuthViewModel(app.authInbox, app.connectivity::request, app.issuedRegistrations, exchange = app.authorization::exchange)
                }
            })
            val api: PhoneApiKeyViewModel = viewModel(factory = viewModelFactory {
                initializer { val app = application as WristPromptApplication; PhoneApiKeyViewModel(app.apiKeyInbox, app.connectivity::request) }
            })
            val presets: PresetsViewModel = viewModel(factory = viewModelFactory {
                initializer { PresetsViewModel((application as WristPromptApplication).presets) }
            })
            val photos: io.github.grassified.wristprompt.images.PhoneImagesViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = application as WristPromptApplication
                    io.github.grassified.wristprompt.images.PhoneImagesViewModel(app.images, contentResolver, app::queueImages)
                }
            })
            val presetApp = application as WristPromptApplication
            val photoSync by presetApp.imageSync.status.collectAsStateWithLifecycle()
            val presetSync by presetApp.presetPublisher.status.collectAsStateWithLifecycle()
            val state by auth.state.collectAsStateWithLifecycle()
            var usageBrowserUnavailable by remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold { contentPadding ->
                    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.companion_setup), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.companion_explanation))
                        Text("Continue with ChatGPT", style = MaterialTheme.typography.titleMedium)
                        Text("Use an eligible ChatGPT plan. OpenAI API keys use separate API billing.")
                        TextButton(onClick = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, "https://chatgpt.com/settings/usage".toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
                            } catch (_: android.content.ActivityNotFoundException) { usageBrowserUnavailable = true }
                        }) { Text("Manage ChatGPT usage") }
                        if (usageBrowserUnavailable) Text("Open ChatGPT → Settings → Usage in a browser on your phone.")
                        Text(stringResource(when (state.stage) {
                            PhoneStage.WAITING -> R.string.waiting_for_watch
                            PhoneStage.READY -> R.string.sign_in_requested
                            PhoneStage.AUTHORIZING -> R.string.authorizing
                            PhoneStage.TRANSFERRING -> R.string.transferring
                            PhoneStage.RETRY_TRANSFER -> R.string.transfer_failed
                            PhoneStage.CONNECTED -> R.string.connected
                            PhoneStage.IDENTITY_ONLY -> R.string.identity_only
                            PhoneStage.FAILED -> problemMessage(state.problem)
                        }))
                        if (state.stage == PhoneStage.READY) Button(onClick = {
                            auth.start { url ->
                                // Ephemeral browser intent only: never saved, printed, or placed in extras.
                                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Black, contentColor = androidx.compose.ui.graphics.Color.White)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.ic_chatgpt), contentDescription = null, modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.continue_with_chatgpt))
                            }
                        }
                        if (state.stage == PhoneStage.RETRY_TRANSFER) Button(onClick = auth::retryTransfer) {
                            Text(stringResource(R.string.retry_transfer))
                        }
                        Text(stringResource(R.string.no_inference))
                        HorizontalDivider()
                        ApiKeySetupUi(api)
                        HorizontalDivider()
                        PresetsUi(presets, presetSync, presetApp::queuePresets)
                        HorizontalDivider()
                        io.github.grassified.wristprompt.images.PhoneImagesUi(photos, photoSync, presetApp::queueImages)
                    }
                }
            }
        }
    }
}

private fun problemMessage(problem: AuthProblem?): Int = when (problem) {
    AuthProblem.BROWSER -> R.string.error_browser
    AuthProblem.LISTENER -> R.string.error_listener
    AuthProblem.TIMEOUT -> R.string.error_timeout
    AuthProblem.DENIED -> R.string.error_denied
    AuthProblem.STATE, AuthProblem.CALLBACK -> R.string.error_callback
    AuthProblem.EXCHANGE -> R.string.error_exchange
    AuthProblem.IDENTITY -> R.string.error_identity
    AuthProblem.SCOPES -> R.string.error_scopes
    AuthProblem.WATCH_UNAVAILABLE -> R.string.error_watch
    AuthProblem.TRANSFER -> R.string.transfer_failed
    AuthProblem.STORAGE, null -> R.string.setup_storage_error
}
