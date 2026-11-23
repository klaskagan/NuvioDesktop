package com.nuvio.app.core.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

/**
 * Keeps an inner scroller's wheel/trackpad scroll and fling to itself: at its end the leftover is
 * dropped instead of scrolling the page around it (Compose's default nested-scroll chaining). Put it
 * before the inner `verticalScroll` / lazy list. Use for a list inside a tile (Library's Your lists,
 * the Cloud files panel), not for a page's main scroller.
 */
internal fun Modifier.containedScroll(): Modifier = composed {
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
    nestedScroll(connection)
        // A wheel the inner list can't use (at its end, or too short to scroll) skips nested scroll
        // and bubbles to the page as a raw event; take it here, after the list has had its turn.
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type == PointerEventType.Scroll) event.changes.forEach { it.consume() }
                }
            }
        }
}
