package com.nuvio.app.features.calendar

import com.nuvio.app.core.time.isoEpochDay
import com.nuvio.app.features.trakt.CalendarCard
import com.nuvio.app.features.trakt.calendarDatePlusDays

internal enum class CalendarView { Day, Week, Month }
internal enum class CalendarFilter { All, Shows, Movies }
internal data class CalendarRange(val start: String, val days: Int) {
    val dates get() = (0 until days).map { calendarDatePlusDays(start, it) }
}
internal fun calendarWeekStart(date: String, firstWeekday: Int): String {
    val weekday = ((isoEpochDay(date) + 3) % 7 + 7).toInt() % 7 // Monday = 0
    return calendarDatePlusDays(date, -((weekday - firstWeekday + 7) % 7))
}
internal fun calendarMonthLength(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}
internal fun calendarMoveMonth(date: String, delta: Int): String {
    val index = date.take(4).toInt() * 12 + date.substring(5, 7).toInt() - 1 + delta
    val year = index / 12
    val month = index % 12 + 1
    val day = date.takeLast(2).toInt().coerceAtMost(calendarMonthLength(year, month))
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
internal fun calendarMonthRange(date: String, firstWeekday: Int): CalendarRange {
    val first = date.take(7) + "-01"
    val start = calendarWeekStart(first, firstWeekday)
    val count = (isoEpochDay(first) - isoEpochDay(start)).toInt() + calendarMonthLength(date.take(4).toInt(), date.substring(5, 7).toInt())
    return CalendarRange(start, ((count + 6) / 7) * 7)
}
/** Presentation always reserves six weeks; fetching can retain its minimal month range. */
internal fun calendarMonthGridRange(date: String, firstWeekday: Int): CalendarRange =
    CalendarRange(calendarMonthRange(date, firstWeekday).start, 42)

internal fun calendarFiltered(cards: List<CalendarCard>, filter: CalendarFilter): List<CalendarCard> = cards.filter {
    when (filter) { CalendarFilter.All -> true; CalendarFilter.Shows -> it.media.type == "series"; CalendarFilter.Movies -> it.media.type == "movie" }
}
internal fun calendarDayGroups(cards: List<CalendarCard>, filter: CalendarFilter) =
    calendarFiltered(cards, filter).sortedBy { it.localDate }.groupBy { it.localDate }

/** Regional labels use the platform calendar; business dates stay ISO and timezone-independent. */
internal expect object CalendarPlatform {
    fun dateLabel(date: String, pattern: String): String
    fun firstWeekday(): Int
    fun localeKey(): String
    fun shortMonthLabel(date: String): String
    fun monthLabel(date: String): String
    fun dayLabel(date: String): String
    fun weekdayLabel(date: String): String
}

/** Relative weeks are anchored to the device's current local date and first weekday. */
internal fun calendarRelativeWeek(start: String, today: String, firstWeekday: Int): Int? =
    when (start) {
        calendarWeekStart(today, firstWeekday) -> 0
        calendarDatePlusDays(calendarWeekStart(today, firstWeekday), 7) -> 1
        else -> null
    }
internal fun calendarWeekRangeLabel(start: String, end: String): String = when {
    start.take(7) == end.take(7) -> "${start.takeLast(2).toInt()} – ${CalendarPlatform.dateLabel(end, "d MMMM yyyy")}"
    start.take(4) == end.take(4) -> "${CalendarPlatform.dateLabel(start, "d MMM")} – ${CalendarPlatform.dateLabel(end, "d MMMM yyyy")}"
    else -> "${CalendarPlatform.dateLabel(start, "d MMM yyyy")} – ${CalendarPlatform.dateLabel(end, "d MMM yyyy")}"
}
