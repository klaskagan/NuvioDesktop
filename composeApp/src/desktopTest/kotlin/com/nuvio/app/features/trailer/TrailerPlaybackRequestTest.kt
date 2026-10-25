package com.nuvio.app.features.trailer

import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrailerPlaybackRequestTest {
    @Test fun dismissedAndSupersededRequestsCannotPublishLateResults() = runTest {
        val old = CompletableDeferred<TrailerPlaybackSource?>()
        val request = TrailerPlaybackRequest(this, { url, _ ->
            if (url == "old") withContext(NonCancellable) { old.await() } else TrailerPlaybackSource(url)
        }, { "Unavailable" })
        request.start("old")
        runCurrent()
        request.start("new")
        runCurrent()
        assertEquals("new", request.source?.videoUrl)
        old.complete(TrailerPlaybackSource("stale"))
        runCurrent()
        assertEquals("new", request.source?.videoUrl)
        request.cancel()
        assertNull(request.source)
        assertFalse(request.loading)
    }

    @Test fun retryStartsFreshAndClearsPreviousErrors() = runTest {
        val refreshes = mutableListOf<Boolean>()
        val request = TrailerPlaybackRequest(this, { _, refresh ->
            refreshes += refresh
            if (refresh) TrailerPlaybackSource("fresh") else null
        }, { "Unavailable" })
        request.start("video")
        runCurrent()
        assertEquals("Unavailable", request.error)
        val firstId = request.id
        request.start("video", forceRefresh = true)
        assertTrue(request.id > firstId)
        assertNull(request.error)
        runCurrent()
        assertEquals(listOf(false, true), refreshes)
        assertEquals("fresh", request.source?.videoUrl)
        assertFalse(request.loading)
    }

    @Test fun stalledResolutionBecomesRetryableError() = runTest {
        val request = TrailerPlaybackRequest(this, { _, _ -> delay(100_000); null }, { "Unavailable" })
        request.start("video")
        advanceTimeBy(35_001)
        runCurrent()
        assertFalse(request.loading)
        assertEquals("Unavailable", request.error)
    }

    @Test fun youtubeBypassesExtractionAndNativeKeepsItsOwnSourceAndRetryPolicy() = runTest {
        var extractions = 0
        val request = TrailerPlaybackRequest(this, { _, _ ->
            extractions++
            TrailerPlaybackSource("https://native.example/video", "https://native.example/audio")
        }, { "Unavailable" })
        val url = "https://youtu.be/R5jcEBVBEbQ"
        request.start(url, backend = TrailerPlayerBackend.YouTube)
        runCurrent()
        assertEquals(0, extractions)
        assertEquals("https://www.youtube.com/watch?v=R5jcEBVBEbQ", request.source?.videoUrl)
        assertEquals(TrailerPlayerBackend.YouTube, request.backend)
        request.start(url, forceRefresh = true, backend = request.backend)
        runCurrent()
        assertEquals(0, extractions)
        request.start(url, backend = TrailerPlayerBackend.Native)
        runCurrent()
        assertEquals(1, extractions)
        assertEquals("https://native.example/audio", request.source?.audioUrl)
        assertEquals(TrailerPlayerBackend.Native, request.backend)
        request.cancel()
    }

    @Test fun changingPresentationPreservesPausedPosition() {
        val state = TrailerPlaybackState()
        state.snapshot = PlayerPlaybackSnapshot(isLoading = false, isPlaying = true, positionMs = 7_500, durationMs = 20_000)
        state.togglePlayback()
        state.snapshot = state.snapshot.copy(isPlaying = false)
        state.changePresentation()
        assertEquals(7_500L, state.snapshot.positionMs)
        assertFalse(state.playWhenReady)
        assertEquals(1, state.presentationId)
    }

    @Test fun startupIsBoundedButPreparedPausedPlaybackIsReady() = runTest {
        val state = TrailerPlaybackState()
        val stalled = async { state.awaitStartup(1_000) }
        advanceTimeBy(1_001)
        assertFalse(stalled.await())
        state.snapshot = PlayerPlaybackSnapshot(isLoading = false, isPlaying = false, durationMs = 30_000)
        assertTrue(state.awaitStartup(1_000))
        state.changePresentation()
        assertFalse(state.awaitStartup(1_000))
    }
}
