package io.github.grassified.wristprompt.ui

import androidx.compose.runtime.*
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState

internal data class ChatReadingPosition(val key: String?, val fallback: Int, val offset: Int)
internal fun restoreChatIndex(position: ChatReadingPosition?, keys: List<String>): Int =
    position?.let { keys.indexOf(it.key).takeIf { index -> index >= 0 } ?: it.fallback.coerceIn(0, (keys.size - 1).coerceAtLeast(0)) } ?: 0

@Composable
internal fun rememberChatScroll(navigation: ShellNavigationViewModel, keys: List<String>): TransformingLazyColumnState {
    val position = navigation.chatPosition
    val scroll = remember(navigation) {
        TransformingLazyColumnState(restoreChatIndex(position, keys), position?.offset ?: 0)
    }
    val currentKeys by rememberUpdatedState(keys)
    DisposableEffect(navigation, scroll) {
        onDispose {
            val index = scroll.anchorItemIndex
            if (index >= 0) navigation.chatPosition = ChatReadingPosition(currentKeys.getOrNull(index), index, scroll.anchorItemScrollOffset)
        }
    }
    return scroll
}

/** Wear's scrollToItem centers a row, which may itself exceed the viewport.
 * Finish the explicit jump at the bottom of that last row, using one bounded scroll. */
internal suspend fun scrollToLatest(scroll: TransformingLazyColumnState) {
    scroll.scrollToItem(latestTarget(scroll.layoutInfo.totalItemsCount))
    val info = scroll.layoutInfo
    val distance = (info.visibleItems.lastOrNull()?.measuredHeight ?: 0) +
        info.viewportSize.height + info.afterContentPadding
    scroll.scroll { scrollBy(distance.toFloat()) }
}
