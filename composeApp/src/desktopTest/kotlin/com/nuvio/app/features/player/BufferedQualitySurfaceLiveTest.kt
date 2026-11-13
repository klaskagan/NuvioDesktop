package com.nuvio.app.features.player

import androidx.compose.ui.awt.ComposeWindow
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.player.desktop.*
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.*

/** Production PlayerScreen + AutoSync wrapper + source-key effects, with isolated synthetic sources. */
class BufferedQualitySurfaceLiveTest {
    @Test fun productionSurfaceAdoptsTheBufferedRendererWithoutReloadingIt() = runBlocking {
        assumeTrue(System.getenv("NUVIO_QUALITY_NATIVE_TEST") == "1" && DesktopHostOs.current == DesktopHostOs.MACOS)
        MoviePlayerControlsFixture.seedMetadata()
        val high = File("build/qa/quality/720p.mkv").absoluteFile
        val low = File("build/qa/quality/360p.mp4").absoluteFile
        check(high.isFile && low.isFile)
        val sources = listOf(
            StreamItem(name = "720p", url = high.toURI().toString(), addonName = "Local fixture", addonId = "fixture"),
            StreamItem(name = "360p", url = low.toURI().toString(), addonName = "Local fixture", addonId = "fixture"),
        )
        PlayerStreamsRepository.clearAll()
        check(PlayerStreamsRepository.primeSourcesFromSelection("movie", "movie-controls-fixture", null, null,
            StreamsUiState(requestToken = StreamsRepository.requestToken("movie", "movie-controls-fixture"),
                groups = listOf(AddonStreamGroup("Local fixture", "fixture", streams = sources)))))
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(1100, 750)
                setContent {
                    AppEnvironment {
                        PlayerScreen(profileId = ProfileRepository.activeProfileId, title = "Buffered quality surface fixture",
                            sourceUrl = high.toURI().toString(), providerName = "Local fixture", streamTitle = "720p",
                            streamSubtitle = null, parentMetaId = "movie-controls-fixture", parentMetaType = "movie",
                            contentType = "movie", videoId = "movie-controls-fixture", stream = sources.first(),
                            onBack = { release -> release({}, {}) })
                    }
                }
                isVisible = true
            }
        }
        try {
            val currentController = DesktopPlayerPictureInPicture.javaClass.getDeclaredField("controller").apply { isAccessible = true }
            var player: NativePlayerController? = null
            withTimeout(20_000) {
                while (true) {
                    player = currentController.get(DesktopPlayerPictureInPicture) as? NativePlayerController
                    if (player?.snapshot()?.videoHeight == 720 && player?.snapshot()?.isLoading == false) break
                    delay(100)
                }
            }
            val native = player!!
            val handleField = native.javaClass.getDeclaredField("handle").apply { isAccessible = true }
            NativePlayerBridge.qualitySet(handleField.getLong(native), false, "mute", "yes")
            val controlsField = native.javaClass.getDeclaredField("controlsState").apply { isAccessible = true }
            withTimeout(10_000) {
                while ((controlsField.get(native) as PlayerControlsState).qualityItems.none { it.height == 360 }) delay(100)
            }
            // Any new creation on this controller would mean Compose discarded the promoted renderer.
            val createField = native.javaClass.getDeclaredField("nativeCreate").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") val originalCreate = createField.get(native) as NativePlayerCreate
            val unexpectedCreates = AtomicInteger()
            val recordingCreate: NativePlayerCreate = { view, url, headers, play, position, controls, decoder, rtx, sink ->
                unexpectedCreates.incrementAndGet()
                originalCreate(view, url, headers, play, position, controls, decoder, rtx, sink)
            }
            createField.set(native, recordingCreate)
            val sink = native.javaClass.getDeclaredField("eventSink").apply { isAccessible = true }.get(native) as NativePlayerEventSink
            sink.onPlayerEvent("selectQuality", 360.0)
            withTimeout(30_000) {
                while (true) {
                    val state = controlsField.get(native) as PlayerControlsState
                    if (native.snapshot().videoHeight == 360 && state.qualityRequestedHeight == 360 && !state.qualityBusy) break
                    delay(100)
                }
            }
            val promotedHandle = handleField.getLong(native)
            delay(1000)
            assertEquals(0, unexpectedCreates.get(), "The buffered renderer must not be recreated by the source-key effects")
            assertEquals(promotedHandle, handleField.getLong(native))
            assertTrue(native.snapshot().isPlaying)
            assertTrue(NativePlayerBridge.videoFrameSignature(promotedHandle) != 0L)
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
            PlayerStreamsRepository.clearAll()
        }
    }
}
