package com.nuvio.app.features.calendar

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform

/** Calendar and History load at a user-driven scroll edge, including short or empty pages. */
internal fun Modifier.agendaScrollLoading(
    list: LazyListState,
    enabled: Boolean,
    loading: Boolean,
    onEarlier: (() -> Unit)? = null,
    onLater: () -> Unit,
): Modifier = composed {
    val currentList by rememberUpdatedState(list)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentLoading by rememberUpdatedState(loading)
    val earlier by rememberUpdatedState(onEarlier)
    val later by rememberUpdatedState(onLater)
    var lastRequest by remember { mutableLongStateOf(0L) }
    val requestEdge by rememberUpdatedState<(Boolean) -> Unit> { atStart ->
        val action = if (atStart) earlier else later
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        if (currentEnabled && !currentLoading && action != null && now - lastRequest > 800) {
            lastRequest = now
            action()
        }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val direction = consumed.y + available.y
                    if (direction > 0 && !currentList.canScrollBackward) requestEdge(true)
                    if (direction < 0 && !currentList.canScrollForward) requestEdge(false)
                }
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) {
                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (delta > 0 && !currentList.canScrollForward) requestEdge(false)
                    if (delta < 0 && !currentList.canScrollBackward) requestEdge(true)
                }
            }
        }
    }
}
