package com.nuvio.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DesktopHistoryNavigationHostTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<HistorySwipe>()

    @Test fun backThenForwardRestoresTheDetailThroughNavDisplay() {
        val movie = DetailRoute("movie", "one")
        lateinit var navigator: NuvioNavigator
        compose.setContent {
            val stack = remember { NavBackStack<NavKey>(TabsRoute, movie) }
            navigator = remember { NuvioNavigator(stack) }
            DesktopHistoryNavigationHost(
                enabled = true,
                canGoBack = navigator.canPopBackStack(),
                canGoForward = navigator.canGoForward,
                onBack = { navigator.popBackStack() },
                onForward = { navigator.goForward() },
            ) {
                NavDisplay(
                    backStack = stack,
                    onBack = { navigator.popBackStack() },
                    entryProvider = entryProvider {
                        entry<TabsRoute> { Box(Modifier.fillMaxSize()) }
                        entry<DetailRoute> { Box(Modifier.fillMaxSize()) }
                    },
                )
            }
        }
        compose.onRoot().performMouseInput { enter(center); scroll(-4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(TabsRoute, navigator.currentRoute) }
        compose.onRoot().performMouseInput { advanceEventTime(600); scroll(4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(movie, navigator.currentRoute) }
    }

    @Test fun trailerSurfaceConsumingScrollDoesNotHideHistoryGestures() {
        setHost {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) event.changes.forEach { it.consume() }
                    }
                }
            })
        }
        compose.onRoot().performMouseInput { enter(center); scroll(-4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Back), actions) }
    }

    @Test fun collectionAtEitherBoundaryNeverTriggersHistory() {
        setHost {
            Row(Modifier.size(600.dp, 100.dp).excludeFromHistoryGestures()
                .horizontalScroll(rememberScrollState()).testTag("collection")) {
                Box(Modifier.size(80.dp))
            }
        }
        val collection = compose.onNodeWithTag("collection")
        collection.performMouseInput { enter(center); scroll(-4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(500)
        collection.performMouseInput { advanceEventTime(600); scroll(4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        compose.onNodeWithTag("history_back_overlay").assertDoesNotExist()
        compose.onNodeWithTag("history_forward_overlay").assertDoesNotExist()
    }

    @Test fun partialSwipeShowsEdgeArrowAndCanBeCancelled() {
        compose.mainClock.autoAdvance = false
        setHost { Box(Modifier.fillMaxSize().background(Color(0xFF24272B))) }
        compose.onRoot().performMouseInput { enter(center); scroll(-1f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("history_back_overlay").assertExists()
        val directory = File("build/reports/history-navigation").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(directory, "back-preview.png"))
        compose.onRoot().performMouseInput { advanceEventTime(30); scroll(1f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        compose.onNodeWithTag("history_back_overlay").assertDoesNotExist()
    }

    @Test fun completedForwardSwipeShowsReadyArrowBeforeNavigating() {
        compose.mainClock.autoAdvance = false
        setHost { Box(Modifier.fillMaxSize().background(Color(0xFF24272B))) }
        compose.onRoot().performMouseInput { enter(center); scroll(4f, ScrollWheel.Horizontal) }
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithTag("history_forward_overlay").assertExists()
        val directory = File("build/reports/history-navigation").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(directory, "forward-ready.png"))
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Forward), actions) }
    }

    private fun setHost(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            DesktopHistoryNavigationHost(
                enabled = true,
                canGoBack = true,
                canGoForward = true,
                onBack = { actions += HistorySwipe.Back },
                onForward = { actions += HistorySwipe.Forward },
                content = content,
            )
        }
    }
}
