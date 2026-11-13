package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import kotlin.test.*

class PlayerRuntimeQualityTest {
    private fun runtime() = PlayerScreenRuntime(PlayerScreenArgs(
        profileId = 1, title = "Quality fixture", sourceUrl = "fixture:old", sourceAudioUrl = null,
        sourceHeaders = mapOf("X-Fixture" to "original"), sourceResponseHeaders = emptyMap(), streamType = null,
        providerName = "Fixture", streamTitle = "720p", streamSubtitle = null, initialBingeGroup = null,
        pauseDescription = null, onBack = {}, onOpenInExternalPlayer = null, onOpenExternalUrl = null,
        modifier = Modifier, logo = null, poster = null, background = null, seasonNumber = null, episodeNumber = null,
        episodeTitle = null, episodeThumbnail = null, contentType = "movie", videoId = "quality-fixture",
        parentMetaId = "quality-fixture", parentMetaType = "movie", providerAddonId = null,
        torrentInfoHash = null, torrentFileIdx = null, torrentFilename = null, torrentTrackers = emptyList(),
        initialPositionMs = 4000, initialProgressFraction = null,
    ))

    private open class Controller(val runtime: PlayerScreenRuntime, var paused: Boolean, val failNew: Boolean) : PlayerEngineController {
        var restored: PlayerQualityCheckpoint? = null
        override fun qualityCheckpoint() = PlayerQualityCheckpoint(4000, paused, 1.25f, 63f, true, 250, emptyList(), 20_000)
        override suspend fun restoreQuality(checkpoint: PlayerQualityCheckpoint): Boolean {
            if (failNew && runtime.activeSourceUrl == "fixture:new") error("fixture failure")
            restored = checkpoint
            return true
        }
        override fun play() {} ; override fun pause() {} ; override fun seekTo(positionMs: Long) {}
        override fun seekBy(offsetMs: Long) {} ; override fun retry() {} ; override fun setPlaybackSpeed(speed: Float) {}
        override fun getAudioTracks() = emptyList<AudioTrack>()
        override fun getSubtitleTracks() = emptyList<SubtitleTrack>()
        override fun applyAudioLanguagePreferences(languages: List<String>) {}
        override fun selectAudioTrack(index: Int) {} ; override fun selectSubtitleTrack(index: Int) {}
        override fun setSubtitleUri(url: String) {} ; override fun clearExternalSubtitle() {}
        override fun clearExternalSubtitleAndSelect(trackIndex: Int) {}
    }

