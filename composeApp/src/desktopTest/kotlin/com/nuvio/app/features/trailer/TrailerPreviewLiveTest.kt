package com.nuvio.app.features.trailer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class TrailerPreviewLiveTest {
    @Test fun avPlayerPreviewProducesChangingPixels() {
        assumeTrue(System.getenv("NUVIO_TRAILER_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val ids = if (System.getenv("NUVIO_TRAILER_LIVE_ALL") == "1") listOf("R5jcEBVBEbQ", "4nhu80EmVQE") else listOf("R5jcEBVBEbQ")
        ids.forEach(::verifyPreview)
    }

    private fun verifyPreview(videoId: String) {
        val source = runBlocking {
            TrailerPlaybackResolver.resolveFromYouTubeUrl("https://www.youtube.com/watch?v=$videoId", target = TrailerPlaybackTarget.FRAME_PREVIEW)
        }
        checkNotNull(source) { "No preview source for $videoId" }
        check(source.audioUrl == null) { "Expected the AVPlayer HLS/combined source preference" }
        runDesktopComposeUiTest(width = 640, height = 360) {
            var ready = false
            var failed = false
            setContent {
                NuvioTheme {
                    HeroTrailerPlayerSurface(
                        sourceUrl = source.videoUrl, sourceAudioUrl = source.audioUrl,
                        playWhenReady = true, muted = true, startPositionMillis = 0, fillFrame = false,
                        modifier = Modifier.fillMaxSize(), onReady = { ready = true },
                        onEnded = {}, onError = { failed = true },
                    )
                }
            }
            waitUntil(timeoutMillis = 25_000) { ready || failed }
            check(ready && !failed) { "Preview failed before its first decoded frame" }
            fun visibleFrameHash(): Int? {
                val image = onRoot().captureToImage().toAwtImage()
                var sum = 1
                var visible = 0
                var samples = 0
                for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) {
                    val pixel = image.getRGB(x, y)
                    sum = sum * 31 + pixel
                    if (((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255) > 96) visible++
                    samples++
                }
                return sum.takeIf { visible > samples / 20 }
            }
            var first: Int? = null
            waitUntil(timeoutMillis = 20_000) { visibleFrameHash().also { first = it } != null }
            waitUntil(timeoutMillis = 10_000) { visibleFrameHash()?.let { it != first } == true }
            val directory = File("build/reports/trailer-playback").apply { mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "avplayer-preview-$videoId.png"))
        }
    }
}
