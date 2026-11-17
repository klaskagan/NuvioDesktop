package com.nuvio.app.features.trailer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.CornerTrailerStyle
import com.nuvio.app.features.details.HeroTrailerLayout
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaDetailsScreen
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaTrailer
import com.nuvio.app.features.details.components.DesktopHeroTrailerPreview
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.features.details.components.rememberDesktopHeroTrailer
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

    @Test fun pinnedPreviewDrawsChangingPixelsAfterStartingPaused() {
        assumeTrue(System.getenv("NUVIO_TRAILER_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val url = "https://www.youtube.com/watch?v=zckJCxYxn1g"
        val source = runBlocking {
            TrailerPlaybackResolver.resolveFromYouTubeUrl(url, target = TrailerPlaybackTarget.DESKTOP_DETAILS_HERO)
        }
        val rendition = checkNotNull(source?.heroRenditions?.firstOrNull())
        runDesktopComposeUiTest(width = 640, height = 360) {
            val playWhenReady = mutableStateOf(false)
            setContent {
                NuvioTheme {
                    val surface = rememberDesktopHeroTrailer(
                        preview = DesktopHeroTrailerPreview(rendition, "Pinned preview fixture", url, visible = true),
                        playWhenReady = playWhenReady.value,
                        muted = true,
                        onReady = {},
                        onTerminal = {},
                    )
                    surface?.invoke(Modifier.fillMaxSize(), false)
                }
            }
            runOnIdle { playWhenReady.value = true }
            verifyDetailsHeroPixels()
        }
    }

    @Test fun detailsHeroDrawsChangingPixelsInBackdropAndAllCornerStyles() {
        assumeTrue(System.getenv("NUVIO_TRAILER_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val meta = MetaDetails(
            id = "hero-rendering-fixture",
            type = "movie",
            name = "Hero rendering fixture",
            trailers = listOf(MetaTrailer(
                "hero", "zckJCxYxn1g", "Preview", "YouTube", type = "Trailer", size = 2160,
            )),
        )
        MetaDetailsRepository.clear()
        MetaScreenSettingsRepository.resetToDefaults()
        MetaScreenSettingsRepository.setHeroTrailerPlayback(true)
        val cacheField = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(MetaDetailsRepository) as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["movie:${meta.id}"] = constructor.newInstance(meta, null, null)
        runDesktopComposeUiTest(width = 1280, height = 820) {
            setContent { AppEnvironment { MetaDetailsScreen("movie", meta.id, onBack = {}) } }
            verifyDetailsHeroPixels()
            runOnIdle { MetaScreenSettingsRepository.setHeroTrailerLayout(HeroTrailerLayout.Corner) }
            for (style in CornerTrailerStyle.entries) {
                runOnIdle { MetaScreenSettingsRepository.setCornerTrailerStyle(style) }
                verifyDetailsHeroPixels()
            }
        }
    }

    private fun ComposeUiTest.verifyDetailsHeroPixels() {
        fun brightFrameHash(): Int? {
            val image = onRoot().captureToImage().toAwtImage()
            var hash = 1
            var bright = 0
            var samples = 0
            for (y in image.height / 8 until image.height / 3 step 9) {
                for (x in image.width * 2 / 3 until image.width * 9 / 10 step 9) {
                    val pixel = image.getRGB(x, y)
                    hash = hash * 31 + pixel
                    if (((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255) > 130) bright++
                    samples++
                }
            }
            return hash.takeIf { bright > samples / 10 }
        }
        var first: Int? = null
        waitUntil(timeoutMillis = 25_000) { brightFrameHash().also { first = it } != null }
        mainClock.advanceTimeBy(600)
        waitUntil(timeoutMillis = 10_000) { brightFrameHash()?.let { it != first } == true }
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
