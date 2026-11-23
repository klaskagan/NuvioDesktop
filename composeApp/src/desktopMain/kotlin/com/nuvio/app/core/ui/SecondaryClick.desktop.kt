package com.nuvio.app.core.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.secondaryClickAt(onClick: ((Offset) -> Unit)?): Modifier {
    if (onClick == null) return this

    return pointerInput(onClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.button == PointerButton.Secondary) {
                    val position = event.changes.firstOrNull()?.position ?: Offset.Zero
                    event.changes.forEach { change -> change.consume() }
                    onClick(position)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.mousePressAndHoldAt(onHold: ((Offset) -> Unit)?): Modifier {
    if (onHold == null) return this

    return pointerInput(onHold) {
        val timeout = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            while (true) {
                val press = awaitPointerEvent()
                val down = press.changes.firstOrNull() ?: continue
                if (press.type != PointerEventType.Press || press.button != PointerButton.Primary ||
                    down.type != PointerType.Mouse
                ) continue
                // Wait for the hold; a quick release or a drag past slop (scrolling) cancels it. A
                // release that arrives after the hold time (event clock) still counts as a hold.
                var cancelled = false
                var releasedAfterHold = false
                val waited = withTimeoutOrNull(timeout) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if ((change.position - down.position).getDistance() > slop) {
                            cancelled = true
                            break
                        }
                        if (!change.pressed) {
                            if (change.uptimeMillis - down.uptimeMillis >= timeout) {
                                change.consume()
                                releasedAfterHold = true
                            } else {
                                cancelled = true
                            }
                            break
                        }
                    }
                }
                if (cancelled) continue
                onHold(down.position)
                if (releasedAfterHold || waited != null) continue
                // Swallow the rest of this press so the clickable around it doesn't also click.
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}
