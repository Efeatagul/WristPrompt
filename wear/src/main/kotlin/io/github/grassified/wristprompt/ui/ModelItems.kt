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
