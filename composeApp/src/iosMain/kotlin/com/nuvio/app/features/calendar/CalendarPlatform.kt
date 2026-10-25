package com.nuvio.app.features.calendar

import platform.Foundation.*
internal actual object CalendarPlatform {
    actual fun dateLabel(date: String, pattern: String): String = label(date, pattern)
    actual fun firstWeekday(): Int = (NSCalendar.currentCalendar.firstWeekday.toInt() + 5) % 7
    actual fun localeKey(): String = NSLocale.currentLocale.localeIdentifier
    private fun label(date: String, pattern: String): String {
        val parser = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }
        val value = parser.dateFromString(date) ?: return date
        return NSDateFormatter().apply { dateFormat = pattern }.stringFromDate(value)
    }
    actual fun shortMonthLabel(date: String): String = label(date, "MMM")
    actual fun monthLabel(date: String): String = label(date, "LLLL yyyy")
    actual fun dayLabel(date: String): String = label(date, "EEEE, d MMMM yyyy")
    actual fun weekdayLabel(date: String): String = label(date, "EEE")
}
