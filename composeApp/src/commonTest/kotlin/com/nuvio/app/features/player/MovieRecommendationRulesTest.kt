package com.nuvio.app.features.player

import com.nuvio.app.features.trakt.RelatedMoviePage
import com.nuvio.app.features.trakt.collectRelatedMoviePages
import kotlinx.coroutines.test.runTest
import androidx.compose.ui.Modifier
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedUiState
import com.nuvio.app.features.watched.watchedItemKeys
import kotlin.test.*

class MovieRecommendationRulesTest {
    @Test fun endingBoundariesAndUnknownDuration() {
        val duration = 600_000L
        for ((remaining, expected) in listOf(240_001L to 0, 240_000L to 1, 30_001L to 1, 30_000L to 2, 0L to 2)) {
            assertEquals(expected, MovieRecommendationRules.stage(duration - remaining, duration, false))
        }
        assertEquals(0, MovieRecommendationRules.stage(0, 0, false))
        assertEquals(0, MovieRecommendationRules.stage(100, -1, false))
        assertEquals(3, MovieRecommendationRules.stage(0, 0, true))
    }

    @Test fun candidatesExcludeWatchedAndCurrentPreservingOrderAndLimit() {
        val items = (0..25).map { MetaPreview("tt$it", "movie", "Title $it") }
        val watched = watchedItemKeys("movie", "tt1").toSet()
        assertEquals((2..21).map { "tt$it" }, MovieRecommendationRules.candidates(items, "tt0", watched).map { it.id })
        assertTrue(MovieRecommendationRules.candidates(items.take(1), "tt0", emptySet()).isEmpty())
        assertEquals(1, MovieRecommendationRules.candidates(listOf(items[1], items[1]), "tt0", emptySet()).size)
    }

    @Test fun readsAllReportedTraktPagesBeforeSelectingUnwatched() = runTest {
        val items = (1..120).map { MetaPreview("tt$it", "movie", "Title $it") }
        val watched = (1..16).flatMap { watchedItemKeys("movie", "tt$it") }.toSet()
        val requested = mutableListOf<Int>()
        val result = collectRelatedMoviePages { page ->
            requested += page
            RelatedMoviePage(items.drop((page - 1) * 20).take(20), 6)
        }
        assertEquals((1..6).toList(), requested)
        assertEquals((17..36).map { "tt$it" }, MovieRecommendationRules.candidates(result, "current", watched).map { it.id })
    }

    @Test fun repeatedPagesAndFailuresKeepEarlierResultsAndWatchedAliasesAreExcluded() = runTest {
        val related = (1..20).map { MetaPreview("tt$it", "movie", "Title $it", alternateIds = setOf("tmdb:$it")) }
        val watched = (1..20).flatMap { watchedItemKeys("movie", "tmdb:$it") }.toSet()
        val additional = listOf(1, 21, 22).map { MetaPreview("tmdb:$it", "movie", "Discovery $it") }
        for (repeatPage in listOf(false, true)) {
            val requested = mutableListOf<Int>()
            val results = collectRelatedMoviePages { page ->
                requested += page
                if (page == 1 || repeatPage) RelatedMoviePage(related, 5) else null
            }
            assertEquals(listOf(1, 2), requested)
            val selected = MovieRecommendationRules.candidates(results + additional, "tt100", watched, setOf("tmdb:22"))
            assertEquals(listOf("tmdb:21"), selected.map { it.id })
        }
    }

    @Test fun tmdbPagesFillQuotaAfterTraktAndExcludeWatchedAndDuplicateIds() = runTest {
        val trakt = (1..8).map {
            MetaPreview("tt$it", "movie", "Trakt $it", alternateIds = setOf("tmdb:$it"))
        }
        val watched = (9..20).flatMap { watchedItemKeys("movie", "tmdb:$it") }.toSet()
        fun selected(items: List<MetaPreview>) =
            MovieRecommendationRules.candidates(items, "tmdb:21", watched)
        val requested = mutableListOf<Int>()
        val combined = MovieRecommendationRules.appendFallbackPages(trakt, { selected(it).size == 20 }) { page ->
            requested += page
            ((page - 1) * 20 + 1..page * 20).map {
                MetaPreview("tmdb:$it", "movie", "TMDB $it")
            } to 23
        }
        assertEquals(listOf(1, 2), requested)
        assertEquals(trakt.map { it.id } + (22..33).map { "tmdb:$it" }, selected(combined).map { it.id })
        assertEquals(trakt, MovieRecommendationRules.appendFallbackPages(trakt, { false }) { null })
    }

