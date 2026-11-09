package com.nuvio.app.features.player

import androidx.compose.ui.awt.ComposeWindow
import com.nuvio.app.features.player.desktop.*
import com.nuvio.app.features.profiles.ProfileRepository
import java.io.File
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Does not pass useNativeController: the actual production movie caller must own that choice. */
class MoviePlayerControlsLiveTest {
    @Test fun actualMovieScreenKeepsTheNativeControlsViewMountedAndVisible() {
        assumeTrue(System.getenv("NUVIO_PLAYER_LIVE_TEST") == "1")
        check(java.lang.Boolean.getBoolean("nuvio.test.isolatedHome"))
        MoviePlayerControlsFixture.seedMetadata()
        val profileId = ProfileRepository.activeProfileId
        PlayerSettingsStorage.saveNightModeEnabled(profileId, false)
        val file = File("../testFixtures/subtitles/video.mp4").absoluteFile
        check(file.isFile)
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(1100, 750)
                setContent { MoviePlayerControlsFixture.Content(file) }
                isVisible = true
            }
        }
        try {
            val field = DesktopPlayerPictureInPicture.javaClass.getDeclaredField("controller").apply { isAccessible = true }
            var controller: NativePlayerController? = null
            await("actual movie decoding") {
                controller = field.get(DesktopPlayerPictureInPicture) as? NativePlayerController
                controller?.snapshot()?.positionMs?.let { it > 500 } == true
            }
            val player = checkNotNull(controller)
            val handle = player.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(player)
            check(NativePlayerBridge.controlsOverlayAvailable(handle)) {
                "The actual movie screen hid its native controls WebView; decoding alone is not a regression check"
            }
            check(NativePlayerBridge.embeddedBounds(handle).all { it == 0.0 })
            SwingUtilities.invokeAndWait { player.pause() }
            await("pause") { !player.snapshot().isPlaying }
            SwingUtilities.invokeAndWait { player.seekTo(4_000) }
            await("seek while paused") { kotlin.math.abs(player.snapshot().positionMs - 4_000) < 1_000 }
            check(NativePlayerBridge.controlsOverlayAvailable(handle))
            SwingUtilities.invokeAndWait { player.play() }
            await("resume") { player.snapshot().isPlaying }
            SwingUtilities.invokeAndWait { player.pause() }
            await("decoded audio") {
                NativePlayerBridge.qualityProperty(handle, false, "audio-out-params/channel-count").isNotBlank()
            }
            val event = player.javaClass.getDeclaredMethod("handlePlayerEvent", String::class.java, Double::class.javaPrimitiveType)
                .apply { isAccessible = true }
            fun property(name: String) = NativePlayerBridge.qualityProperty(handle, false, name)
            val originalFilters = property("af")
            val originalVolume = property("volume")
            val originalMute = property("mute")
            SwingUtilities.invokeAndWait { event.invoke(player, "nightMode", 0.0) }
            check(desktopNightModeInChain(property("af")))
            check(PlayerSettingsStorage.loadNightModeEnabled(profileId))
            check(!PlayerSettingsStorage.loadNightModeEnabled(profileId + 1))
            SwingUtilities.invokeAndWait { event.invoke(player, "nightMode", 0.0) }
            check(property("af") == originalFilters)
            check(property("volume") == originalVolume && property("mute") == originalMute)
            check(!PlayerSettingsStorage.loadNightModeEnabled(profileId))
            println("Actual movie screen: native controls attached/visible through pause, seek and resume")
        } finally { SwingUtilities.invokeAndWait { window.dispose() } }
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(50) }
        error("Timed out: $label")
    }
}
