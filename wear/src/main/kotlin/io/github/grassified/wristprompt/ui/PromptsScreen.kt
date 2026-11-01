package io.github.grassified.wristprompt.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.grassified.wristprompt.openai.InferenceViewModel
import io.github.grassified.wristprompt.presets.*

@Composable
internal fun PromptsScreen(model: WatchPresetsViewModel, proof: InferenceViewModel, navigation: ShellNavigationViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val selection = state.selection
    LaunchedEffect(model) { model.refresh() }
    DisposableEffect(model) { onDispose { model.cancel() } }
    BackHandler(enabled = selection != null) { model.cancel() }
    // Detail actions start at the top even when the selected list row was far down.
    val scroll = if (selection == null) rememberShellScroll(navigation, Surface.PROMPTS)
        else remember(selection) { androidx.wear.compose.foundation.lazy.TransformingLazyColumnState() }
    ShellList(Modifier.fillMaxSize(), scroll) {
        item { ShellText("Prompts", heading = true) }
        state.error?.let { item { ShellText(it) } }
        if (selection == null) {
            item { ShellText("Saved user text. Select, review and insert; Send stays separate.") }
            val live = state.catalog.records.filterNot { it.deleted }.sortedBy { it.name.lowercase() }
            if (live.isEmpty()) item { ShellText("No synced prompts. Create one on your phone, then retry sync.") }
            live.forEach { preset -> action(preset.name) { model.select(preset, proof) } }
            action("Refresh sync", onClick = model::refresh)
            action("Back") { model.cancel(); navigation.back() }
        } else {
            item { ShellText(selection.preset.name, heading = true) }
            if (selection.draft.isNotEmpty()) item { ShellText("Insert appends two newlines and this prompt. Replace changes the whole draft.") }
            action("Insert") { model.apply(PresetChoice.INSERT, proof) { navigation.open(Surface.COMPOSER) } }
            if (selection.draft.isNotEmpty()) action("Replace") { model.apply(PresetChoice.REPLACE, proof) { navigation.open(Surface.COMPOSER) } }
            action("Cancel", onClick = model::cancel)
            paragraphs(selection.preset.text)
        }
    }
}
