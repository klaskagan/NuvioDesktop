package com.nuvio.app.features.trailer

import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.*
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.graphics.toAwtImage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Exercises the actual Details shelf handler with invalid, offline-only video IDs. */
@OptIn(ExperimentalTestApi::class)
class DetailsTrailerUiTest {
    @Test fun nativeDetailsOpensOverlayAndOnlyExpandEntersFullscreen() = checkOverlay(TrailerPlayerBackend.Native)
    @Test fun youtubeDetailsOpensOverlayAndOnlyExpandEntersFullscreen() = checkOverlay(TrailerPlayerBackend.YouTube)

    private fun checkOverlay(backend: TrailerPlayerBackend) = runDesktopComposeUiTest(width = 1280, height = 1000) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.setTrailerPlayerBackend(backend)
        MetaScreenSettingsRepository.setHeroTrailerPlayback(false)
        MetaScreenSettingsRepository.setEnabled(MetaScreenSectionKey.TRAILERS, true)
        MetaDetailsRepository.clear()
        val meta = MetaDetails(
            "trailer-ui-fixture", "movie", "Hope", releaseInfo = "2026",
            trailers = listOf(MetaTrailer("hope-teaser", "invalid", "Nothing can prepare you for it.", "YouTube", type = "Teaser")),
        )
        val cacheField = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(MetaDetailsRepository) as MutableMap<String, Any>
        val ctor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["movie:${meta.id}"] = ctor.newInstance(meta, null, null)
        setContent { AppEnvironment { MetaDetailsScreen("movie", meta.id, onBack = {}) } }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithContentDescription("Nothing can prepare you for it.").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription("Nothing can prepare you for it.").performScrollTo().performClick()
        onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        onNodeWithContentDescription("Exit fullscreen").assertDoesNotExist()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        val initial = onNodeWithContentDescription("Enter fullscreen").fetchSemanticsNode().boundsInRoot
        onNodeWithText("Retry").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        assertEquals(initial, onNodeWithContentDescription("Enter fullscreen").fetchSemanticsNode().boundsInRoot)
        save("overlay-error")
        onNodeWithContentDescription("Enter fullscreen").performClick()
        onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        onNodeWithContentDescription("Enter fullscreen").assertDoesNotExist()
        onNodeWithText("Retry").assertIsDisplayed()
        save("fullscreen-error")
        onNodeWithContentDescription("Exit fullscreen").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Enter fullscreen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        onNodeWithContentDescription("Close trailer").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Enter fullscreen").fetchSemanticsNodes().isEmpty() }
        onNodeWithContentDescription("Nothing can prepare you for it.").assertIsDisplayed()
    }

    private fun ComposeUiTest.save(name: String) {
        mainClock.advanceTimeBy(100)
        waitForIdle()
        val dir = File("build/reports/trailer-playback").apply { mkdirs() }
        assertTrue(ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png")))
    }
}
