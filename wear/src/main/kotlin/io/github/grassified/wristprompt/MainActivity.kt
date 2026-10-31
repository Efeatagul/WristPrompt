package io.github.grassified.wristprompt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.ui.WristPromptTheme
import io.github.grassified.wristprompt.auth.ApiKeySettingsViewModel
import io.github.grassified.wristprompt.bridge.WatchAuthViewModel
import io.github.grassified.wristprompt.openai.InferenceViewModel

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
                    InferenceViewModel(app.credentials.state, app.inference, app.credentials::selectedModel, app.credentials::selectModel,
                        app.conversations, app.credentials::conversationIdentity)
                }
            })
            WristPromptTheme {
                io.github.grassified.wristprompt.ui.WearShell(auth, settings, proof)
            }
        }
    }
}
