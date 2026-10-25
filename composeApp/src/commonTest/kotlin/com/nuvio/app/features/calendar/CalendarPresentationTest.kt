package com.nuvio.app.features.calendar

import com.nuvio.app.features.trakt.*
import kotlinx.coroutines.*
import kotlin.test.*

class CalendarPresentationTest {
    @Test fun localeWeekBoundariesAndMonthPadding() {
        assertEquals("2026-10-19", calendarWeekStart("2026-10-25", 0))
        assertEquals("2026-10-25", calendarWeekStart("2026-10-25", 6))
        val november = calendarMonthRange("2026-11-09", 0)
        assertEquals("2026-10-26", november.start)
        assertEquals(42, november.days)
        assertEquals("2026-12-06", november.dates.last())
        assertEquals(28, calendarMonthRange("2027-02-01", 0).days)
        assertEquals(29, calendarMonthLength(2028, 2))
        assertEquals(28, calendarMonthLength(2100, 2))
    }
    @Test fun relativeWeekHeadingsRespectLocalWeekBoundary() {
        assertEquals(0, calendarRelativeWeek("2026-10-19", "2026-10-25", 0))
        assertEquals(1, calendarRelativeWeek("2026-10-26", "2026-10-25", 0))
        assertEquals(0, calendarRelativeWeek("2026-10-25", "2026-10-25", 6))
        assertEquals(1, calendarRelativeWeek("2026-11-01", "2026-10-25", 6))
        assertNull(calendarRelativeWeek("2026-11-02", "2026-10-25", 0))
    }
    @Test fun compactWeekRangesDoNotRepeatMonthOrOmitYearAtYearBoundary() {
        assertEquals("16 – " + CalendarPlatform.dateLabel("2026-11-22", "d MMMM yyyy"), calendarWeekRangeLabel("2026-11-16", "2026-11-22"))
        assertEquals(CalendarPlatform.dateLabel("2026-11-30", "d MMM") + " – " + CalendarPlatform.dateLabel("2026-12-06", "d MMMM yyyy"), calendarWeekRangeLabel("2026-11-30", "2026-12-06"))
        assertEquals(CalendarPlatform.dateLabel("2026-12-28", "d MMM yyyy") + " – " + CalendarPlatform.dateLabel("2027-01-03", "d MMM yyyy"), calendarWeekRangeLabel("2026-12-28", "2027-01-03"))
    }
    @Test fun monthGridAlwaysReservesSixWeeks() {
        for (firstWeekday in 0..6) for (date in listOf("2027-02-01", "2027-03-01", "2027-05-01", "2028-02-01", "2026-12-01", "2027-01-01")) {
            val grid = calendarMonthGridRange(date, firstWeekday)
            assertEquals(42, grid.days)
            assertEquals(calendarMonthRange(date, firstWeekday).start, grid.start)
            val ownDates = grid.dates.filter { it.startsWith(date.take(7)) }
            assertEquals(calendarMonthLength(date.take(4).toInt(), date.substring(5, 7).toInt()), ownDates.size)
        }
    }
    @Test fun monthMovesClampAndCrossYears() {
        assertEquals("2026-02-28", calendarMoveMonth("2026-01-31", 1))
        assertEquals("2028-02-29", calendarMoveMonth("2028-03-31", -1))
        assertEquals("2027-01-30", calendarMoveMonth("2026-12-30", 1))
        assertEquals("2025-12-31", calendarMoveMonth("2026-01-31", -1))
    }
    @Test fun daySectionsSkipEmptyAndFilteredDates() {
        val show = CalendarMedia("show", "series", "Series")
        val movie = CalendarMedia("movie", "movie", "Movie")
        val cards = listOf(CalendarCard("a", show, "2026-11-26", daysAway = 0),
            CalendarCard("b", movie, "2026-11-30", daysAway = 4),
            CalendarCard("c", show, "2026-12-07", daysAway = 11))
        assertEquals(listOf("2026-11-26", "2026-11-30", "2026-12-07"), calendarDayGroups(cards, CalendarFilter.All).keys.toList())
        assertEquals(listOf("2026-11-26", "2026-12-07"), calendarDayGroups(cards, CalendarFilter.Shows).keys.toList())
        assertEquals(listOf("2026-11-30"), calendarDayGroups(cards, CalendarFilter.Movies).keys.toList())
    }
    @Test fun sharedTransportDeduplicatesRangesAndKeepsConsumersIndependent() = runBlocking {
        var calls = 0
        val cache = CalendarTransportCache({ "A" }, { shows, _, count ->
            calls++; assertEquals(7, count)
            CalendarBatch(movies = if (shows) emptyList() else listOf(
                CalendarMovie(CalendarMedia("first", "movie", "First"), "2026-10-25"),
                CalendarMovie(CalendarMedia("second", "movie", "Second"), "2026-11-01")))
        }, { 0 })
        val home = CalendarRepository({ "A" }, cache::load, { 0 }, { "2026-10-25" }, { "UTC" })
        val screen = CalendarRepository({ "A" }, cache::load, { 0 }, { "2026-10-25" }, { "UTC" })
        coroutineScope { awaitAll(async { home.load() }, async { screen.load(42, startDate = "2026-10-26") }) }
        val homeBefore = home.state.value
        assertEquals(2, homeBefore.cards.size)
        assertEquals(1, screen.state.value.cards.size)
        val before = calls
        screen.load(7, startDate = "2026-10-26", force = true)
        assertEquals(before, calls)
        assertEquals(homeBefore, home.state.value)
        assertTrue(calls < 24)
    }
    @Test fun cacheExpiresAndFailedEndpointCanRetryWithoutDroppingSuccessfulBatches() = runBlocking {
        var time = 0L
        var showsCalls = 0
        var movieCalls = 0
        var failMovies = true
        val cache = CalendarTransportCache({ "A" }, { shows, _, _ ->
            if (shows) showsCalls++ else { movieCalls++; if (failMovies) error("offline") }
            CalendarBatch()
        }, { time })
        cache.load(true, "2026-10-25", 7)
        assertFailsWith<IllegalStateException> { cache.load(false, "2026-10-25", 7) }
        val before = showsCalls
        failMovies = false
        cache.load(true, "2026-10-25", 7)
        cache.load(false, "2026-10-25", 7)
        assertEquals(before, showsCalls)
        assertTrue(movieCalls > 1)
        time = 600_001
        cache.load(true, "2026-10-25", 7)
        assertTrue(showsCalls > before)
    }
    @Test fun cacheDoesNotPublishOldAccountResponse() = runBlocking {
        var account: String? = "A"
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cache = CalendarTransportCache({ account }, { _, _, _ -> started.complete(Unit); release.await(); CalendarBatch() })
        val job = async { runCatching { cache.load(true, "2026-10-25", 7) } }
        started.await(); account = "B"; cache.invalidate(); release.complete(Unit)
        assertTrue(job.await().exceptionOrNull() is CancellationException)
    }
}
