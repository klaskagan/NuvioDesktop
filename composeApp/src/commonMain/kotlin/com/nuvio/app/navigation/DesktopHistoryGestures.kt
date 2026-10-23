package com.nuvio.app.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.isDesktop
import kotlin.math.abs

internal enum class HistorySwipe { Back, Forward }
internal data class HistorySwipePreview(val direction: HistorySwipe, val progress: Float)

internal class HistoryGestureExclusions {
    private val regions = mutableMapOf<Any, (Offset) -> Boolean>()
    fun update(key: Any, hitTest: (Offset) -> Boolean) { regions[key] = hitTest }
    fun remove(key: Any) { regions.remove(key) }
    fun contains(position: Offset) = regions.values.any { it(position) }
}

internal val LocalHistoryGestureExclusions = staticCompositionLocalOf<HistoryGestureExclusions?> { null }

/** Exclude occupied row content, including gaps between items, rather than its empty tail. */
internal fun Modifier.excludeFromHistoryGestures(state: LazyListState? = null): Modifier = if (!isDesktop) this else composed {
    val exclusions = LocalHistoryGestureExclusions.current
    val active = LocalScreenActive.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val key = remember { Any() }
    DisposableEffect(exclusions, key, active, state, rtl) {
        onDispose { exclusions?.remove(key) }
    }
    this.onGloballyPositioned { coordinates ->
        if (active) exclusions?.update(key) { position ->
            if (!coordinates.isAttached || !coordinates.boundsInRoot().contains(position)) false
            else if (state == null) true
            else {
                // Read live layout information: scrolling and changing entries can move the
                // occupied area without changing the full-width row's measured bounds.
                val info = state.layoutInfo
                val items = info.visibleItemsInfo
                val start = items.minOfOrNull { it.offset }?.minus(info.viewportStartOffset)
                val end = items.maxOfOrNull { it.offset + it.size }?.minus(info.viewportStartOffset)
                val local = coordinates.localPositionOf(coordinates.findRootCoordinates(), position)
                val x = if (rtl) coordinates.size.width - local.x else local.x
                start != null && end != null && x >= start && x < end
            }
        }
    }
}

/** Wheel ticks are platform units, not pixels. Latch the whole burst, including inertia. */
internal class HistorySwipeDetector {
    private var lastEventTime: Long? = null
    private var horizontal = 0f
    private var vertical = 0f
    private var finished = false

    val preview: HistorySwipePreview?
        get() = if (finished || abs(horizontal) < 0.15f) null else HistorySwipePreview(
            direction = if (horizontal < 0f) HistorySwipe.Back else HistorySwipe.Forward,
            progress = (abs(horizontal) / 3f).coerceIn(0f, 1f),
        )

    fun finish(): HistorySwipe? {
        val ready = preview?.takeIf { it.progress >= 1f && abs(horizontal) >= vertical * 2f }?.direction
        finished = true
        return ready
    }

    fun reset() {
        lastEventTime = null
        horizontal = 0f
        vertical = 0f
        finished = false
    }

    fun onScroll(delta: Offset, timeMillis: Long, consumed: Boolean, preserveSession: Boolean = false): HistorySwipe? {
        if (!preserveSession && lastEventTime?.let { timeMillis - it > 400 } != false) reset()
        lastEventTime = timeMillis
        // If content claimed any part of this gesture, let it own the entire burst.
        if (consumed) finished = true
        if (finished) return null
        horizontal += delta.x
        vertical += abs(delta.y)
        if (vertical >= 1f && vertical > abs(horizontal)) {
            finished = true
            return null
        }
        if (abs(horizontal) < 3f || abs(horizontal) < vertical * 2f) return null
        // Positive horizontal wheel ticks move content left, negative ticks right.
        return if (horizontal < 0f) HistorySwipe.Back else HistorySwipe.Forward
    }
}

internal fun Modifier.desktopHistoryGestures(
    enabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onPreview: (HistorySwipePreview?) -> Unit = {},
    exclusions: HistoryGestureExclusions? = null,
    gestureResetKey: Any? = null,
): Modifier = if (!isDesktop) this else composed {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentBack by rememberUpdatedState(onBack)
    val currentForward by rememberUpdatedState(onForward)
    val currentPreview by rememberUpdatedState(onPreview)
    val currentExclusions by rememberUpdatedState(exclusions)
    val scope = rememberCoroutineScope()
    var finishJob by remember { mutableStateOf<Job?>(null) }
    val session = remember { HistorySwipeSession() }
    DisposableEffect(enabled, gestureResetKey) {
        finishJob?.cancel()
        session.reset()
        currentPreview(null)
        onDispose {
            finishJob?.cancel()
            session.reset()
            currentPreview(null)
        }
    }
    fun accept(direction: HistorySwipe?) {
        if (!currentEnabled) return
        when (direction) {
            HistorySwipe.Back -> currentBack()
            HistorySwipe.Forward -> currentForward()
            null -> Unit
        }
    }
    fun clearPreviewAfterTransition() {
        finishJob = scope.launch {
            delay(240)
            currentPreview(null)
        }
    }
    ObserveHistoryGesturePhases { phase ->
        when (phase) {
            HistoryGesturePhase.Began -> {
                finishJob?.cancel()
                session.onPhase(phase)
                currentPreview(null)
            }
            HistoryGesturePhase.Ended -> {
                finishJob?.cancel()
                accept(session.onPhase(phase))
                clearPreviewAfterTransition()
            }
            HistoryGesturePhase.Cancelled -> {
                finishJob?.cancel()
                session.onPhase(phase)
                currentPreview(null)
            }
            else -> session.onPhase(phase)
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    this.onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                if (!currentEnabled) {
                    finishJob?.cancel()
                    session.reset()
                    currentPreview(null)
                    continue
                }
                val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
                val time = event.changes.maxOfOrNull { it.uptimeMillis } ?: continue
                val blocked = event.changes.any { currentExclusions?.contains(it.position + origin) == true }
                session.onScroll(delta, time, blocked || event.changes.any { it.isConsumed })
                currentPreview(session.preview)
                finishJob?.cancel()
                if (session.needsFallbackCompletion) {
                    finishJob = scope.launch {
                        // Non-phased mouse wheels and non-macOS input retain burst completion.
                        delay(180)
                        accept(session.finishFallback())
                        delay(240)
                        currentPreview(null)
                    }
                }
            }
        }
    }
}
