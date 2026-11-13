package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerQualityCheckpoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.*

/** Native renderer + controller restoration; never reads the user's profile or titles. */
class NativeQualityRestoreLiveTest {
    @Test fun actualRendererRestoresPausedAndPlayingQualitySwitches() = runBlocking {
        assumeTrue(System.getenv("NUVIO_QUALITY_NATIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val high = File("build/qa/quality/720p.mkv").absoluteFile
        val low = File("build/qa/quality/360p.mkv").absoluteFile
        check(high.isFile && low.isFile)
        lateinit var frame: JFrame
        lateinit var player: NativePlayerController
        SwingUtilities.invokeAndWait {
            val host = NativePlayerHost()
            frame = JFrame("Quality playback fixture").apply {
                add(host); setSize(960, 640); isVisible = true
            }
            player = NativePlayerController(host)
        }
        fun handle() = player.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(player)
        fun get(name: String) = NativePlayerBridge.qualityProperty(handle(), false, name)
        fun set(name: String, value: String) { check(NativePlayerBridge.qualitySet(handle(), false, name, value)) }
        suspend fun load(file: File, position: Long) {
            SwingUtilities.invokeAndWait {
                player.attach(file.toURI().toString(), emptyMap(), false, position, 0, false, { message -> check(message == null) { "Fixture native load failed" } })
            }
            withTimeout(20_000) { while (player.snapshot().isLoading || player.snapshot().videoHeight <= 0) delay(100) }
        }
        suspend fun restore(checkpoint: PlayerQualityCheckpoint) {
            withTimeout(10_000) { while (!player.restoreQuality(checkpoint)) delay(100) }
        }
        try {
            load(high, 4000)
            check(NativePlayerBridge.qualityCommand(handle(), false, arrayOf("seek", "4", "absolute+exact")))
            set("speed", "1.25"); set("volume", "63"); set("mute", "yes"); set("sub-delay", "0.25")
            withTimeout(10_000) { while (abs((get("time-pos").toDoubleOrNull() ?: 0.0) * 1000 - 4000) > 1000) delay(100) }
            val paused = checkNotNull(player.qualityCheckpoint())
            assertTrue(paused.paused)
            assertEquals(720, player.snapshot().videoHeight)
            SwingUtilities.invokeAndWait { player.dispose() }
            load(low, paused.positionMs)
            restore(paused)
            assertFalse(player.snapshot().isPlaying)
            assertEquals(360, player.snapshot().videoHeight)
            assertEquals(1.25f, player.snapshot().playbackSpeed)
            assertEquals(63f, get("volume").toFloat())
            assertEquals("yes", get("mute"))
            assertEquals(.25, get("sub-delay").toDouble())
            assertTrue(player.qualityCheckpoint()!!.tracks.any { it.selected && it.role == "main" && it.language == "eng" })
            assertTrue(NativePlayerBridge.videoFrameSignature(handle()) != 0L)
            val unavailableLanguage = paused.copy(durationMs = 5_000, tracks = paused.tracks.map {
                if (it.selected && it.type == "audio") it.copy(language = "rus", role = null) else it
            })
            assertTrue(player.restoreQuickQuality(unavailableLanguage))
            assertTrue(player.qualityCheckpoint()!!.tracks.any { it.type == "audio" && it.selected })
            player.play()
            withTimeout(5000) { while (!player.snapshot().isPlaying) delay(100) }
            val playing = checkNotNull(player.qualityCheckpoint())
            assertFalse(playing.paused)
            SwingUtilities.invokeAndWait { player.dispose() }
            load(high, playing.positionMs)
            restore(playing)
            if (!playing.paused) player.play()
            withTimeout(5000) { while (!player.snapshot().isPlaying) delay(100) }
            assertEquals(720, player.snapshot().videoHeight)
        } finally {
            SwingUtilities.invokeAndWait { player.dispose(); frame.dispose() }
        }
    }
}