    @Test fun runtimeUsesCurrentMetadataSettingNavigationAndSnapshot() {
        val runtime = runtime()
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.playerSettingsUiState = runtime.playerSettingsUiState.copy(movieRecommendationsEnabled = false)
        assertTrue(runtime.movieRecommendationCandidates.isEmpty())
        runtime.playerSettingsUiState = runtime.playerSettingsUiState.copy(movieRecommendationsEnabled = true)
        runtime.args = runtime.args.copy(onOpenMetaDetails = null)
        assertTrue(runtime.movieRecommendationCandidates.isEmpty())
        runtime.args = runtime.args.copy(onOpenMetaDetails = {})
        runtime.playerMeta = runtime.playerMeta!!.copy(id = "old-movie")
        assertTrue(runtime.movieRecommendationCandidates.isEmpty())
        runtime.playerMeta = null
        assertEquals(0, runtime.movieRecommendationStage)
        runtime.playerMeta = MetaDetails("tt1234567", "movie", "Current", moreLikeThis = listOf(MetaPreview("tt2", "movie", "Next")))
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.watchedUiState = WatchedUiState(watchedKeys = watchedItemKeys("movie", "tt2").toSet())
        assertEquals(0, runtime.movieRecommendationStage)
        runtime.watchedUiState = WatchedUiState()
        runtime.activeSourceUrl = "https://example.com/new.mp4"
        assertEquals(0, runtime.movieRecommendationStage)
        runtime.args = runtime.args.copy(parentMetaType = "series", contentType = "series")
        assertTrue(runtime.movieRecommendationCandidates.isEmpty())
    }

    @Test fun dismissAndOpenRejectEventsFromPreviousPlayback() {
        val runtime = runtime()
        val session = runtime.movieRecommendationSessionKey
        runtime.handlePlayerControlsEvent("dismissMovieRecommendations:$session", 0.0)
        assertTrue(runtime.movieRecommendationsDismissed)
        runtime.playbackSnapshot = runtime.playbackSnapshot.copy(positionMs = 580_000L)
        assertEquals(2, runtime.movieRecommendationStage)
        runtime.syncMovieRecommendationDismissal()
        assertTrue(runtime.movieRecommendationsDismissed)
        runtime.args = runtime.args.copy(launchId = 2)
        runtime.movieRecommendationsDismissed = false
        runtime.handlePlayerControlsEvent("dismissMovieRecommendations:$session", 0.0)
        assertFalse(runtime.movieRecommendationsDismissed)
        var backedOut = false
        runtime.args = runtime.args.copy(onBack = { backedOut = true })
        runtime.handlePlayerControlsEvent("openMovieRecommendation:$session:movie:tt2", 0.0)
        assertFalse(backedOut)
    }

    @Test fun seekingBackAndNewPlaybackResetDismissal() {
        val runtime = runtime()
        runtime.handlePlayerControlsEvent("dismissMovieRecommendations:${runtime.movieRecommendationSessionKey}", 0.0)
        runtime.playbackSnapshot = runtime.playbackSnapshot.copy(positionMs = 300_000L)
        runtime.syncMovieRecommendationDismissal()
        assertFalse(runtime.movieRecommendationsDismissed)
        runtime.playbackSnapshot = runtime.playbackSnapshot.copy(positionMs = 400_000L)
        runtime.handlePlayerControlsEvent("dismissMovieRecommendations:${runtime.movieRecommendationSessionKey}", 0.0)
        assertTrue(runtime.movieRecommendationsDismissed)
        runtime.activeSourceUrl = "https://example.com/new-source.mp4"
        runtime.syncMovieRecommendationDismissal()
        assertFalse(runtime.movieRecommendationsDismissed)
    }

    @Test fun introDbStartsAtCreditsEvenWhenEarlierOrLaterThanFourMinutes() {
        val mode = MovieRecommendationTiming.INTRODB
        assertEquals(0, MovieRecommendationRules.stage(349_999, 600_000, false, mode, 350_000))
        assertEquals(0, MovieRecommendationRules.stage(354_999, 600_000, false, mode, 350_000))
        assertEquals(1, MovieRecommendationRules.stage(355_000, 600_000, false, mode, 350_000))
        assertEquals(0, MovieRecommendationRules.stage(500_000, 600_000, false, mode, 550_000))
        assertEquals(1, MovieRecommendationRules.stage(555_000, 600_000, false, mode, 550_000))
        assertEquals(2, MovieRecommendationRules.stage(570_000, 600_000, false, mode, 550_000))
        assertEquals(3, MovieRecommendationRules.stage(0, 0, true, mode, null))
    }

