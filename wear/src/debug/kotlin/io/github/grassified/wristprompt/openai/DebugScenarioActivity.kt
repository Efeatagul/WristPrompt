package io.github.grassified.wristprompt.openai

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material3.MaterialTheme
import io.github.grassified.wristprompt.auth.KeySetupStatus
import io.github.grassified.wristprompt.bridge.WatchAuthState
import io.github.grassified.wristprompt.ui.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

internal class ScenarioOwner(val fixture: DebugScenarios) : ViewModel() {
    val presentationMetrics = PresentationMetrics()
    var ready by mutableStateOf(false)
    var recreated = false
    var creations = 0
    val identity = java.util.UUID.randomUUID().toString()
    private val store = ViewModelStore().also {
        ViewModelProvider.create(it, viewModelFactory { initializer { fixture.vm } })[InferenceViewModel::class]
    }
    init { viewModelScope.launch { fixture.prepare(); ready = true } }
    override fun onCleared() { store.clear() }
}

/** Explicit ADB entrypoint only. No production navigation references this class. */
class DebugScenarioActivity : ComponentActivity() {
    companion object {
        private var reportEpoch = 0L
        private val processOwner = java.util.UUID.randomUUID().toString()
    }
    private var activityReportEpoch = -1L
    private var navigation: ShellNavigationViewModel? = null
    private var presentationMetrics: PresentationMetrics? = null
    private var fixture: DebugScenarios? = null
    private var owner: ScenarioOwner? = null
    private var launchId: String? = null
    private var registered = false
    private var collecting = false
    private var observedLength = 0
    private var commandRevision = 0
    private var lastReportAt = 0L
    private var lastReportStage: ChatStage? = null
    private val command = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra("launch_id") != launchId) return
            val f = fixture ?: return
            if (intent?.getStringExtra("command") == "REPORT") { report(force = true); return }
            if (!f.presentationScenario && !f.lifecycleScenario && !f.processScenario && !f.networkScenario && f.scenario != DebugScenario.COMPOSER_RECREATION) return
            commandRevision++
            when (intent?.getStringExtra("command")) {
                "RESTORE_NETWORK" -> { f.restoreNetwork(); report() }
                "NEXT_REQUEST" -> f.nextRequest()
                "RELOAD_MODELS" -> if (f.networkScenario) f.vm.loadModels()
                "FAIL_STOP" -> f.failAndStop()
                "SEED" -> owner?.viewModelScope?.launch { f.seedProcess(); report() }
                "ADVANCE" -> f.advance()
                "COMPLETE" -> f.advance(complete = true)
                "STOP" -> f.vm.stop()
                "RECREATE" -> recreate()
            }
            report()
        }
    }
    private fun report(force: Boolean = false) {
        if (activityReportEpoch != reportEpoch || lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val f = fixture ?: return
        val o = owner ?: return
        if (!o.ready) return
        val chat = f.vm.chat.value
        // Fine deltas must not turn the existing debug acknowledgement into per-character disk IO.
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && f.scenario == DebugScenario.STREAMING_FINE_GRAINED &&
            chat.stage == lastReportStage && now - lastReportAt < 100) return
        lastReportAt = now; lastReportStage = chat.stage
        openFileOutput("debug-scenario-ready.txt", MODE_PRIVATE).bufferedWriter().use {
            it.write("${launchId} ${f.scenario.name} READY generations=${f.generations} stage=${chat.stage}" +
                " bridgeLookups=${f.bridgeLookups} bridgeRequests=${f.bridgeRequests} refreshes=${f.refreshes}" +
                " commandRevision=$commandRevision discoveries=${f.discoveries} completions=${f.completions} chars=${chat.partial.length}" +
                " draftChars=${chat.draft.length} messages=${chat.messages.size} owner=${o.identity} creations=${o.creations}" +
                " collecting=$collecting observedChars=$observedLength lifecycle=${lifecycle.currentState}" +
                " pid=${android.os.Process.myPid()} processOwner=$processOwner busy=${chat.busy}" +
                " proof=${f.vm.state.value.stage} model=${f.vm.state.value.selectedSlug ?: "NONE"} billing=${f.mode}" +
                " surface=${navigation?.surface?.value ?: Surface.CHAT} pendingInput=${navigation?.inputPending?.value ?: false}" +
                " excluded=${chat.messages.count { it.status == ChatMessageStatus.EXCLUDED }}" +
                " assistants=${chat.messages.count { it.role == ChatRole.ASSISTANT }} lastInput=${f.inputs.lastOrNull()?.size ?: 0}" +
                " problem=${chat.problem ?: "NONE"} discoveryProblem=${f.vm.state.value.problem ?: "NONE"}" +
                " presentationFlushes=${presentationMetrics?.flushes ?: 0} presentationSegments=${presentationMetrics?.segmentations ?: 0} presentationChars=${presentationMetrics?.projectedChars ?: 0}" +
                " keepScreenOn=${window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0}")
        }
    }
    override fun onDestroy() {
        if (registered) unregisterReceiver(command)
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = DebugScenario.parse(intent.getStringExtra("scenario")) ?: run { finish(); return }
        val launchId = intent.getStringExtra("launch_id")?.takeIf { Regex("[a-f0-9]{32}").matches(it) }
            ?: run { finish(); return }
        activityReportEpoch = ++reportEpoch
        this.launchId = launchId
        if (scenario != DebugScenario.SCREEN_OFF_STYLE_RESUME) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setTurnScreenOn(true)
        }
        ContextCompat.registerReceiver(this, command, IntentFilter("$packageName.DEBUG_LIFECYCLE"), ContextCompat.RECEIVER_EXPORTED)
        registered = true
        lifecycle.addObserver(LifecycleEventObserver { _, _ -> report() })
        setContent {
            val owner: ScenarioOwner = viewModel(factory = viewModelFactory { initializer { ScenarioOwner(DebugScenarios(scenario)) } })
            val f = owner.fixture
            val metrics = owner.presentationMetrics
            presentationMetrics = metrics
            val nav: ShellNavigationViewModel = viewModel(factory = viewModelFactory {
                initializer { ShellNavigationViewModel(f.surface) }
            })
            navigation = nav
            LaunchedEffect(nav) {
                snapshotFlow { nav.surface.value to nav.inputPending.value }.collect { report() }
            }
            DisposableEffect(owner) {
                this@DebugScenarioActivity.owner = owner
                fixture = f
                owner.creations++
                onDispose { }
            }
            LaunchedEffect(owner) {
                launch { combine(f.vm.chat, f.vm.state) { _, _ -> Unit }.collect { report() } }
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    collecting = true
                    try { f.vm.chat.collect { observedLength = it.partial.length; report() } }
                    finally { collecting = false; report() }
                }
            }
            val chat by f.vm.chat.collectAsStateWithLifecycle()
            io.github.grassified.wristprompt.ui.WristPromptTheme {
                Column(Modifier.fillMaxSize()) {
                    if (f.lifecycleScenario || f.processScenario) Text("G=${f.generations} M=${f.discoveries} ${chat.stage}\nchars=${chat.partial.length}",
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 42.dp, end = 42.dp),
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Box(Modifier.weight(1f)) {
                        WearShellContent(f.accounts, f.authState, f.key, KeySetupStatus.IDLE, false, f.vm,
                            ConnectionActions({}, {}, {}, {}, {}, {}, {}), f.surface,
                            null, metrics)
                    }
                }
            }
            LaunchedEffect(owner.ready) {
                if (owner.ready) {
                    if (scenario in setOf(DebugScenario.RECREATED_COMPLETED_STATE, DebugScenario.RECREATED_CANCELLED_STATE, DebugScenario.ACTIVE_STREAM_RECREATION) && !owner.recreated) {
                        owner.recreated = true; recreate()
                    } else {
                        report()
                    }
                }
            }
        }
    }
}
