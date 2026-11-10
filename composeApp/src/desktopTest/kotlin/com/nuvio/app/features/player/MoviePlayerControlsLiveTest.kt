package com.nuvio.app.features.player

import androidx.compose.ui.awt.ComposeWindow
import com.nuvio.app.features.player.desktop.*
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.streams.StreamsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
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
    @Test fun actualMovieScreenShowsRewindCuesUntilTheOriginalPosition() {
        val file = System.getenv("NUVIO_REWIND_TEST_MEDIA")?.let(::File)
        assumeTrue(file?.isFile == true)
        val fixtureDirectory = checkNotNull(file).parentFile
        val addonFile = fixtureDirectory.resolve("cue.srt")
        val withoutSubtitles = fixtureDirectory.resolve("no-subtitles.mkv")
        check(addonFile.isFile && withoutSubtitles.isFile)
        val addonServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/cue.srt") { request ->
                val bytes = addonFile.readBytes()
                request.sendResponseHeaders(200, bytes.size.toLong())
                request.responseBody.use { it.write(bytes) }
            }
            start()
        }
        check(java.lang.Boolean.getBoolean("nuvio.test.isolatedHome"))
        MoviePlayerControlsFixture.seedMetadata()
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            PlayerSettingsRepository.setAutoShowSubtitlesOnRewindEnabled(true)
            window = ComposeWindow().apply {
                setSize(1100, 750)
                setContent { MoviePlayerControlsFixture.Content(file) }
                isVisible = true
            }
        }
        try {
            val field = DesktopPlayerPictureInPicture.javaClass.getDeclaredField("controller").apply { isAccessible = true }
            var controller: NativePlayerController? = null
            await("embedded subtitle track") {
                controller = field.get(DesktopPlayerPictureInPicture) as? NativePlayerController
                controller?.getSubtitleTracks()?.any { !it.isExternal } == true
            }
            val player = checkNotNull(controller)
            val handle = player.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(player)
            val event = player.javaClass.getDeclaredMethod("handlePlayerEvent", String::class.java, Double::class.javaPrimitiveType)
                .apply { isAccessible = true }
            fun dispatch(type: String, value: Double = 0.0) {
                SwingUtilities.invokeAndWait { event.invoke(player, type, value) }
            }
            fun seekTo(positionMs: Long) {
                dispatch("scrubFinish", positionMs.toDouble())
                await("position near $positionMs") { kotlin.math.abs(player.snapshot().positionMs - positionMs) < 500 }
                Thread.sleep(350)
            }
            fun subtitlesOn() = player.getSubtitleTracks().any { it.isSelected }
            SwingUtilities.invokeAndWait { player.pause() }
            await("paused playback") { !player.snapshot().isPlaying }
            dispatch("selectBuiltInSubtitleTrack", -1.0)
            seekTo(35_000)
            dispatch("keyboardSeekBack")
            await("first rewind with embedded track selected") {
                player.snapshot().positionMs in 24_500L..25_500L && subtitlesOn()
            }
            SwingUtilities.invokeAndWait { player.play() }
            await("subtitle cue during replay") {
                NativePlayerBridge.qualityProperty(handle, false, "sub-text").contains("Rewind subtitle fixture")
            }
            SwingUtilities.invokeAndWait { player.pause() }
            await("paused replay") { !player.snapshot().isPlaying }
            seekTo(25_000)
            dispatch("seekBack")
            await("second rewind with subtitles") { player.snapshot().positionMs in 14_500L..15_500L && subtitlesOn() }
            Thread.sleep(800)
            check(subtitlesOn()) { "Pausing during replay must keep the temporary subtitles" }
            seekTo(34_000)
            check(subtitlesOn()) { "The second rewind must keep the original replay endpoint" }
            SwingUtilities.invokeAndWait { player.play() }
            await("subtitles off after natural playback catches up") {
                player.snapshot().positionMs >= 35_000 && !subtitlesOn()
            }
            SwingUtilities.invokeAndWait { player.pause() }
            seekTo(40_000)
            dispatch("scrubFinish", 20_000.0)
            await("backward timeline seek with subtitles") { player.snapshot().positionMs in 19_500L..20_500L && subtitlesOn() }
            dispatch("scrubFinish", 45_000.0)
            await("forward timeline seek clears subtitles") { player.snapshot().positionMs >= 44_500 && !subtitlesOn() }

            dispatch("keyboardSeekBack")
            await("temporary track for manual override") { subtitlesOn() }
            val index = player.getSubtitleTracks().first { it.isSelected }.index
            dispatch("selectBuiltInSubtitleTrack", index.toDouble())
            seekTo(46_000)
            check(subtitlesOn()) { "Explicit subtitle selection must survive the replay endpoint" }
            dispatch("selectBuiltInSubtitleTrack", -1.0)
            SwingUtilities.invokeAndWait { PlayerSettingsRepository.setAutoShowSubtitlesOnRewindEnabled(false) }
            Thread.sleep(350)
            dispatch("keyboardSeekBack")
            await("rewind with the setting disabled") { player.snapshot().positionMs in 35_500L..36_500L }
            check(!subtitlesOn())

            SwingUtilities.invokeAndWait { PlayerSettingsRepository.setAutoShowSubtitlesOnRewindEnabled(true) }
            val addonField = SubtitleRepository.javaClass.getDeclaredField("_addonSubtitles").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val addons = addonField.get(SubtitleRepository) as MutableStateFlow<List<AddonSubtitle>>
            val addonUrl = "http://127.0.0.1:${addonServer.address.port}/cue.srt"
            addons.value = listOf(AddonSubtitle("fixture", addonUrl, "en", "Fixture add-on"))
            Thread.sleep(350)
            dispatch("selectAddonSubtitle")
            try {
                await("add-on track selection") { player.getSubtitleTracks().any { it.isExternal && it.isSelected } }
            } catch (failure: IllegalStateException) {
                throw IllegalStateException("Add-on selection left tracks ${player.getSubtitleTracks()}", failure)
            }
            dispatch("keyboardSeekBack")
            await("rewind with the add-on track selected") {
                player.snapshot().positionMs in 25_500L..26_500L &&
                    player.getSubtitleTracks().any { it.isExternal && it.isSelected }
            }
            seekTo(37_000)
            check(player.getSubtitleTracks().any { it.isExternal && it.isSelected })

            dispatch("selectBuiltInSubtitleTrack", -1.0)
            await("add-on track off") { !subtitlesOn() }
            dispatch("keyboardSeekBack")
            await("temporary subtitle before source replacement") { subtitlesOn() }
            val source = StreamItem(name = "No subtitles", url = withoutSubtitles.toURI().toString(), addonName = "Fixture", addonId = "fixture")
            val request = StreamsRepository.requestToken("movie", "movie-controls-fixture")
            check(PlayerStreamsRepository.primeSourcesFromSelection(
                "movie", "movie-controls-fixture", null, null,
                StreamsUiState(requestToken = request, groups = listOf(AddonStreamGroup("Fixture", "fixture", listOf(source)))),
            ))
            Thread.sleep(350)
            dispatch("selectSource")
            await("replacement source with no subtitle tracks") {
                player.snapshot().videoHeight == 136 && player.getSubtitleTracks().isEmpty()
            }
            SwingUtilities.invokeAndWait { player.pause() }
            await("paused replacement source") { !player.snapshot().isPlaying }
            seekTo(35_000)
            dispatch("keyboardSeekBack")
            await("rewind without subtitle tracks") {
                player.snapshot().positionMs in 24_500L..25_500L && player.getSubtitleTracks().isEmpty()
            }
        } finally {
            addonServer.stop(0)
            SwingUtilities.invokeAndWait {
                window.dispose()
                PlayerSettingsRepository.setAutoShowSubtitlesOnRewindEnabled(true)
                SubtitleRepository.clear()
                PlayerStreamsRepository.clearAll()
            }
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(50) }
        error("Timed out: $label")
    }
}
