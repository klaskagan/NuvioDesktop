package com.nuvio.app.navigation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DesktopHistoryGesturesTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<HistorySwipe>()

    @Test fun scrollEventsNavigateOncePerGestureInBothDirections() {
        showContent()
        compose.onRoot().performMouseInput {
            enter(center)
            scroll(4f, ScrollWheel.Horizontal)
            advanceEventTime(30)
            scroll(4f, ScrollWheel.Horizontal)
        }
        compose.mainClock.advanceTimeBy(220)
        compose.onRoot().performMouseInput {
            advanceEventTime(600)
            scroll(-4f, ScrollWheel.Horizontal)
        }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Forward, HistorySwipe.Back), actions) }
    }

    @Test fun horizontalSwipeOverVerticalPageNavigates() {
        compose.setContent {
            Box(Modifier.size(600.dp, 400.dp).desktopHistoryGestures(
                enabled = true,
                onBack = { actions += HistorySwipe.Back },
                onForward = { actions += HistorySwipe.Forward },
            )) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.height(1200.dp))
                }
            }
        }
        compose.onRoot().performMouseInput {
            enter(center)
            scroll(5f, ScrollWheel.Horizontal)
        }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Forward), actions) }
    }

    @Test fun scrollingClaimedByContentDoesNotNavigate() {
        showContent(consumeScroll = true)
        compose.onRoot().performMouseInput {
            enter(center)
            scroll(5f, ScrollWheel.Horizontal)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
    }

    @Test fun disabledGesturesDoNotNavigate() {
        showContent(enabled = false)
        compose.onRoot().performMouseInput {
            enter(center)
            scroll(5f, ScrollWheel.Horizontal)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
    }

    private fun showContent(consumeScroll: Boolean = false, enabled: Boolean = true) {
        compose.setContent {
            DesktopHistoryNavigationHost(
                enabled = enabled,
                canGoBack = true,
                canGoForward = true,
                onBack = { actions += HistorySwipe.Back },
                onForward = { actions += HistorySwipe.Forward },
            ) {
                Box(Modifier.fillMaxSize()
                    .then(if (consumeScroll) Modifier.excludeFromHistoryGestures() else Modifier)
                    .pointerInput(consumeScroll) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (consumeScroll && event.type == PointerEventType.Scroll) {
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    })
            }
        }
    }
}
