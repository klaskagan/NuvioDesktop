package com.nuvio.app.features.calendar

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

internal actual object CalendarPlatform {
    actual fun dateLabel(date: String, pattern: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    actual fun firstWeekday(): Int = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1
    actual fun localeKey(): String = Locale.getDefault().toLanguageTag()
    actual fun shortMonthLabel(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
    actual fun monthLabel(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
    actual fun dayLabel(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
    actual fun weekdayLabel(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
}
