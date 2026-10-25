package com.nuvio.app.features.trakt

import com.nuvio.app.core.format.formatLocalTime
import com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs
import com.nuvio.app.features.home.components.calendarReleaseLabel
import java.util.TimeZone
import kotlin.test.*

class TraktCalendarLocalTimeTest {
    @Test fun vilniusDstAndDateOnlyMoviesUseDeviceLocalDate() {
        val original = TimeZone.getDefault()
        val locale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.UK)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vilnius"))
            val show = CalendarMedia("show", "series", "Series")
            fun episode(n: Int, date: String) = CalendarEpisode("e$n", show, 1, n, null, date, parseZonedIsoDateTimeToEpochMs(date)!!)
            val cards = buildCalendarCards(listOf(
                episode(5, "2026-10-24T22:30:00Z"), // 01:30 next local date
                episode(6, "2026-10-25T02:30:00Z"), // 04:30 after DST fallback
            ), listOf(CalendarMovie(CalendarMedia("movie", "movie", "Film"), "2026-10-25")), "2026-10-25")
            assertEquals(2, cards.size)
            val group = cards.last()
            assertEquals("2026-10-25", group.localDate)
            assertEquals(0, group.daysAway)
            assertEquals("01:30", com.nuvio.app.core.format.formatLocalTime(group.releaseEpochMs!!))
            assertEquals("04:30", com.nuvio.app.core.format.formatLocalTime(group.lastReleaseEpochMs!!))
            assertTrue(calendarReleaseLabel(group).endsWith("01:30–04:30"))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val shifted = buildCalendarCards(group.episodes, listOf(CalendarMovie(CalendarMedia("movie", "movie", "Film"), "2026-10-25")), "2026-10-24")
            assertEquals("2026-10-24", shifted.first().localDate)
            assertEquals("2026-10-25", shifted.last().localDate)
            assertNull(shifted.last().releaseEpochMs)
        } finally { TimeZone.setDefault(original); java.util.Locale.setDefault(locale) }
    }
}
