package com.nuvio.app.features.trakt

import com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.*
import kotlin.test.*

class TraktCalendarTest {
    private val show = CalendarMedia("ttshow", "series", "Series")
    private fun episode(n: Int, date: String = "2026-10-24T20:00:00Z", season: Int = 1) =
        CalendarEpisode("episode-$season-$n-$date", show, season, n, null, date, parseZonedIsoDateTimeToEpochMs(date)!!)
    private fun utc(ms: Long) = kotlin.time.Instant.fromEpochMilliseconds(ms).toString().take(10)

    @Test fun groupsSameDayWithoutCollapsingOtherDatesOrSeasons() {
        val cards = buildCalendarCards(listOf(episode(7), episode(5), episode(6),
            episode(8, "2026-10-25T20:00:00Z"), episode(1, season = 2)), emptyList(), "2026-10-24", localDate = ::utc)
        assertEquals(3, cards.size)
        val grouped = cards.single { it.episodes.size == 3 }
        assertEquals("S1 · E5–7", grouped.episodeLabel)
        assertEquals(listOf(5, 6, 7), grouped.episodes.map { it.number })
        assertEquals("S1 · E8", cards.single { it.localDate == "2026-10-25" }.episodeLabel)
        assertEquals(3, cards.map { it.toPreview().stableKey() }.toSet().size)
    }

    @Test fun mixedChronologyPreservesDateOnlyMovieAndWindowBoundaries() {
        val film = CalendarMovie(CalendarMedia("ttmovie", "movie", "Film"), "2026-10-24")
        val cards = buildCalendarCards(listOf(episode(5), episode(6,"2026-11-06T23:00:00Z"),
            episode(7,"2026-11-07T00:00:00Z"), episode(4,"2026-10-23T23:00:00Z")), listOf(film), "2026-10-24", localDate = ::utc)
        assertEquals(3, cards.size)
        assertEquals("ttmovie", cards.first().media.id)
        assertNull(cards.first().releaseEpochMs)
        assertEquals(13, cards.last().daysAway)
        assertEquals("2026-10-23" to 16, calendarUtcRequestWindow("2026-10-24", 14))
    }

    @Test fun localDateDeterminesGroupingAndTodayNotUtcDate() {
        val cards = buildCalendarCards(listOf(episode(5,"2026-10-23T23:00:00Z"), episode(6,"2026-10-24T00:00:00Z")),
            emptyList(), "2026-10-24", localDate = { "2026-10-24" })
        assertEquals(1, cards.size)
        assertEquals(0, cards.single().daysAway)
        assertEquals("S1 · E5–6", cards.single().episodeLabel)
        assertNotEquals(cards.single().releaseEpochMs, cards.single().lastReleaseEpochMs)
    }

    @Test fun decoderKeepsIdentitiesAndReliableMetadataWithMissingArtAndTitle() {
        val batch = decodeCalendarBatch("""[{"first_aired":"2026-10-24T20:00:00Z","show":{"title":"Series","ids":{"trakt":12,"imdb":"ttshow","tmdb":34}},"episode":{"season":1,"number":5,"ids":{"trakt":123},"episode_type":"season_finale"}}]""", true)
        val episode = batch.episodes.single()
        assertEquals("trakt:123", episode.id)
        assertEquals(34, episode.show.tmdbId)
        assertNull(episode.title)
        assertNull(episode.show.poster)
        assertTrue(buildCalendarCards(batch.episodes, emptyList(), "2026-10-24", localDate = ::utc).single().finale)
        assertEquals("2026-10-24", decodeCalendarBatch("""[{"released":"2026-10-24","movie":{"title":"Film","ids":{"trakt":7}}}]""", false).movies.single().released)
    }

    @Test fun partialFailureRetryEmptyAndCache() = runBlocking {
        var failMovies = true
        var calls = 0
        val repo = CalendarRepository({ "account" }, { shows, _, _ ->
            calls++
            if (!shows && failMovies) error("offline")
            CalendarBatch(episodes = if (shows) listOf(episode(5)) else emptyList())
        }, now = { 0L }, localDate = { "2026-10-24" }, timezone = { "UTC" })
        repo.load()
        assertTrue(repo.state.value.partial)
        assertEquals(CalendarProblem.NETWORK, repo.state.value.problem)
        assertEquals(1, repo.state.value.cards.size)
        failMovies = false
        repo.load(force = true)
        assertNull(repo.state.value.problem)
        assertFalse(repo.state.value.loading)
        val before = calls
        repo.load()
        assertEquals(before, calls)
        val empty = CalendarRepository({ "account" }, { _, _, _ -> CalendarBatch() }, now = { 0 }, localDate = { "2026-10-24" })
        empty.load()
        assertTrue(empty.state.value.cards.isEmpty())
        assertNull(empty.state.value.problem)
    }

    @Test fun accountChangeAndSameAccountReconnectRejectLateResponses() = runBlocking {
        var account: String? = "A"
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repo = CalendarRepository({ account }, { _, _, _ ->
            started.complete(Unit); release.await(); CalendarBatch(episodes = listOf(episode(5)))
        }, now = { 0 }, localDate = { "2026-10-24" })
        val job = launch { repo.load() }
        started.await()
        account = null; repo.invalidate()
        assertEquals(CalendarProblem.DISCONNECTED, repo.state.value.problem)
        account = "A"; repo.invalidate()
        release.complete(Unit); job.join()
        assertTrue(repo.state.value.cards.isEmpty())
        account = "B"; repo.invalidate()
        assertTrue(repo.state.value.episodes.isEmpty())
    }

    @Test fun authenticationFailureClearsDataAndDoesNotShowPartialContent() = runBlocking {
        val repo = CalendarRepository({ "account" }, { shows, _, _ ->
            if (shows) CalendarBatch(episodes = listOf(episode(5))) else throw CalendarAuthenticationException()
        }, now = { 0 }, localDate = { "2026-10-24" })
        repo.load()
        assertEquals(CalendarProblem.AUTHENTICATION, repo.state.value.problem)
        assertTrue(repo.state.value.cards.isEmpty())
    }
    @Test fun explicitRangeAndTimezoneChangesRefreshReusableData() = runBlocking {
        var zone = "UTC"
        var calls = 0
        val repo = CalendarRepository({ "account" }, { shows, start, days ->
            calls++
            assertEquals("2026-10-31", start)
            assertEquals(9, days)
            CalendarBatch(episodes = if (shows) listOf(episode(5, "2026-11-01T20:00:00Z")) else emptyList())
        }, now = { 0 }, localDate = { ms -> if (ms == 0L) "2026-10-24" else "2026-11-01" }, timezone = { zone })
        repo.load(days = 7, startDate = "2026-11-01")
        assertEquals(8, repo.state.value.cards.single().daysAway)
        zone = "Europe/Vilnius"
        repo.load(days = 7, startDate = "2026-11-01")
        assertEquals(4, calls)
    }

    @Test fun groupedWatchedIndicatorRequiresEveryEpisode() {
        val card = buildCalendarCards(listOf(episode(5), episode(6)), emptyList(), "2026-10-24", localDate = ::utc).single()
        fun keys(number: Int) = com.nuvio.app.features.watched.watchedItemKeys("series", "ttshow", 1, number)
        assertFalse(com.nuvio.app.features.watching.application.WatchingState.isPosterWatched(keys(5), card.toPreview()))
        assertTrue(com.nuvio.app.features.watching.application.WatchingState.isPosterWatched(keys(5) + keys(6), card.toPreview()))
    }

}
