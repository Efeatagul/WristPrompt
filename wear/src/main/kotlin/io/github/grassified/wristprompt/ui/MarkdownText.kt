package io.github.grassified.wristprompt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*

/** Native text only. No URI handler, intent, HTML, image loader or nested vertical scroll. */
@Composable
internal fun MarkdownText(block: MarkdownBlock, inspect: (List<MarkdownLink>) -> Unit) {
    if (block.kind == MarkdownKind.RULE) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outline))
        return
    }
    val code = block.kind == MarkdownKind.CODE
    val heading = block.kind == MarkdownKind.HEADING
    val bold = heading || block.kind == MarkdownKind.TABLE_RECORD && block.level == 1
    val size = if (heading && block.level == 1) 19.sp else if (heading && block.level == 2) 17.sp else 16.sp
    Column(Modifier.fillMaxWidth()
        .padding(start = if (block.kind == MarkdownKind.LIST_ITEM) (block.level.coerceIn(1, 3) - 1).times(6).dp else 0.dp,
            top = if (block.continuation) 0.dp else if (heading) 6.dp else 2.dp)
        .then(if (code) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 6.dp, end = 6.dp, top = if (block.continuation) 0.dp else 6.dp,
            bottom = if (block.continues) 0.dp else 6.dp) else Modifier)) {
        if (block.language.isNotEmpty()) Text(block.language, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth()) {
            if (block.marker.isNotEmpty()) Text(block.marker + " ", style = MaterialTheme.typography.bodyMedium)
            if (block.kind == MarkdownKind.QUOTE) Box(Modifier.padding(end = 6.dp).width(2.dp).height(20.dp)
                .background(MaterialTheme.colorScheme.primary))
            Text(block.inline, modifier = Modifier.weight(1f)
                .then(if (code) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .semantics {
                    if (heading) { heading(); stateDescription = "Heading level ${block.level}" }
                    if (code) stateDescription = "Code. Scroll horizontally for long lines."
                },
                softWrap = !code,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = size, lineHeight = 21.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = if (code) FontFamily.Monospace else FontFamily.Default))
        }
        if (block.links.isNotEmpty()) {
            TextButton(onClick = { inspect(block.links) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Inspect links", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
