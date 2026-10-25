package com.nuvio.app.features.trailer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network/native check; never loads an account or a real profile. */
class TrailerPlaybackLiveTest {
    @Test fun hopeTeaserStartsInNativePlayer() {
        assumeTrue(System.getenv("NUVIO_TRAILER_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val ids = System.getenv("NUVIO_TRAILER_LIVE_IDS")?.split(',') ?: if (System.getenv("NUVIO_TRAILER_LIVE_ALL") == "1") {
            listOf("5gHMar_DZ3Y", "hScuBtStpS0", "sMdU59uUb68", "Ls-wVxqGge4",
                "OFaJzL83y0Q", "R5jcEBVBEbQ", "ODnHr2Emnf0", "Wf0UgltketI", "Rp6ZwGPp4XE", "WBpIxTMjYsc")
        } else listOf("sMdU59uUb68")
        ids.forEach(::verifyVideo)
    }

    @Test fun hopeTeaserStartsInsideTheDetailsOverlay() {
        assumeTrue(System.getenv("NUVIO_TRAILER_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val source = runBlocking {
            TrailerPlaybackResolver.resolveFromYouTubeUrl("https://www.youtube.com/watch?v=sMdU59uUb68")
        }
        checkNotNull(source)
        lateinit var window: androidx.compose.ui.awt.ComposeWindow
        javax.swing.SwingUtilities.invokeAndWait {
            window = androidx.compose.ui.awt.ComposeWindow().apply {
                setSize(1280, 1000)
                setContent {
                    NuvioTheme {
                        com.nuvio.app.features.details.components.TrailerPlayerPopup(
                            visible = true, trailerTitle = "Nothing can prepare you for it.",
                            trailerType = "Teaser", contentTitle = "Hope", playbackSource = source,
                            isLoading = false, errorMessage = null, onDismiss = {},
                        )
                    }
                }
                isVisible = true
            }
        }
        try {
            val owner = com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture
            val field = owner.javaClass.getDeclaredField("controller").apply { isAccessible = true }
            val deadline = System.nanoTime() + 30_000_000_000L
            var player: com.nuvio.app.features.player.desktop.NativePlayerController? = null
            while (System.nanoTime() < deadline && player == null) {
                val candidate = field.get(owner) as? com.nuvio.app.features.player.desktop.NativePlayerController
                if (candidate != null && candidate.snapshot().positionMs > 1_000) player = candidate
                else Thread.sleep(50)
            }
            val playing = checkNotNull(player) { "The overlay did not start native playback" }
            check(playing.getAudioTracks().isNotEmpty()) { "Overlay playback has no audio" }
            val handle = nativeHandle(playing)
            val bounds = com.nuvio.app.features.player.desktop.NativePlayerBridge.embeddedBounds(handle)
            check(bounds[2] in 200.0..700.0 && bounds[3] in 100.0..500.0) {
                "Overlay escaped its player rectangle: ${bounds.toList()}"
            }
            assertChangingPixels(handle)
            javax.swing.SwingUtilities.invokeAndWait { window.setSize(1440, 1080) }
            Thread.sleep(300)
            val resized = com.nuvio.app.features.player.desktop.NativePlayerBridge.embeddedBounds(handle)
            check(resized[2] in 200.0..700.0) { "Resizing expanded the overlay to the whole window" }
            println("Trailer live: Details overlay advanced with video and audio")
        } finally {
            javax.swing.SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    @Test fun ordinaryMediaStillDecodesUsingTheWindowHost() {
        assumeTrue(System.getenv("NUVIO_TRAILER_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val file = java.io.File("build/qa/ordinary.mp4")
        check(file.isFile) { "Generate the local ordinary-media fixture before live tests" }
        lateinit var window: androidx.compose.ui.awt.ComposeWindow
        var player: com.nuvio.app.features.player.desktop.NativePlayerController? = null
        val ready = java.util.concurrent.CountDownLatch(1)
        javax.swing.SwingUtilities.invokeAndWait {
            window = androidx.compose.ui.awt.ComposeWindow().apply {
                setSize(960, 640)
                setContent {
                    NuvioTheme {
                        PlatformPlayerSurface(
                            sourceUrl = file.toURI().toString(), modifier = Modifier.fillMaxSize(),
                            useNativeController = true,
                            onControllerReady = { player = it as? com.nuvio.app.features.player.desktop.NativePlayerController },
                            onSnapshot = { if (it.positionMs > 500) ready.countDown() }, onError = {},
                        )
                    }
                }
                isVisible = true
            }
        }
        try {
            check(ready.await(15, java.util.concurrent.TimeUnit.SECONDS))
            val handle = nativeHandle(checkNotNull(player))
            check(com.nuvio.app.features.player.desktop.NativePlayerBridge.embeddedBounds(handle).all { it == 0.0 })
            assertChangingPixels(handle)
        } finally { javax.swing.SwingUtilities.invokeAndWait { window.dispose() } }
    }

    private fun verifyVideo(videoId: String) {
        val source = runBlocking {
            TrailerPlaybackResolver.resolveFromYouTubeUrl("https://www.youtube.com/watch?v=$videoId")
        }
        checkNotNull(source) { "Hope teaser extraction failed" }
        println("Trailer live: videoId=$videoId separateAudio=${source.audioUrl != null}")
        val advanced = java.util.concurrent.CountDownLatch(1)
        var snapshot = PlayerPlaybackSnapshot()
        var error: String? = null
        var controller: com.nuvio.app.features.player.PlayerEngineController? = null
        lateinit var window: androidx.compose.ui.awt.ComposeWindow
        javax.swing.SwingUtilities.invokeAndWait {
            window = androidx.compose.ui.awt.ComposeWindow().apply {
                setSize(960, 640)
                setContent {
                    NuvioTheme {
                        PlatformPlayerSurface(
                            sourceUrl = source.videoUrl,
                            sourceAudioUrl = source.audioUrl,
                            useYoutubeChunkedPlayback = true,
                            modifier = Modifier.fillMaxSize(),
                            onControllerReady = { controller = it },
                            onSnapshot = { snapshot = it; if (it.positionMs > 1_000) advanced.countDown() },
                            onError = { error = it; if (it != null) advanced.countDown() },
                        )
                    }
                }
                isVisible = true
            }
        }
        try {
            advanced.await(30, java.util.concurrent.TimeUnit.SECONDS)
            println("Trailer live: position=${snapshot.positionMs} duration=${snapshot.durationMs} loading=${snapshot.isLoading} error=$error")
            (controller as? com.nuvio.app.features.player.desktop.NativePlayerController)?.let { native ->
                val field = native.javaClass.getDeclaredField("handle").apply { isAccessible = true }
                val handle = field.getLong(native)
                if (handle != 0L) {
                    listOf("mpv-version", "ffmpeg-version", "idle-active", "core-idle", "pause", "paused-for-cache", "file-format", "track-list/count", "audio-codec-name", "video-codec", "duration", "time-pos", "cache-buffering-state").forEach {
                        println("Trailer live: $it=" + com.nuvio.app.features.player.desktop.NativePlayerBridge.stringProperty(handle, it))
                    }
                }
            }
            check(error == null) { "Native playback failed: $error" }
            check(snapshot.positionMs > 1_000) { "Native playback never advanced" }
            check(controller?.getAudioTracks()?.isNotEmpty() == true) { "Trailer has no audio track" }
            val player = checkNotNull(controller as? com.nuvio.app.features.player.desktop.NativePlayerController)
            assertChangingPixels(nativeHandle(player))
            player.pause()
            awaitNative { !player.snapshot().isPlaying }
            val seekPosition = minOf(5_000L, snapshot.durationMs / 2)
            player.seekTo(seekPosition)
            awaitNative {
                val seconds = com.nuvio.app.features.player.desktop.NativePlayerBridge.stringProperty(nativeHandle(player), "time-pos").toDoubleOrNull()
                seconds != null && kotlin.math.abs(seconds * 1_000 - seekPosition) < 500
            }
            check(!player.snapshot().isPlaying) { "Seeking resumed a paused trailer" }
            player.play()
            awaitNative { player.snapshot().positionMs > seekPosition + 500 }
            println("Trailer live: pause, seek, and resume passed for $videoId")
            System.getenv("NUVIO_TRAILER_LIVE_HOLD_MS")?.toLongOrNull()?.coerceIn(0, 45_000)?.let(Thread::sleep)
        } catch (failure: Throwable) {
            val player = controller as? com.nuvio.app.features.player.desktop.NativePlayerController
            println("Trailer live failure: videoId=$videoId snapshot=${player?.snapshot()}")
            if (player != null) {
                val handle = nativeHandle(player)
                if (handle != 0L) listOf("time-pos", "pause", "paused-for-cache", "core-idle", "eof-reached").forEach {
                    println("Trailer failure $it=" + com.nuvio.app.features.player.desktop.NativePlayerBridge.stringProperty(handle, it))
                }
            }
            throw failure
        } finally {
            javax.swing.SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    private fun nativeHandle(player: com.nuvio.app.features.player.desktop.NativePlayerController): Long =
        player.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(player)

    private fun assertChangingPixels(handle: Long) {
        val bridge = com.nuvio.app.features.player.desktop.NativePlayerBridge
        var first = 0L
        awaitNative { first = bridge.videoFrameSignature(handle); first != 0L }
        awaitNative {
            val next = bridge.videoFrameSignature(handle)
            next != 0L && next != first
        }
    }

    private fun awaitNative(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(50)
        check(condition()) { "Native player transition timed out" }
    }
}
