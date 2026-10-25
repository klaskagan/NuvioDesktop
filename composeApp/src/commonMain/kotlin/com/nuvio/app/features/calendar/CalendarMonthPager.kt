package com.nuvio.app.features.calendar

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nuvio.app.navigation.HistoryGesturePhase
import com.nuvio.app.navigation.ObserveHistoryGesturePhases
import com.nuvio.app.navigation.excludeFromHistoryGestures
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Finger contact owns the preview; only a completed contact can change the selected month. */
@Composable
internal fun CalendarMonthPager(
    selected: String,
    firstWeekday: Int,
    modifier: Modifier,
    move: (Int) -> Unit,
    content: @Composable (List<String>, Boolean, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val maxSwipeDistance = with(LocalDensity.current) { 80.dp.toPx() }
    val latestMove by rememberUpdatedState(move)
    var width by remember { mutableIntStateOf(1) }
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var native by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(false) }
    var momentum by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf<Job?>(null) }
    var animation by remember { mutableStateOf<Job?>(null) }
    fun finish(cancelled: Boolean) {
        if (settling) return
        fallback?.cancel()
        val direction = if (!cancelled && abs(offset) >= minOf(width * .08f, maxSwipeDistance)) { if (offset < 0) 1 else -1 } else 0
        settling = true
        animation = scope.launch {
            try {
                animate(offset, if (direction == 0) 0f else -direction * width.toFloat(), animationSpec = tween(220)) { value, _ -> offset = value }
                if (direction != 0) latestMove(direction)
                offset = 0f
            } finally { settling = false }
        }
    }
    fun drag(delta: Float) { if (!settling) offset = (offset + delta).coerceIn(-width.toFloat(), width.toFloat()) }
    val latestFinish by rememberUpdatedState<(Boolean) -> Unit> { finish(it) }
    val latestDrag by rememberUpdatedState<(Float) -> Unit> { drag(it) }
    ObserveHistoryGesturePhases { phase ->
        when (phase) {
            HistoryGesturePhase.Began -> { native = true; contact = true; momentum = false; fallback?.cancel() }
            HistoryGesturePhase.Ended -> { if (contact) latestFinish(false); contact = false }
            HistoryGesturePhase.Cancelled -> { contact = false; latestFinish(true) }
            HistoryGesturePhase.Momentum -> momentum = true
            HistoryGesturePhase.Unphased -> { native = false; contact = false; momentum = false }
        }
    }
    LaunchedEffect(selected.take(7)) { animation?.cancel(); fallback?.cancel(); offset = 0f; settling = false }
    Box(modifier.clipToBounds().testTag("calendar-month-grid").onSizeChanged { width = it.width.coerceAtLeast(1) }
        .excludeFromHistoryGestures()
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = { latestFinish(false) }, onDragCancel = { latestFinish(true) }) { change, delta ->
                change.consume(); latestDrag(delta)
            }
        }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll || momentum || native && !contact) continue
                    val dx = event.changes.sumOf { it.scrollDelta.x.toDouble() }.toFloat()
                    val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (abs(dx) <= abs(dy) || dx == 0f) continue
                    latestDrag(-dx * 20f)
                    event.changes.forEach { it.consume() }
                    if (!native) {
                        fallback?.cancel()
                        fallback = scope.launch { delay(180); latestFinish(false) }
                    }
                }
            }
        }) {
        val neighbor = if (offset < 0f) 1 else -1
        if (offset != 0f) Box(Modifier.fillMaxSize().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative((offset + neighbor * width).roundToInt(), 0) }
        }) { content(calendarMonthGridRange(calendarMoveMonth(selected, neighbor), firstWeekday).dates, false, calendarMoveMonth(selected, neighbor).take(7)) }
        Box(Modifier.fillMaxSize().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(offset.roundToInt(), 0) }
        }) { content(calendarMonthGridRange(selected, firstWeekday).dates, true, selected.take(7)) }
    }
}
