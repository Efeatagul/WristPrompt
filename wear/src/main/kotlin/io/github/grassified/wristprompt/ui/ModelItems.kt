package io.github.grassified.wristprompt.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.grassified.wristprompt.openai.AiModel

// Identity and label come directly from discovery, including API catalogs whose name is the ID.
// No mapped catalog: indexed lazy content reads only visible rows in service order.
internal fun modelRowKey(model: AiModel): String = "model:${model.slug}"
internal fun modelRowName(model: AiModel): String = model.displayName.ifBlank { model.slug }

internal fun ShellListScope.modelItems(
    models: List<AiModel>, selectedSlug: String?, enabled: Boolean, onSelect: (String) -> Unit,
) {
    items(count = models.size, key = { modelRowKey(models[it]) }, contentType = { "model" }) { index ->
        val model = models[index]
        val chosen = model.slug == selectedSlug
        Button(onClick = { onSelect(model.slug) }, enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = chosen },
            label = { Text((if (chosen) "✓ " else "") + modelRowName(model),
                maxLines = 3, overflow = TextOverflow.Ellipsis) })
    }
}

internal fun ShellListScope.thinkingItems(
    state: io.github.grassified.wristprompt.openai.ProofState, enabled: Boolean, onSelect: (String?) -> Unit,
) {
    val current = state.configuration.controls.firstOrNull { it.key == io.github.grassified.wristprompt.openai.InferenceControls.THINKING }
    val capability = state.models.firstOrNull { it.slug == state.selectedSlug }?.thinking
    item { ShellText("Thinking level", heading = true) }
    item { ShellText("Current: " + (current?.value ?: "Default"), compact = true) }
    state.thinkingNotice?.let { notice -> item { ShellText(notice) } }
    action((if (current == null) "✓ " else "") + "Default", enabled = enabled && !state.thinkingSaving) { onSelect(null) }
    item { ShellText("Default omits the parameter.", compact = true) }
    capability?.values?.forEach { value ->
        action((if (current?.value == value) "✓ " else "") + value, enabled = enabled && !state.thinkingSaving) { onSelect(value) }
    }
    if (capability == null) item { ShellText("Native levels unavailable for this model and route.", compact = true) }
}
