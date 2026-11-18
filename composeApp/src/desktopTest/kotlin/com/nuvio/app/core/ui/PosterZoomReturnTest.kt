package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class PosterZoomReturnTest {
    @Test fun displayedPosterDoesNotBlankDuringSameArtworkPreparation() = runDesktopComposeUiTest(width = 600, height = 600) {
        val file = java.io.File("build/reports/poster-return/readiness.png").apply { parentFile.mkdirs() }
        val bitmap = java.awt.image.BufferedImage(100, 150, java.awt.image.BufferedImage.TYPE_INT_RGB)
        bitmap.createGraphics().apply { color = java.awt.Color.CYAN; fillRect(0, 0, 100, 150); dispose() }
        javax.imageio.ImageIO.write(bitmap, "png", file)
        var pending by mutableStateOf(false)
        var imageUrl by mutableStateOf(file.toURI().toString())
        setContent {
            AppEnvironment {
                Box(Modifier.padding(24.dp)) {
                    NuvioPosterCard("Fixture", imageUrl,
                        modifier = Modifier.testTag("ready-card"),
                        revealArtworkTogether = true, artworkBadge = "03:13",
                        artworkPending = pending, showArtwork = !pending,
                    )
                }
            }
        }
        val bounds = onNodeWithTag("ready-card").fetchSemanticsNode().boundsInRoot
        fun hasImage(): Boolean {
            val image = onRoot().captureToImage().toAwtImage()
            val pixel = java.awt.Color(image.getRGB(bounds.center.x.toInt(), bounds.top.toInt() + 30))
            return pixel.green > 220 && pixel.blue > 220 && pixel.red < 30
        }
        waitUntil(timeoutMillis = 10_000) { hasImage() }
        onNodeWithText("03:13").assertIsDisplayed()
        runOnIdle { pending = true }
        assertTrue(hasImage(), "A displayed poster must not return to its blank readiness placeholder")
        onNodeWithText("03:13").assertIsDisplayed()
        runOnIdle { pending = false }
        assertTrue(hasImage())
        val replacement = java.io.File(file.parentFile, "replacement.png")
        bitmap.createGraphics().apply { color = java.awt.Color.MAGENTA; fillRect(0, 0, 100, 150); dispose() }
        javax.imageio.ImageIO.write(bitmap, "png", replacement)
        runOnIdle { pending = true; imageUrl = replacement.toURI().toString() }
        assertFalse(hasImage(), "A different artwork identity must not reuse the old revealed image")
        onNodeWithText("03:13").assertDoesNotExist()
        runOnIdle { pending = false }
        waitUntil(timeoutMillis = 10_000) {
            val image = onRoot().captureToImage().toAwtImage()
            val pixel = java.awt.Color(image.getRGB(bounds.center.x.toInt(), bounds.top.toInt() + 30))
            pixel.red > 220 && pixel.blue > 220 && pixel.green < 30
        }
        onNodeWithText("03:13").assertIsDisplayed()
    }

    @Test fun imageCardNeverBlanksAfterPortalAndBlurAreRemoved() = verifyPortalReturn(readsCallerAnchor = false)

    /**
     * History's portal lambda reads its mutable anchor, as the recorded flicker did: dismissal clears
     * it a frame before the portal leaves the shell host, so the overlay must keep the opened card.
     */
    @Test fun portalOverlayReadingClearedAnchorDoesNotCoverReturnedCard() = verifyPortalReturn(readsCallerAnchor = true)

    /**
     * With a stationary pointer the card is still hover-scaled when the menu closes. The overlay must
     * land on the card's transformed bounds, not its scaled origin plus unscaled size.
     */
    @Test fun hoverScaledCardReturnsWithoutShift() = verifyHoverReturn(moveAway = false)

    /** Moving off the card while the menu is open must still return it unscaled, without a shrink. */
    @Test fun cardLeftDuringMenuReturnsUnscaled() = verifyHoverReturn(moveAway = true)

    private fun verifyHoverReturn(moveAway: Boolean) = runDesktopComposeUiTest(width = 600, height = 600) {
        val file = java.io.File("build/reports/poster-return/hover.png").apply { parentFile.mkdirs() }
        val bitmap = java.awt.image.BufferedImage(160, 90, java.awt.image.BufferedImage.TYPE_INT_RGB)
        bitmap.createGraphics().apply { color = java.awt.Color.CYAN; fillRect(0, 0, 160, 90); dispose() }
        javax.imageio.ImageIO.write(bitmap, "png", file)
        var anchor by mutableStateOf<PosterZoomAnchor?>(null)
        var dismissed = false
        setContent {
            AppEnvironment {
                val host = remember { PosterZoomOverlayHostState() }
                val haze = rememberHazeState()
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.fillMaxSize().then(if (host.content != null) Modifier.hazeSource(haze) else Modifier)) {
                        CompositionLocalProvider(LocalPosterZoomOverlayHost provides host) {
                            Box(Modifier.padding(start = 120.dp, top = 160.dp)) {
                                NuvioPosterCard(
                                    title = "Fixture", imageUrl = file.toURI().toString(),
                                    modifier = Modifier.testTag("hover-card"),
                                    shape = NuvioPosterShape.Landscape,
                                    revealArtworkTogether = true,
                                    onLongClick = { anchor = PosterZoomAnchorHolder.consume() },
                                )
                            }
                            anchor?.let { selected ->
                                PosterZoomOverlayPortal { rootHaze ->
                                    NuvioPosterZoomActionOverlay(
                                        imageUrl = file.toURI().toString(), title = "Fixture", subtitle = null,
                                        anchor = selected, actions = emptyList(), hazeState = rootHaze,
                                        onDismissed = { dismissed = true; anchor = null },
                                    )
                                }
                            }
                        }
                    }
                    host.content?.invoke(haze)
                }
            }
        }
        fun cyanBox(): IntArray? {
            val frame = onRoot().captureToImage().toAwtImage()
            var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1
            for (y in 0 until frame.height) for (x in 0 until frame.width) {
                val p = java.awt.Color(frame.getRGB(x, y))
                if (p.green > 200 && p.blue > 200 && p.red < 60) {
                    if (x < left) left = x; if (y < top) top = y; if (x > right) right = x; if (y > bottom) bottom = y
                }
            }
            return if (right < 0) null else intArrayOf(left, top, right, bottom)
        }
        waitUntil(timeoutMillis = 10_000) { cyanBox() != null }
        val center = onNodeWithTag("hover-card").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val hovered = checkNotNull(cyanBox())
        onRoot().performMouseInput { click(center, MouseButton.Secondary) }
        mainClock.advanceTimeBy(1_500)
        waitForIdle()
        val dismissAt = if (moveAway) Offset(560f, 560f) else center
        if (moveAway) {
            onRoot().performMouseInput { moveTo(dismissAt) }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
        mainClock.autoAdvance = false
        onRoot().performMouseInput { click(dismissAt) }
        var lastOverlayFrame: IntArray? = null
        var firstReturnedFrame: IntArray? = null
        repeat(120) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val box = cyanBox()
            if (!dismissed) lastOverlayFrame = box
            else if (firstReturnedFrame == null) firstReturnedFrame = box
        }
        mainClock.autoAdvance = true
        val settled = checkNotNull(cyanBox())
        val landing = checkNotNull(lastOverlayFrame) { "Overlay frame before return was not captured" }
        fun near(a: IntArray, b: IntArray) = a.indices.all { kotlin.math.abs(a[it] - b[it]) <= 2 }
        assertTrue(if (moveAway) settled[2] - settled[0] < hovered[2] - hovered[0] else near(settled, hovered),
            "Settled card ${settled.toList()} has the wrong hover scale (hovered ${hovered.toList()}, moveAway=$moveAway)")
        assertTrue(near(landing, settled),
            "Overlay landed at ${landing.toList()} but the card settled at ${settled.toList()} (hovered ${hovered.toList()}, first returned ${firstReturnedFrame?.toList()})")
    }

    private fun verifyPortalReturn(readsCallerAnchor: Boolean) = runDesktopComposeUiTest(width = 600, height = 600) {
        val file = java.io.File("build/reports/poster-return/fixture.png").apply { parentFile.mkdirs() }
        val bitmap = java.awt.image.BufferedImage(100, 150, java.awt.image.BufferedImage.TYPE_INT_RGB)
        bitmap.createGraphics().apply { color = java.awt.Color.CYAN; fillRect(0, 0, 100, 150); dispose() }
        javax.imageio.ImageIO.write(bitmap, "png", file)
        var anchor by mutableStateOf<PosterZoomAnchor?>(null)
        var dismissed = false
        setContent {
            AppEnvironment {
                val host = remember { PosterZoomOverlayHostState() }
                val haze = rememberHazeState()
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.fillMaxSize().then(if (host.content != null) Modifier.hazeSource(haze) else Modifier)) {
                        CompositionLocalProvider(LocalPosterZoomOverlayHost provides host) {
                            Box(Modifier.padding(24.dp)) {
                                NuvioPosterCard(
                                    title = "Fixture", imageUrl = file.toURI().toString(),
                                    modifier = Modifier.testTag("image-card"),
                                    revealArtworkTogether = true, artworkBadge = "03:13",
                                    onLongClick = { anchor = PosterZoomAnchorHolder.consume() },
                                )
                            }
                            anchor?.let { selected ->
                                PosterZoomOverlayPortal { rootHaze ->
                                    val current = if (readsCallerAnchor) anchor else selected
                                    NuvioPosterZoomActionOverlay(
                                        // History falls back to the series poster once its anchor is cleared.
                                        imageUrl = if (readsCallerAnchor) current?.imageUrl else file.toURI().toString(),
                                        title = "Fixture", subtitle = null,
                                        anchor = current, actions = emptyList(), hazeState = rootHaze,
                                        onDismissed = { dismissed = true; anchor = null },
                                    )
                                }
                            }
                        }
                    }
                    host.content?.invoke(haze)
                }
            }
        }
        val bounds = onNodeWithTag("image-card").fetchSemanticsNode().boundsInRoot
        val x = (bounds.left + bounds.width / 2).toInt()
        val y = (bounds.top + 30).toInt()
        fun isCyan(): Boolean {
            val frame = onRoot().captureToImage().toAwtImage()
            val pixel = java.awt.Color(frame.getRGB(x, y))
            return pixel.green > 220 && pixel.blue > 220 && pixel.red < 30
        }
        waitUntil(timeoutMillis = 10_000) { isCyan() }
        repeat(3) { cycle ->
            dismissed = false
            onNodeWithTag("image-card").performMouseInput { click(button = MouseButton.Secondary) }
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            mainClock.autoAdvance = false
            onRoot().performMouseInput { click(Offset(550f, 550f)) }
            var returnedFrames = 0
            repeat(100) { index ->
                mainClock.advanceTimeByFrame()
                waitForIdle()
                if (dismissed) {
                    returnedFrames++
                    assertTrue(isCyan(), "Blank image after return: cycle=$cycle frame=$index")
                }
            }
            assertTrue(returnedFrames > 5)
            mainClock.autoAdvance = true
        }
    }

}
