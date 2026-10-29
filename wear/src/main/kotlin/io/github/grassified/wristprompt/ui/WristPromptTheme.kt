package io.github.grassified.wristprompt.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WristPromptColors = ColorScheme(
    primary = Color(0xFF80CBC4),
    primaryDim = Color(0xFF65AFA8),
    primaryContainer = Color(0xFF173B38),
    onPrimary = Color.Black,
    onPrimaryContainer = Color(0xFFD0F5EF),
    secondary = Color(0xFFB9CBC8),
    secondaryContainer = Color(0xFF293331),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surfaceContainerLow = Color(0xFF101313),
    surfaceContainer = Color(0xFF191D1C),
    surfaceContainerHigh = Color(0xFF252B29),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFC1CBC8),
)

/** Static Wear Material 3 foundation: native typography/shapes, no dynamic work. */
@Composable
internal fun WristPromptTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WristPromptColors, content = content)
}