    private fun exercise(paused: Boolean, fail: Boolean) = runBlocking {
        val runtime = runtime().apply { scope = this@runBlocking }
        val controller = Controller(runtime, paused, fail)
        runtime.playerController = controller
        runtime.qualityChoices = listOf(PlayerQualityChoice(0, 360, "fixture:new", mapOf("X-Fixture" to "new")))
        val loader = launch {
            while (isActive) {
                runtime.playerControllerSourceUrl = runtime.activeSourceUrl
                runtime.playbackSnapshot = PlayerPlaybackSnapshot(isLoading = false, durationMs = 20_000, positionMs = 4000)
                runtime.resetIdentityStateIfNeeded()
                delay(10)
            }
        }
        try {
            runtime.selectPlayerQuality(360)
            withTimeout(3000) { runtime.qualityJob!!.join() }
            assertEquals(if (fail) "fixture:old" else "fixture:new", runtime.activeSourceUrl)
            assertEquals(!paused, runtime.shouldPlay)
            assertFalse(runtime.qualityBusy)
            assertFalse(runtime.qualitySwitching)
            assertEquals(4000L, controller.restored?.positionMs)
            assertEquals(1.25f, controller.restored?.speed)
            assertEquals(63f, controller.restored?.volume)
            assertEquals(250, controller.restored?.subtitleDelayMs)
            assertEquals(true, controller.restored?.muted)
            if (fail) assertEquals("original", runtime.activeSourceHeaders["X-Fixture"])
            else {
                assertEquals(360, runtime.qualityRequestedHeight)
                assertEquals("fixture:new", runtime.playbackSession.lastSourceUrl)
            }
        } finally { loader.cancelAndJoin(); runtime.cancelPlayerQuality() }
    }
    @Test fun repeatedMenuOpeningUsesCachedSourcesSynchronouslyWithoutAPlayer() {
        val runtime = runtime()
        val sources = listOf("2160p", "2160p", "1080p", "1080p", "720p").mapIndexed { index, quality ->
            com.nuvio.app.features.streams.StreamItem(name = quality, url = "https://example.test/$index", addonId = "comet", addonName = "Comet")
        }
        runtime.sourceStreamsState = com.nuvio.app.features.streams.StreamsUiState(
            requestToken = "movie::quality-fixture::null::null::pluginsGrouped=false",
            groups = listOf(com.nuvio.app.features.streams.AddonStreamGroup("comet", "Comet", streams = sources)),
        )
        runtime.openPlayerQualityMenu()
        val cached = runtime.qualityChoices
        repeat(25) { runtime.openPlayerQualityMenu() }
        assertSame(cached, runtime.qualityChoices)
        assertEquals(listOf(2160, 1080, 720), runtime.qualityChoices.map { it.height })
        assertNull(runtime.qualityJob)
        assertFalse(runtime.qualityBusy)
        assertEquals(5, runtime.sourceStreamsState.groups.single().streams.size)
    }
    @Test fun previousTitleSourcesCannotPopulateTheNewTitlesQualityMenu() {
        val runtime = runtime()
        runtime.sourceStreamsState = com.nuvio.app.features.streams.StreamsUiState(
            requestToken = "movie::different-title::null::null::pluginsGrouped=false",
            groups = listOf(com.nuvio.app.features.streams.AddonStreamGroup("comet", "Comet", streams = listOf(
                com.nuvio.app.features.streams.StreamItem(name = "2160p", url = "https://example.test/wrong-title", addonId = "comet", addonName = "Comet")))),
        )
        runtime.discoverPlayerQuality()
        assertTrue(runtime.qualityChoices.isEmpty())
    }
    @Test fun currentSourceKeepsPlayingUntilBackgroundCandidateIsReady() = runBlocking {
        val runtime = runtime().apply { scope = this@runBlocking; shouldPlay = true }
        val started = CompletableDeferred<Unit>()
        val allow = CompletableDeferred<Unit>()
        var promoted = false
        val controller = object : Controller(runtime, false, false) {
            override suspend fun prepareBufferedQuality(choice: PlayerQualityChoice, checkpoint: PlayerQualityCheckpoint): BufferedPlayerQuality {
                started.complete(Unit)
                allow.await()
                return object : BufferedPlayerQuality {
                    override fun promote(): PlayerQualityCheckpoint { promoted = true; return qualityCheckpoint().copy(positionMs = 5200) }
                    override suspend fun cancel() {}
                }
            }
        }
        runtime.playerController = controller
        runtime.qualityChoices = listOf(PlayerQualityChoice(0, 360, "fixture:new", emptyMap()))
        val loader = launch {
            while (isActive) {
                runtime.playerControllerSourceUrl = runtime.activeSourceUrl
                runtime.updatePlaybackSnapshot(PlayerPlaybackSnapshot(isLoading = false, durationMs = 20_000, positionMs = 5200))
                runtime.resetIdentityStateIfNeeded()
                delay(10)
            }
        }
        try {
            runtime.selectPlayerQuality(360)
            started.await()
            assertEquals("fixture:old", runtime.activeSourceUrl)
            assertTrue(runtime.shouldPlay)
            assertFalse(runtime.qualityReplacing)
            assertFalse(promoted)
            // A pause during loading must be reflected in the new stream, rather than the click-time state.
            controller.paused = true
            runtime.shouldPlay = false
            allow.complete(Unit)
            withTimeout(3000) { runtime.qualityJob!!.join() }
            assertTrue(promoted)
            assertEquals("fixture:new", runtime.activeSourceUrl)
            assertEquals(5200L, runtime.activeInitialPositionMs)
            assertFalse(runtime.shouldPlay)
        } finally { loader.cancelAndJoin(); runtime.cancelPlayerQuality() }
    }
    @Test fun cancellingBackgroundPreparationDoesNotReplaceOrPauseTheOldSource() = runBlocking {
        val runtime = runtime().apply { scope = this@runBlocking; shouldPlay = true }
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val controller = object : Controller(runtime, false, false) {
            override suspend fun prepareBufferedQuality(choice: PlayerQualityChoice, checkpoint: PlayerQualityCheckpoint): BufferedPlayerQuality {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        runtime.playerController = controller
        runtime.qualityChoices = listOf(PlayerQualityChoice(0, 360, "fixture:new", emptyMap()))
        runtime.selectPlayerQuality(360)
        started.await()
        val pending = runtime.qualityJob!!
        runtime.cancelPlayerQuality()
        withTimeout(3000) { pending.join() }
        assertTrue(cancelled)
        assertEquals("fixture:old", runtime.activeSourceUrl)
        assertTrue(runtime.shouldPlay)
        assertFalse(runtime.qualityReplacing)
        assertNull(controller.restored)
    }
    @Test fun aPreparedCandidateCannotPromoteAfterTheTitleChanges() = runBlocking {
        val runtime = runtime().apply { scope = this@runBlocking }
        val started = CompletableDeferred<Unit>()
        val allow = CompletableDeferred<Unit>()
        var promoted = false
        var released = false
        val controller = object : Controller(runtime, false, false) {
            override suspend fun prepareBufferedQuality(choice: PlayerQualityChoice, checkpoint: PlayerQualityCheckpoint): BufferedPlayerQuality {
                started.complete(Unit); allow.await()
                return object : BufferedPlayerQuality {
                    override fun promote(): PlayerQualityCheckpoint { promoted = true; return checkpoint }
                    override suspend fun cancel() { released = true }
                }
            }
        }
        runtime.playerController = controller
        runtime.qualityChoices = listOf(PlayerQualityChoice(0, 360, "fixture:new", emptyMap()))
        runtime.selectPlayerQuality(360)
        started.await()
        runtime.activeVideoId = "different-title"
        allow.complete(Unit)
        withTimeout(3000) { runtime.qualityJob!!.join() }
        assertFalse(promoted)
        assertTrue(released)
        assertEquals("fixture:old", runtime.activeSourceUrl)
        runtime.cancelPlayerQuality()
    }
    @Test fun pausedSelectionRemainsPausedThroughIdentityReset() = exercise(paused = true, fail = false)
    @Test fun playingSelectionRestoresPlaybackState() = exercise(paused = false, fail = false)
    @Test fun failedSelectionRestoresSourceHeadersAndCheckpoint() = exercise(paused = true, fail = true)
}
