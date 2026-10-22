package com.nuvio.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.geometry.Offset

internal enum class HistoryGesturePhase { Began, Ended, Cancelled, Unphased, Momentum }

@Composable
internal expect fun PlatformHistoryGesturePhaseObserver(onPhase: (HistoryGesturePhase) -> Unit)

// An injected phase stream also lets UI tests exercise finger contact without native hardware.
internal val LocalHistoryGesturePhaseEvents = staticCompositionLocalOf<Flow<HistoryGesturePhase>?> { null }

@Composable
internal fun ObserveHistoryGesturePhases(onPhase: (HistoryGesturePhase) -> Unit) {
    val events = LocalHistoryGesturePhaseEvents.current
    val latest by rememberUpdatedState(onPhase)
    if (events == null) PlatformHistoryGesturePhaseObserver(onPhase)
    else LaunchedEffect(events) { events.collect { latest(it) } }
}

/** Native contact phases own the gesture until finger-up, including pauses while held. */
internal class HistorySwipeSession {
    private val detector = HistorySwipeDetector()
    private var nativeSequence = false
    private var fingersDown = false
    private var momentum = false
    val preview: HistorySwipePreview? get() = detector.preview
    val needsFallbackCompletion: Boolean get() = !nativeSequence

    fun onPhase(phase: HistoryGesturePhase): HistorySwipe? = when (phase) {
        HistoryGesturePhase.Began -> {
            nativeSequence = true
            fingersDown = true
            momentum = false
            detector.reset()
            null
        }
        HistoryGesturePhase.Ended -> {
            val wasDown = fingersDown
            fingersDown = false
            if (wasDown) detector.finish() else null
        }
        HistoryGesturePhase.Cancelled -> {
            fingersDown = false
            detector.reset()
            detector.finish()
            null
        }
        HistoryGesturePhase.Unphased -> {
            if (nativeSequence) detector.reset()
            nativeSequence = false
            momentum = false
            fingersDown = false
            null
        }
        HistoryGesturePhase.Momentum -> { momentum = true; null }
    }

    fun onScroll(delta: Offset, time: Long, blocked: Boolean) {
        if (momentum || nativeSequence && !fingersDown) return
        detector.onScroll(delta, time, blocked, preserveSession = fingersDown)
    }

    fun finishFallback(): HistorySwipe? = if (needsFallbackCompletion) detector.finish() else null
    fun reset() {
        detector.reset()
        // Keep the native sequence latched until a new begin or an ordinary wheel event.
        fingersDown = false
    }
}
