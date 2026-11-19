package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Arrow keys and gamepad skips go through [handlePlayerControllerCommand]; one press is one 10 s skip. */
class PlayerControllerSeekTest {
    private class RecordingController : PlayerEngineController {
        val seeks = mutableListOf<String>()
        override fun play() {} ; override fun pause() {}
        override fun seekTo(positionMs: Long) { seeks += "to:$positionMs" }
        override fun seekBy(offsetMs: Long) { seeks += "by:$offsetMs" }
        override fun retry() {} ; override fun setPlaybackSpeed(speed: Float) {}
        override fun getAudioTracks() = emptyList<AudioTrack>()
        override fun getSubtitleTracks() = emptyList<SubtitleTrack>()
        override fun applyAudioLanguagePreferences(languages: List<String>) {}
        override fun selectAudioTrack(index: Int) {} ; override fun selectSubtitleTrack(index: Int) {}
        override fun setSubtitleUri(url: String) {} ; override fun clearExternalSubtitle() {}
        override fun clearExternalSubtitleAndSelect(trackIndex: Int) {}
    }

    private fun runtime() = PlayerScreenRuntime(PlayerScreenArgs(
        profileId = 1, title = "Seek fixture", sourceUrl = "fixture:seek", sourceAudioUrl = null,
        sourceHeaders = emptyMap(), sourceResponseHeaders = emptyMap(), streamType = null,
        providerName = "Fixture", streamTitle = "720p", streamSubtitle = null, initialBingeGroup = null,
        pauseDescription = null, onBack = {}, onOpenInExternalPlayer = null, onOpenExternalUrl = null,
        modifier = Modifier, logo = null, poster = null, background = null, seasonNumber = null, episodeNumber = null,
        episodeTitle = null, episodeThumbnail = null, contentType = "movie", videoId = "seek-fixture",
        parentMetaId = "seek-fixture", parentMetaType = "movie", providerAddonId = null,
        torrentInfoHash = null, torrentFileIdx = null, torrentFilename = null, torrentTrackers = emptyList(),
        initialPositionMs = 0, initialProgressFraction = null,
    ))

    @Test
    fun `one skip command seeks ten seconds once`() = runBlocking {
        for ((command, expected) in listOf(PlayerCommand.SeekForward to 110_000L, PlayerCommand.SeekBack to 90_000L)) {
            val controller = RecordingController()
            val runtime = runtime().apply {
                scope = this@runBlocking
                playerController = controller
                playbackSnapshot = PlayerPlaybackSnapshot(isLoading = false, durationMs = 600_000, positionMs = 100_000)
            }
            runtime.handlePlayerControllerCommand(command)
            assertEquals(listOf("to:$expected"), controller.seeks, "$command")
        }
    }

    @Test
    fun `key dispatcher leaves shortcuts to a player whose controls page handles them`() {
        var handles = true
        PlayerControllerBridge.register(null, { true }, { handles })
        try {
            assertEquals(true, PlayerControllerBridge.keyboardShortcutsHandledByPlayer())
            handles = false
            assertEquals(false, PlayerControllerBridge.keyboardShortcutsHandledByPlayer())
            PlayerControllerBridge.register(null, { true })
            assertEquals(false, PlayerControllerBridge.keyboardShortcutsHandledByPlayer())
        } finally {
            PlayerControllerBridge.register(null, null)
        }
    }
}
