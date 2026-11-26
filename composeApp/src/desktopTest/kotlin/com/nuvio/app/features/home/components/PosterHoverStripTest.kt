package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import com.nuvio.app.core.ui.PosterHoverStyle
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.LocalPosterZoomOverlayHost
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.PosterZoomOverlayHostState
import com.nuvio.app.features.home.MetaPreview
import dev.chrisbanes.haze.HazeState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Poster hover · actions" and "Poster hover · Quick look": hover shows the strip, ⓘ opens Quick look, Esc's close returns. */
@OptIn(ExperimentalTestApi::class)
class PosterHoverStripTest {
    private val first = MetaPreview("fixture-silo", "series", "Silo")
    private val second = MetaPreview("fixture-severance", "series", "Severance")

    @AfterTest fun reset() {
        PosterQuickLook.close()
        PosterCardStyleRepository.setHoverStyle(PosterHoverStyle.ActionsQuickLook)
    }

    private var menuOpens = 0
    private val openedDetails = mutableListOf<String>()

    private fun ComposeUiTest.showPoster() {
        setContent {
            AppEnvironment {
                val host = remember { PosterZoomOverlayHostState() }
                val haze = remember { HazeState() }
                CompositionLocalProvider(
                    LocalPosterZoomOverlayHost provides host,
                    LocalPosterQuickLookRow provides listOf(first, second),
                ) {
                    Row(Modifier.padding(40.dp)) {
                        HomePosterCard(first, onClick = {}, onLongClick = { menuOpens++ }, modifier = Modifier.testTag("first_poster"))
                        HomePosterCard(second, onClick = {}, modifier = Modifier.testTag("second_poster"))
                    }
                    PosterQuickLookHost(onPlay = null, openMeta = { openedDetails += it.id })
                }
                host.content?.invoke(haze)
            }
        }
    }

