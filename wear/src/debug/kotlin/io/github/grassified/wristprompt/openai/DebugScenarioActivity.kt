package io.github.grassified.wristprompt.openai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material3.MaterialTheme
import io.github.grassified.wristprompt.auth.KeySetupStatus
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.ui.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class ScenarioOwner(val fixture: DebugScenarios) : ViewModel() {
    var ready by mutableStateOf(false)
    var recreated = false
    init { viewModelScope.launch { fixture.prepare(); ready = true } }
    override fun onCleared() { fixture.vm.viewModelScope.cancel() }
}

/** Explicit ADB entrypoint only. No production navigation references this class. */
class DebugScenarioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = DebugScenario.parse(intent.getStringExtra("scenario")) ?: run { finish(); return }
        val launchId = intent.getStringExtra("launch_id")?.takeIf { Regex("[a-f0-9]{32}").matches(it) }
            ?: run { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTurnScreenOn(true)
        setContent {
            val owner: ScenarioOwner = viewModel(factory = viewModelFactory { initializer { ScenarioOwner(DebugScenarios(scenario)) } })
            val f = owner.fixture
            MaterialTheme {
                WearShellContent(f.accounts, WatchAuthState.IDLE, f.key, KeySetupStatus.IDLE, false, f.vm,
                    ConnectionActions({}, {}, {}, {}, {}, {}, {}), f.surface,
                    if (scenario == DebugScenario.LONG_ERROR_MESSAGE)
                        (getString(problemText(ApiProblem.TEMPORARY)) + " " + chatRecovery(ApiProblem.TEMPORARY) + " ").repeat(15) else null)
            }
            LaunchedEffect(owner.ready) {
                if (owner.ready) {
                    if (scenario in setOf(DebugScenario.RECREATED_COMPLETED_STATE, DebugScenario.RECREATED_CANCELLED_STATE) && !owner.recreated) {
                        owner.recreated = true; recreate()
                    } else {
                        // Fixed enum/status/count only, no content or credentials. Fresh token supplied by launcher.
                        openFileOutput("debug-scenario-ready.txt", MODE_PRIVATE).bufferedWriter().use {
                            it.write("${launchId} ${scenario.name} READY generations=${f.generations} stage=${f.vm.chat.value.stage}")
                        }
                    }
                }
            }
        }
    }
}
