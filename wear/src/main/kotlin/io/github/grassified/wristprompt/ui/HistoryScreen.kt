package io.github.grassified.wristprompt.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.grassified.wristprompt.openai.InferenceViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun HistoryScreen(proof: InferenceViewModel, navigation: ShellNavigationViewModel) {
    val history by proof.history.collectAsStateWithLifecycle()
    val chatBusy by remember(proof) { proof.chat.map { it.busy }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = false)
    val inference by proof.state.collectAsStateWithLifecycle()
    val busy = chatBusy || inference.busy || history.loading
    var selectedId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf(false) }
    val selected = history.entries.firstOrNull { it.id == selectedId }
    val renameInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = renameId; renameId = null
        val title = if (result.resultCode == Activity.RESULT_OK)
            result.data?.let(RemoteInput::getResultsFromIntent)?.getCharSequence(CHAT_INPUT_KEY)?.toString() else null
        if (id != null && title != null) proof.renameChat(id, title)
    }
    BackHandler(enabled = selected != null) { selectedId = null; deleteConfirmation = false }
    ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, Surface.HISTORY)) {
        item { ShellText("Chats", heading = true) }
        action("New Chat") { proof.newChat(); navigation.chatPosition = null; navigation.open(Surface.CHAT) }
        if (history.loading) item { ShellText("Loading chats…") }
        history.error?.let { error -> item { ShellText(error) } }
        if (inputError) item { ShellText("Text input is unavailable. Try renaming again later.") }
        if (selected == null) {
            if (history.entries.isEmpty() && !history.loading) item { ShellText("No saved chats for this account.") }
            history.entries.forEach { entry ->
                action(entry.title) { selectedId = entry.id; deleteConfirmation = false }
            }
            action("Refresh", !history.loading) { proof.refreshHistory() }
            action("Back") { navigation.back() }
        } else {
            item { ShellText(selected.title, heading = true) }
            item { ShellText("Model: ${selected.model}", compact = true) }
            if (deleteConfirmation) {
                item { ShellText("Delete this chat from this watch? Its messages cannot be recovered here.") }
                action("Delete chat") { proof.deleteChat(selected.id); selectedId = null; deleteConfirmation = false }
                action("Cancel") { deleteConfirmation = false }
            } else {
                action("Open chat", !busy) { proof.openChat(selected.id) {
                    if (navigation.surface.value == Surface.HISTORY) { navigation.chatPosition = null; navigation.open(Surface.CHAT) }
                } }
                action("Rename", !busy) {
                    renameId = selected.id; inputError = false
                    try { renameInput.launch(chatInputIntent("Chat name")) }
                    catch (_: ActivityNotFoundException) { renameId = null; inputError = true }
                }
                action("Delete", !busy) { deleteConfirmation = true }
                action("Back to chats") { selectedId = null }
            }
        }
    }
}
