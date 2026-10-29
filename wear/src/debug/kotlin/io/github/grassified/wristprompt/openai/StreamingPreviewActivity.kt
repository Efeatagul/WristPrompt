package io.github.grassified.wristprompt.openai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material3.*
import io.github.grassified.wristprompt.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*

/** Explicit debug-only preview: no Application stores, bridge, real credentials or network clients. */
class StreamingPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTurnScreenOn(true)
        setContent {
            val vm: InferenceViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val lease = CredentialLease("synthetic-preview", 0, "synthetic-not-a-token", Long.MAX_VALUE)
                    InferenceViewModel(MutableStateFlow(InferenceAccessState(client = lease.clientId, usable = true)),
                        InferenceSession({ lease }, PreviewProvider()) { true })
                }
            })
            val chat by vm.chat.collectAsState()
            val proof by vm.state.collectAsState()
            // Match production: never pair a live delegated state read with an older row cache.
            val renderedChat = chat
            val chunks = remember(renderedChat.messages) { renderedChat.messages.associate { it.id to readableChunks(it.text) } }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Synthetic preview", modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelMedium)
                        Button(onClick = { if (chat.busy) vm.stop() else { vm.draft("short"); vm.send() } },
                            enabled = !proof.busy,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                            label = { Text(if (chat.busy) "Stop" else "Next prompt") })
                        ShellList(Modifier.weight(1f)) {
                            action("Long stream", !chat.busy && !proof.busy) { vm.draft("long"); vm.send() }
                            chatItems(renderedChat, chunks)
                            action("New Chat", onClick = vm::newChat)
                            action("Recreate screen") { recreate() }
                        }
                    }
                }
            }
        }
    }
}

private class PreviewProvider : AiProvider {
    override suspend fun models(credential: CredentialLease) = listOf(AiModel("synthetic-model", "Synthetic"))
    override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) =
        error("Preview uses chat only")
    override suspend fun stream(credential: CredentialLease, model: String, input: List<JsonObject>, onEvent: suspend (ResponseEvent) -> Unit) {
        val long = input.last()["content"]?.jsonPrimitive?.content == "long"
        val text = StringBuilder()
        try {
            repeat(if (long) 80 else 10) { index ->
                val delta = if (long) "Line ${index + 1}: readable synthetic output for scrolling. " else "Word ${index + 1}. "
                text.append(delta); onEvent(ResponseEvent.Delta(delta)); delay(250)
            }
            val answer = "$text Completed synthetic response; input items: ${input.size}."
            onEvent(ResponseEvent.Completed(answer))
        } catch (cancelled: CancellationException) {
            // Deliberately attempt an old completion after Stop; the session's owning Job must reject it.
            withContext(NonCancellable) {
                delay(1200)
                try { onEvent(ResponseEvent.Completed("Late synthetic completion must never appear")) }
                catch (_: CancellationException) { }
            }
            throw cancelled
        }
    }
}
