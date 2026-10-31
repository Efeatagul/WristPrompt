package io.github.grassified.wristprompt.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState

/** Pure local inspection. Even HTTP(S) destinations have no launch action in D5-C. */
@Composable
internal fun LinkInspection(links: List<MarkdownLink>, back: () -> Unit) {
    val scroll = remember(links) { TransformingLazyColumnState() }
    val parts = remember(links) {
        buildList {
            for (link in links) {
                val value = "${link.label}\n${link.host?.let { "HTTP(S) host: $it" } ?: "Non-actionable destination"}\n${link.destination}"
                add(value)
            }
        }
    }
    ShellList(Modifier.fillMaxSize(), scroll) {
        action("Back", onClick = back)
        item { ShellText("Link details", heading = true) }
        for (part in parts) paragraphs(part)
        item { ShellText("Inspection only. Links do not open.") }
    }
}
