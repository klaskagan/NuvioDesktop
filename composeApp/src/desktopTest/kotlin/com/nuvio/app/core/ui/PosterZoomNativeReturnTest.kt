package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.MainAppContent
import com.nuvio.app.AppScreenTab
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.tracking.*
import kotlinx.coroutines.flow.MutableStateFlow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/** Real macOS rendering: opt-in because this fixture opens a window and moves the pointer. */
class PosterZoomNativeReturnTest {
    @Test fun imageSurvivesNativeBlurTeardown() = verify(false)
    @Test fun historySurvivesNativeBlurTeardown() = verify(true)
    private fun verify(history: Boolean) {
        assumeTrue(System.getenv("NUVIO_POSTER_LIVE_TEST") == "1")
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val directory = File("build/reports/poster-return-native/${if (history) "history" else "poster"}").apply { mkdirs() }
        val file = File(directory, "fixture.png")
        val bitmap = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
        bitmap.createGraphics().apply { color = java.awt.Color.CYAN; fillRect(0, 0, 320, 180); dispose() }
        ImageIO.write(bitmap, "png", file)
        val session = TrackingHistoryAccount(1, 1)
        val reader = object : TrackingHistoryProvider {
            override val providerId = TrackingProviderId.SIMKL
            override val account = MutableStateFlow<TrackingHistoryAccount?>(session)
            override suspend fun loadPage(profileId: Int, cursor: String?) = TrackingHistoryPage(
                (1..48).map { index ->
                    val artwork = File(directory, "episode-$index.png")
                    ImageIO.write(bitmap, "png", artwork)
                    TrackingHistoryEvent("event-$index", 1_765_000_000_000L - index * 1000L,
                    TrackingMediaReference(TrackingMediaKind.SHOW, title = "Fixture", episode = TrackingEpisode(1, index, "Episode $index")),
                    episodeScreenshot = artwork.toURI().toString()) }, null,
            )
        }
        if (history) TrackingProviderRegistry.registerHistoryReader(reader)
        PosterCardStyleRepository.ensureLoaded()
        PosterCardStyleRepository.setHistoryLandscapeModeEnabled(true)
        val dismissed = AtomicBoolean(false)
        val overlayWasOpen = AtomicBoolean(false)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setLocation(100, 100)
                setSize(800, 700)
                isAlwaysOnTop = true
                setContent {
                    AppEnvironment {
                        var anchor by remember { mutableStateOf<PosterZoomAnchor?>(null) }
                        val host = remember { PosterZoomOverlayHostState() }
                        val haze = rememberHazeState()
                        val visible = PosterZoomOverlayCoordinator.isVisible
                        SideEffect {
                            if (visible) overlayWasOpen.set(true)
                            else if (overlayWasOpen.compareAndSet(true, false)) dismissed.set(true)
                        }
                        if (history) MainAppContent(initialTab = AppScreenTab.History, ownsAppRuntime = false, showLaunchOverlay = false)
                        else Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.fillMaxSize().then(if (host.content != null) Modifier.hazeSource(haze) else Modifier)) {
                                CompositionLocalProvider(LocalPosterZoomOverlayHost provides host) {
                                    Box(Modifier.padding(24.dp)) {
                                        NuvioPosterCard("Fixture", file.toURI().toString(),
                                            shape = NuvioPosterShape.Landscape,
                                            revealArtworkTogether = true, artworkBadge = "03:13",
                                            onLongClick = { anchor = PosterZoomAnchorHolder.consume() },
                                        )
                                    }
                                    anchor?.let { selected ->
                                        PosterZoomOverlayPortal { rootHaze ->
                                            NuvioPosterZoomActionOverlay(null, "Fixture", null,
                                                anchor = selected, actions = emptyList(), hazeState = rootHaze,
                                                onDismissed = { dismissed.set(true); anchor = null },
                                            )
                                        }
                                    }
                                }
                            }
                            host.content?.invoke(haze)
                        }
                    }
                }
                isVisible = true
                toFront()
            }
        }
        try {
            val robot = Robot()
            Thread.sleep(2_000)
            robot.mouseMove(window.x + 400, window.y + 14)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.delay(80)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            robot.delay(250)
            val origin = window.contentPane.locationOnScreen
            if (history) {
                robot.mouseMove(window.x + 600, window.y + 400)
                robot.mouseWheel(8)
                Thread.sleep(1_000)
            }
            val initial = robot.createScreenCapture(window.bounds)
            val firstCyan = (0 until initial.height).firstNotNullOfOrNull { yy ->
                (0 until initial.width).firstOrNull { xx ->
                    java.awt.Color(initial.getRGB(xx, yy)).let { it.green > 220 && it.blue > 220 && it.red < 30 }
                }?.let { xx -> xx to yy }
            }
            checkNotNull(firstCyan) { "No image visible in native fixture" }
            val x = window.x + firstCyan.first + 50
            val y = window.y + firstCyan.second + 30
            fun cyan() = robot.getPixelColor(x, y).let { it.green > 220 && it.blue > 220 && it.red < 30 }
            ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(directory, "initial.png"))
            assertTrue(cyan(), "Fixture image must be visible before opening at $x,$y; origin=$origin pixel=${robot.getPixelColor(x, y)}")
            val bad = mutableListOf<String>()
            repeat(5) { cycle ->
                dismissed.set(false)
                robot.mouseMove(x, y)
                robot.delay(100)
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                robot.delay(80)
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                val openDeadline = System.nanoTime() + 5_000_000_000L
                while (!overlayWasOpen.get() && System.nanoTime() < openDeadline) Thread.sleep(20)
                assertTrue(overlayWasOpen.get(), "Native right click must open the menu before testing return")
                Thread.sleep(1_200)
                ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(directory, "opened-$cycle.png"))
                robot.mouseMove(x, y)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.delay(80)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                val deadline = System.nanoTime() + 6_000_000_000L
                var observedDismissalAt = 0L
                var sample = 0
                var movedAway = false
                while (System.nanoTime() < deadline) {
                    if (dismissed.get() && observedDismissalAt == 0L) observedDismissalAt = System.nanoTime()
                    if (observedDismissalAt != 0L && System.nanoTime() - observedDismissalAt > 1_000_000_000L) break
                    if (dismissed.get() && !movedAway) {
                        robot.mouseMove(origin.x + 750, origin.y + 620)
                        movedAway = true
                    }
                    if (dismissed.get() && !cyan()) {
                        bad += "$cycle:$sample"
                        ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(directory, "blank-$cycle-$sample.png"))
                    }
                    sample++
                    Thread.sleep(5)
                }
                ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(directory, "closed-$cycle.png"))
                assertTrue(dismissed.get(), "Outside click must dismiss")
            }
            assertTrue(bad.isEmpty(), "Native image blanked after return: $bad")
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
            if (history) TrackingProviderRegistry.unregisterHistoryReader(reader)
        }
    }
}
