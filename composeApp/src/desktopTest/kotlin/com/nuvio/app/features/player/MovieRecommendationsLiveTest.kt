package com.nuvio.app.features.player

import androidx.compose.ui.awt.ComposeWindow
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.desktop.*
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Native player fixture: local media, isolated profile, no real account or catalog changes. */
class MovieRecommendationsLiveTest {
    @Test fun nativeMovieCardOpensDetailsAfterReleasingPlayback() {
        assumeTrue(System.getenv("NUVIO_PLAYER_LIVE_TEST") == "1")
        val media = File("build/qa/movie-recommendations.mp4").absoluteFile
        check(media.isFile) { "Generate the local QA movie before running the live fixture" }
        val recommendations = (1..20).map { MetaPreview("recommendation-fixture-$it", "movie", "Title $it") }
        val selected = recommendations.last()
        MoviePlayerControlsFixture.seedMetadata(recommendations)
        val opened = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setLocation(80, 80)
                setSize(1100, 750)
                setContent { MoviePlayerControlsFixture.Content(media,
                    onBack = { released.set(true) },
                    onOpenMetaDetails = { check(it.id == selected.id); check(released.get()); opened.set(true) },
                ) }
                isVisible = true
            }
        }
        try {
            val field = DesktopPlayerPictureInPicture.javaClass.getDeclaredField("controller").apply { isAccessible = true }
            var controller: NativePlayerController? = null
            await("native decoding") {
                controller = field.get(DesktopPlayerPictureInPicture) as? NativePlayerController
                controller?.snapshot()?.positionMs?.let { it > 500 } == true
            }
            val player = checkNotNull(controller)
            val handle = player.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(player)
            check(NativePlayerBridge.controlsOverlayAvailable(handle))
            // Wait for the shelf to open, then scroll to its trailing edge.
            val robot = Robot()
            robot.mouseMove(window.x + window.width / 2, window.y + window.height / 2)
            Thread.sleep(1_500)
            robot.mouseMove(window.x + window.width - 21, window.y + window.height - 128)
            repeat(3) {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                robot.delay(400)
            }
            val output = File("build/qa/movie-recommendations-native.png").apply { parentFile.mkdirs() }
            ImageIO.write(robot.createScreenCapture(window.bounds), "png", output)
            // The final poster sits against the trailing gutter at the end of the row.
            robot.mouseMove(window.x + window.width - 70, window.y + window.height - 100)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            await("recommendation detail callback after player release") { opened.get() }
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 25_000_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        error("Timed out: $label")
    }
}