    @Test fun explicitFourMinutesAndMissingOrInvalidApiDataKeepOriginalBehavior() {
        assertEquals(1, MovieRecommendationRules.stage(360_000, 600_000, false, MovieRecommendationTiming.FINAL_FOUR_MINUTES, 550_000))
        for (start in listOf(null, -1L, 600_000L, 700_000L)) {
            assertEquals(1, MovieRecommendationRules.stage(360_000, 600_000, false, MovieRecommendationTiming.INTRODB, start))
        }
    }

    @Test fun apiLookupDoesNotFlashFourMinuteCardWhilePending() {
        val runtime = runtime()
        runtime.movieRecommendationCreditsLoaded = false
        assertEquals(0, runtime.movieRecommendationStage)
        runtime.playerSettingsUiState = runtime.playerSettingsUiState.copy(movieRecommendationTiming = MovieRecommendationTiming.FINAL_FOUR_MINUTES)
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.playerSettingsUiState = runtime.playerSettingsUiState.copy(movieRecommendationTiming = MovieRecommendationTiming.INTRODB)
        runtime.movieRecommendationCreditsLoaded = true
        runtime.movieRecommendationCreditsStartMs = 550_000
        assertEquals(0, runtime.movieRecommendationStage)
        runtime.playbackSnapshot = runtime.playbackSnapshot.copy(positionMs = 555_000)
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.args = runtime.args.copy(launchId = 5)
        assertEquals(0, runtime.movieRecommendationStage)
    }

    @Test fun durationAndTimingChangesCannotReuseEarlierLookupReadiness() {
        val runtime = runtime()
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.playbackSnapshot = runtime.playbackSnapshot.copy(durationMs = 601_000)
        assertEquals(0, runtime.movieRecommendationStage)
        runtime.movieRecommendationCreditsRequest = runtime.movieRecommendationLookupKey
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.playerSettingsUiState = runtime.playerSettingsUiState.copy(movieRecommendationTiming = MovieRecommendationTiming.FINAL_FOUR_MINUTES)
        runtime.movieRecommendationCreditsRequest = runtime.movieRecommendationLookupKey
        assertEquals(1, runtime.movieRecommendationStage)
        runtime.playerSettingsUiState = runtime.playerSettingsUiState.copy(movieRecommendationTiming = MovieRecommendationTiming.INTRODB)
        assertEquals(0, runtime.movieRecommendationStage)
    }

    private fun runtime(): PlayerScreenRuntime = PlayerScreenRuntime(args().copy(onOpenMetaDetails = {})).apply {
        playerMeta = MetaDetails("tt1234567", "movie", "Current", moreLikeThis = listOf(MetaPreview("tt2", "movie", "Next")))
        playbackSnapshot = PlayerPlaybackSnapshot(positionMs = 400_000L, durationMs = 600_000L)
        playbackSnapshotKey = activePlaybackKey
        movieRecommendationCreditsRequest = movieRecommendationLookupKey
        movieRecommendationCreditsLoaded = true
    }
    private fun args() = PlayerScreenArgs(
        profileId = 1,
        title = "Title",
        sourceUrl = "https://example.com/video.mp4",
        sourceAudioUrl = null,
        sourceHeaders = emptyMap(),
        sourceResponseHeaders = emptyMap(),
        streamType = null,
        providerName = "Provider",
        streamTitle = "Source",
        streamSubtitle = null,
        initialBingeGroup = null,
        pauseDescription = null,
        onBack = {},
        onOpenInExternalPlayer = null,
        onOpenExternalUrl = null,
        modifier = Modifier,
        logo = null,
        poster = null,
        background = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeTitle = null,
        episodeThumbnail = null,
        contentType = "movie",
        videoId = "tt1234567",
        parentMetaId = "tt1234567",
        parentMetaType = "movie",
        providerAddonId = null,
        torrentInfoHash = null,
        torrentFileIdx = null,
        torrentFilename = null,
        torrentTrackers = emptyList(),
        initialPositionMs = 0L,
        initialProgressFraction = null,
    )
}
