package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.format.formatLocalTime
import com.nuvio.app.features.trakt.CalendarCard
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun calendarBadge(card: CalendarCard): String {
    val day = when (card.daysAway) {
        0 -> stringResource(Res.string.calendar_today)
        1 -> stringResource(Res.string.calendar_tomorrow)
        else -> stringResource(Res.string.calendar_in_days, card.daysAway)
    }
    val labels = listOfNotNull(day,
        if (card.premiere) stringResource(Res.string.calendar_premiere) else null,
        if (card.finale) stringResource(Res.string.calendar_finale) else null)
    return labels.joinToString(" · ")
}

@Composable
internal fun calendarMovieLabel(year: Int?): String = stringResource(Res.string.calendar_movie) +
    (year?.let { " · $it" } ?: "")

internal fun calendarReleaseLabel(card: CalendarCard): String {
    val date = formatReleaseDateForDisplay(card.localDate)
    val start = card.releaseEpochMs ?: return date
    val first = com.nuvio.app.core.format.formatLocalTime(start)
    val last = card.lastReleaseEpochMs?.takeIf { it != start }?.let(::formatLocalTime)
    return "$date · $first" + if (last != null && last != first) "–$last" else ""
}
