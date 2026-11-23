package com.nuvio.app.features.history

import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingHistoryEvent
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryBoardStatsTest {
    private val today = LocalDate.of(2026, 11, 23)

    private fun at(date: LocalDate, hour: Int = 20) = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun episode(id: String, show: String, date: LocalDate) = TrackingHistoryEvent(
        id = id, watchedAtMillis = at(date),
        media = TrackingMediaReference(TrackingMediaKind.SHOW, title = show, ids = TrackingExternalIds(imdb = "tt-$show"), episode = TrackingEpisode(season = 1, number = 1)),
    )

    private fun movie(id: String, date: LocalDate) = TrackingHistoryEvent(
        id = id, watchedAtMillis = at(date), media = TrackingMediaReference(TrackingMediaKind.MOVIE, title = "Movie $id"),
    )

    @Test fun weekCountsOnlyTheLastSevenDays() {
        val events = listOf(
            episode("a", "Alpha", today), episode("b", "Alpha", today.minusDays(1)), movie("m", today.minusDays(6)),
            episode("old", "Alpha", today.minusDays(7)),
        )
        val stats = historyWeekStats(events, today.toString())
        assertEquals(HistoryWeekStats(episodes = 2, movies = 1, activeDays = 3), stats)
    }

    @Test fun topShowIsTheMostWatchedSeriesThisWeek() {
        val events = listOf(
            episode("a", "Alpha", today), episode("b", "Beta", today), episode("c", "Beta", today.minusDays(2)),
            episode("d", "Alpha", today.minusDays(10)), episode("e", "Alpha", today.minusDays(11)), movie("m", today),
        )
        val top = historyTopShow(events, today.toString())!!
        assertEquals("Beta", top.title)
        assertEquals(2, top.watches)
        assertNull(historyTopShow(listOf(movie("m", today)), today.toString()))
    }
}
