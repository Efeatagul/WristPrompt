package io.github.grassified.wristprompt.presets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PresetsUi(model: PresetsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<PromptPreset?>(null) }
    var discard by remember { mutableStateOf(false) }
    val editor = state.editor
    fun requestClose() {
        if (editor != null && (editor.name != (editor.original?.name ?: "") || editor.text != (editor.original?.text ?: ""))) discard = true
        else model.close()
    }
    BackHandler(enabled = editor != null) { if (!state.busy) requestClose() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prompt presets", style = MaterialTheme.typography.titleLarge)
        Text("Reusable user text. Saved on this phone only; never sent or applied automatically.")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
        if (!state.loaded) {
            Button(onClick = model::reload, enabled = !state.busy) { Text("Reload presets") }
        } else {
            Button(onClick = { model.open() }, enabled = !state.busy && editor == null) { Text("Create preset") }
            if (state.catalog.records.none { !it.deleted }) Text("No presets yet.")
            state.catalog.records.filterNot { it.deleted }.sortedBy { it.name.lowercase() }.forEach { preset ->
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { model.open(preset) }, modifier = Modifier.weight(1f), enabled = !state.busy && editor == null) { Text(preset.name) }
                    TextButton(onClick = { deleting = preset }, enabled = !state.busy && editor == null) { Text("Delete") }
                }
            }
        }
    }
    if (editor != null) AlertDialog(
        onDismissRequest = { if (!state.busy) requestClose() },
        title = { Text(if (editor.original == null) "Create preset" else "Edit preset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(editor.name, { model.edit(it, editor.text) }, label = { Text("Name (1–${PresetCodec.MAX_NAME})") }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(editor.text, { model.edit(editor.name, it) }, label = { Text("Prompt (1–${PresetCodec.MAX_TEXT})") }, minLines = 5, maxLines = 12, enabled = !state.busy)
                state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = model::save, enabled = !state.busy) { Text("Save") } },
        dismissButton = { TextButton(onClick = { requestClose() }, enabled = !state.busy) { Text("Cancel") } },
    )
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard unsaved edits?") },
        confirmButton = { TextButton(onClick = { discard = false; model.close() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    deleting?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete preset?") }, text = { Text("This removes the saved text. Deletion cannot be undone.") },
            confirmButton = { TextButton(onClick = { deleting = null; model.delete(preset) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

private fun problemText(problem: PresetProblem): String = when (problem) {
    PresetProblem.NAME -> "Use a nonblank name of at most 80 characters, without surrounding spaces or control characters."
    PresetProblem.TEXT -> "Use nonblank prompt text of at most 8,192 characters. Line breaks and tabs are allowed; invalid Unicode and other control characters are not."
    PresetProblem.COUNT -> "Preset capacity reached (50 saved presets or 256 lifetime IDs). Nothing was changed."
    PresetProblem.STORAGE -> "Unable to save or load presets. The catalog limit is 512 KiB. Existing data was not reset; retry after checking storage."
    PresetProblem.INVALID_DATA -> "Saved presets are invalid or use an unsupported format. Existing data was preserved."
    PresetProblem.CONFLICT -> "This preset changed or was deleted. Cancel and reopen it before editing again."
    PresetProblem.REVISION_EXHAUSTED -> "This preset cannot accept further revisions. Nothing was changed."
}