    private fun ComposeUiTest.exists(tag: String) =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Press-and-hold opens Quick look; right-click keeps the poster menu (Home design, 26 Nov). */
    @Test fun pressAndHoldOpensQuickLookAndRightClickKeepsTheMenu() = runDesktopComposeUiTest(width = 900, height = 700) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setHoverPreviewEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        menuOpens = 0
        showPoster()
        waitForIdle()
        onAllNodesWithText("Silo")[0].performMouseInput { rightClick(center) }
        waitForIdle()
        assertEquals(1, menuOpens)
        assertNull(PosterQuickLook.open)
        onAllNodesWithText("Silo")[0].performTouchInput { longClick(center) }
        waitUntil(timeoutMillis = 3_000) { PosterQuickLook.open?.item?.id == first.id }
        assertEquals(1, menuOpens, "press-and-hold must not open the menu")
    }

    /** Nothing in Quick look moves while it loads: the panel and its actions keep their bounds. */
    @Test fun quickLookContentDoesNotShiftWhileLoading() = runDesktopComposeUiTest(width = 1200, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setHoverPreviewEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        showPoster()
        waitForIdle()
        onAllNodesWithText("Silo")[0].performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 1_000) { exists("poster_strip_quick_look") }
        onNodeWithTag("poster_strip_quick_look").performClick()
        waitUntil(timeoutMillis = 3_000) { exists("poster_quick_look_details") }
        mainClock.advanceTimeBy(600)
        val panel = onNodeWithTag("poster_quick_look_panel").getUnclippedBoundsInRoot()
        val details = onNodeWithTag("poster_quick_look_details").getUnclippedBoundsInRoot()
        // Details, ratings and providers settle (here: fail, with no addons) without moving anything.
        mainClock.advanceTimeBy(4_000)
        waitForIdle()
        assertEquals(panel, onNodeWithTag("poster_quick_look_panel").getUnclippedBoundsInRoot())
        assertEquals(details, onNodeWithTag("poster_quick_look_details").getUnclippedBoundsInRoot())
    }

    /** The strip's Play trailer opens Quick look already expanded; Esc steps back to Quick look. */
    @Test fun playTrailerOpensTheExpandedTrailerAndBackReturnsToQuickLook() = runDesktopComposeUiTest(width = 900, height = 700) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setHoverPreviewEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        showPoster()
        waitForIdle()
        onAllNodesWithText("Silo")[0].performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 1_000) { exists("poster_strip_trailer") }
        onNodeWithTag("poster_strip_trailer").performClick()
        waitUntil(timeoutMillis = 3_000) { exists("poster_quick_look_trailer") }
        assertTrue(PosterQuickLook.expanded)
        assertEquals(first.id, PosterQuickLook.open?.item?.id)
        PosterQuickLook.back()
        waitForIdle()
        assertFalse(PosterQuickLook.expanded)
        assertEquals(first.id, PosterQuickLook.open?.item?.id)
        PosterQuickLook.back()
        waitForIdle()
        assertNull(PosterQuickLook.open)
    }

    /** Inline expand waits for the hover delay, then widens the poster and pushes the next one right. */
    @Test fun inlineExpandWaitsForTheDelayThenWidensAndPushesTheRow() = runDesktopComposeUiTest(width = 1100, height = 700) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setHoverPreviewEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        PosterCardStyleRepository.setHoverPreviewOpenDelayMillis(1_000)
        PosterCardStyleRepository.setHoverStyle(PosterHoverStyle.InlineExpand)
        // Portrait posters: a landscape card is already 16:9 and has nothing to widen into.
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        PosterCardStyleRepository.setWidthDp(140)
        showPoster()
        waitForIdle()
        val secondLeft = onNodeWithTag("second_poster").getUnclippedBoundsInRoot().left
        onAllNodesWithText("Silo")[0].performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(400)
        assertFalse(exists("poster_inline_card"), "the card must wait for the hover delay")
        assertFalse(exists("poster_action_strip"), "inline expand shows no strip")
        waitUntil(timeoutMillis = 4_000) { exists("poster_inline_card") }
        waitUntil(timeoutMillis = 2_000) { onNodeWithTag("second_poster").getUnclippedBoundsInRoot().left > secondLeft + 100.dp }
        onNodeWithTag("poster_inline_quick_look").performClick()
        waitUntil(timeoutMillis = 3_000) { PosterQuickLook.open?.item?.id == first.id }
    }

    /** The strip ignores the hover delay (it moves nothing), even at the longest setting. */
    @Test fun hoverShowsStripAtOnceAndInfoOpensQuickLookThatWalksTheRow() = runDesktopComposeUiTest(width = 900, height = 700) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setHoverPreviewEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        PosterCardStyleRepository.setHoverPreviewOpenDelayMillis(5_000)
        showPoster()
        waitForIdle()
        assertFalse(exists("poster_action_strip"))
        onAllNodesWithText("Silo")[0].performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 1_000) { exists("poster_strip_quick_look") }

        onNodeWithTag("poster_strip_quick_look").performClick()
        waitUntil(timeoutMillis = 3_000) { exists("poster_quick_look_panel") }
        assertEquals(first.id, PosterQuickLook.open?.item?.id)

        PosterQuickLook.move(1)
        waitForIdle()
        assertEquals(second.id, PosterQuickLook.open?.item?.id)

        // Fullscreen grows the same trailer in place; Esc (back) collapses it, then closes Quick look.
        onNodeWithTag("poster_quick_look_expand").performClick()
        waitForIdle()
        assertTrue(PosterQuickLook.expanded)
        PosterQuickLook.move(1)
        assertEquals(second.id, PosterQuickLook.open?.item?.id, "the expanded trailer stays on its title")
        PosterQuickLook.back()
        waitForIdle()
        assertFalse(PosterQuickLook.expanded)
        assertEquals(second.id, PosterQuickLook.open?.item?.id)

        // Clicking the picture toggles Fullscreen; the title opens Details and closes Quick look.
        onNodeWithTag("poster_quick_look_trailer").performClick()
        waitForIdle()
        assertTrue(PosterQuickLook.expanded)
        onNodeWithTag("poster_quick_look_trailer").performClick()
        waitForIdle()
        assertFalse(PosterQuickLook.expanded)
        onNodeWithTag("poster_quick_look_title").performClick()
        waitForIdle()
        assertEquals(listOf(second.id), openedDetails)
        assertNull(PosterQuickLook.open)

        PosterQuickLook.close()
        waitUntil(timeoutMillis = 3_000) { !exists("poster_quick_look") }
        assertNull(PosterQuickLook.open)
    }
}
