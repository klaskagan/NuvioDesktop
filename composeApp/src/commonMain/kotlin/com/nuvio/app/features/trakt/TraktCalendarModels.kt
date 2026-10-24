package com.nuvio.app.features.trakt

import com.nuvio.app.core.time.*
import com.nuvio.app.features.home.MetaPreview
import kotlin.time.Instant

/** Raw calendar releases remain independent of Home and retain all constituent identities. */
data class CalendarMedia(
    val id: String,
    val type: String,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val landscape: String? = null,
    val traktId: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
)

data class CalendarEpisode(
    val id: String,
    val show: CalendarMedia,
    val season: Int,
    val number: Int,
    val title: String?,
    val firstAired: String,
    val airingEpochMs: Long,
    val episodeType: String? = null,
    val screenshot: String? = null,
    val traktId: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
)

data class CalendarMovie(val movie: CalendarMedia, val released: String, val releaseEpochMs: Long? = null)

data class CalendarCard(
    val key: String,
    val media: CalendarMedia,
    val localDate: String,
    val episodes: List<CalendarEpisode> = emptyList(),
    val releaseEpochMs: Long? = null,
    val lastReleaseEpochMs: Long? = releaseEpochMs,
    val daysAway: Int,
) {
    val episodeLabel: String? get() = episodes.takeIf { it.isNotEmpty() }?.let {
        val numbers = it.map(CalendarEpisode::number)
        "S${it.first().season} · E${numbers.min()}" +
            if (numbers.min() == numbers.max()) "" else "–${numbers.max()}"
    }
    val premiere: Boolean get() = episodes.any { it.episodeType in setOf("series_premiere", "season_premiere", "mid_season_premiere") }
    val finale: Boolean get() = episodes.any { it.episodeType in setOf("series_finale", "season_finale", "mid_season_finale") }
    fun toPreview(): MetaPreview = MetaPreview(
        id = media.id, type = media.type, name = media.title,
        poster = media.poster, banner = media.landscape ?: episodes.firstOrNull()?.screenshot,
        calendarCard = this,
    )
}

/** Pure presentation mapping, also usable by a future Calendar screen. */
fun buildCalendarCards(
    episodes: List<CalendarEpisode>,
    movies: List<CalendarMovie>,
    today: String,
    days: Int = 14,
    localDate: (Long) -> String? = EpisodeReleaseDatePlatform::localIsoDateAtEpochMs,
    startDate: String = today,
): List<CalendarCard> {
    fun distance(date: String) = (isoEpochDay(date) - isoEpochDay(today)).toInt()
    fun inWindow(date: String) = (isoEpochDay(date) - isoEpochDay(startDate)).toInt() in 0 until days
    val episodeCards = episodes.distinctBy { it.id }.mapNotNull { episode ->
        localDate(episode.airingEpochMs)?.let { date -> episode to date }
    }.filter { inWindow(it.second) }
        .groupBy { (episode, date) -> Triple(episode.show.id, episode.season, date) }
        .map { (group, values) ->
            val members = values.map { it.first }.sortedBy { it.number }
            CalendarCard("episode:${group.first}:${group.second}:${group.third}", members.first().show,
                group.third, members, members.minOf { it.airingEpochMs }, members.maxOf { it.airingEpochMs }, distance(group.third))
        }
    val movieCards = movies.distinctBy { it.movie.id to it.released }.mapNotNull { release ->
        val date = if (release.releaseEpochMs == null) parseIsoCalendarDate(release.released)
            else localDate(release.releaseEpochMs)
        date?.takeIf { inWindow(it) }?.let {
            CalendarCard("movie:${release.movie.id}:$it", release.movie, it,
                releaseEpochMs = release.releaseEpochMs, daysAway = distance(it))
        }
    }
    return (episodeCards + movieCards).sortedWith(compareBy({ it.localDate }, { it.releaseEpochMs ?: Long.MIN_VALUE }, { it.key }))
}

internal fun calendarDatePlusDays(date: String, days: Int): String =
    Instant.fromEpochMilliseconds((isoEpochDay(date) + days) * 86_400_000L).toString().take(10)

/** One extra UTC date on either edge covers all local zones, including a DST transition. */
internal fun calendarUtcRequestWindow(today: String, days: Int): Pair<String, Int> = calendarDatePlusDays(today, -1) to days + 2
