package com.nuvio.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopHistoryContactTest {
    @get:Rule val compose = createComposeRule()
    private val phases = MutableSharedFlow<HistoryGesturePhase>(extraBufferCapacity = 16)
    private val actions = mutableListOf<HistorySwipe>()

    @Test fun holdAndReturnWithoutLiftingCancelsEvenAfterTenSeconds() {
        showHost()
        phase(HistoryGesturePhase.Began)
        compose.onRoot().performMouseInput { enter(center); scroll(-4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithTag("history_back_overlay").assertExists()
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        compose.onRoot().performMouseInput { advanceEventTime(10_000); scroll(4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("history_back_overlay").assertDoesNotExist()
        phase(HistoryGesturePhase.Ended)
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
    }

    @Test fun holdThenLiftAtTheSamePositionNavigatesWithoutAnotherWheelEvent() {
        showHost()
        phase(HistoryGesturePhase.Began)
        compose.onRoot().performMouseInput { enter(center); scroll(-4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        phase(HistoryGesturePhase.Ended)
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Back), actions) }
        phase(HistoryGesturePhase.Momentum)
        compose.onRoot().performMouseInput { advanceEventTime(10_000); scroll(-20f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Back), actions) }
    }

    @Test fun forwardAlsoWaitsForReleaseAndSystemCancellationDoesNotNavigate() {
        showHost()
        phase(HistoryGesturePhase.Began)
        compose.onRoot().performMouseInput { enter(center); scroll(4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        phase(HistoryGesturePhase.Cancelled)
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        phase(HistoryGesturePhase.Began)
        compose.onRoot().performMouseInput { advanceEventTime(30); scroll(4f, ScrollWheel.Horizontal) }
        phase(HistoryGesturePhase.Ended)
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Forward), actions) }
    }

    private fun phase(phase: HistoryGesturePhase) {
        compose.runOnIdle { assertTrue(phases.tryEmit(phase)) }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun showHost() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalHistoryGesturePhaseEvents provides phases) {
                DesktopHistoryNavigationHost(
                    enabled = true,
                    canGoBack = true,
                    canGoForward = true,
                    onBack = { actions += HistorySwipe.Back },
                    onForward = { actions += HistorySwipe.Forward },
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
        compose.waitForIdle()
    }
}
