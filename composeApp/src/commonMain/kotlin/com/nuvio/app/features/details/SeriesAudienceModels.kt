package com.nuvio.app.features.details

import com.nuvio.app.features.trakt.AudienceEpisodeKey
import com.nuvio.app.features.trakt.TraktAudienceEpisode
import com.nuvio.app.features.trakt.TraktAudienceSeason
import com.nuvio.app.features.trakt.TraktAudienceStats
import com.nuvio.app.features.watched.episodePlaybackId
import com.nuvio.app.features.watched.releasedPlayableEpisodes
import kotlin.math.roundToInt

internal data class SeasonWatchedCount(val season: Int, val watched: Int, val aired: Int)

internal fun MetaDetails.seasonWatchedCounts(
    today: String,
    isWatched: (MetaVideo) -> Boolean,
): List<SeasonWatchedCount> = releasedPlayableEpisodes(today)
    .groupBy { episodePlaybackId(it) }
    .values.groupBy { normalizeSeasonNumber(it.first().season) }
    .entries.sortedBy { seasonSortKey(it.key) }
    .map { (season, entries) ->
        SeasonWatchedCount(season, entries.count { duplicates -> duplicates.any(isWatched) }, entries.size)
    }

internal fun List<TraktAudienceSeason>.episodeMap(): Map<AudienceEpisodeKey, TraktAudienceEpisode> =
    sortedBy { it.number }.flatMap { season ->
        season.episodes.sortedBy { it.number }.map { AudienceEpisodeKey(season.number, it.number) to it }
    }.toMap()

private fun String?.matchableTitle(): String? =
    this?.lowercase()?.filter(Char::isLetterOrDigit)?.takeIf { it.isNotEmpty() }

private fun String?.airDate(): String? = this?.take(10)?.takeIf { it.length == 10 }

internal fun mapAudienceEpisodes(
    videos: List<MetaVideo>,
    audience: Map<AudienceEpisodeKey, TraktAudienceEpisode>,
): Map<AudienceEpisodeKey, MetaVideo> {
    val candidates = videos.filter { it.season != null && it.episode != null }.distinctBy { it.id }
    val byId = candidates.groupBy { it.id.lowercase() }
    val byNumber = candidates.groupBy { AudienceEpisodeKey(requireNotNull(it.season), requireNotNull(it.episode)) }
    val byTitleAndDate = candidates.groupBy { it.title.matchableTitle() to it.released.airDate() }
    val matches = audience.mapNotNull { (key, episode) ->
        val ids = episode.ids
        val stable = (byId[ids?.imdb].orEmpty() + byId[ids?.trakt?.let { "trakt:$it" }].orEmpty()).distinct()
        if (stable.isNotEmpty()) return@mapNotNull stable.singleOrNull()?.let { key to it }

        val title = episode.title.matchableTitle()
        val date = episode.firstAired.airDate()
        val exact = byNumber[key].orEmpty().filter { video ->
            val otherTitle = video.title.matchableTitle()
            val otherDate = video.released.airDate()
            val titleMatches = title != null && title == otherTitle
            val dateMatches = date != null && date == otherDate
            (titleMatches || dateMatches) &&
                (title == null || otherTitle == null || titleMatches) &&
                (date == null || otherDate == null || dateMatches)
        }
        if (exact.isNotEmpty()) return@mapNotNull exact.singleOrNull()?.let { key to it }

        val reordered = if (title != null && date != null) byTitleAndDate[title to date]?.singleOrNull() else null
        reordered?.let { key to it }
    }
    return matches.groupBy { it.second.id }.values.mapNotNull { it.singleOrNull() }.toMap()
}

internal fun retentionRatios(
    keys: List<AudienceEpisodeKey>,
    stats: Map<AudienceEpisodeKey, TraktAudienceStats>,
): List<Double?> {
    val first = keys.firstOrNull { it.episode == 1 }
    val baseline = stats[first]?.watchers?.takeIf { it > 0 }
    return keys.map { key ->
        if (baseline == null) null else stats[key]?.watchers?.takeIf { it >= 0 }
            ?.let { (it.toDouble() * 1000 / baseline).roundToInt() / 10.0 }
    }
}

internal fun relativeAudienceValues(values: List<Double?>): List<Int?> {
    val low = values.filterNotNull().minOrNull() ?: return values.map { null }
    val high = values.filterNotNull().max()
    return values.map { value ->
        value?.let { if (low == high) 100 else ((it - low) * 100 / (high - low)).roundToInt() }
    }
}

internal fun largestAudienceDrop(values: List<Double?>): Pair<Int, Double>? =
    values.zipWithNext().mapIndexedNotNull { index, (before, after) ->
        if (before == null || after == null || before - after < 0.999999) null
        else index to (before - after)
    }.maxByOrNull { it.second }

internal data class RetentionAxisTick(val index: Int, val left: Int)

internal fun retentionAxisTicks(
    episodes: List<Int>,
    labelWidths: List<Int>,
    chartWidth: Int,
    gap: Int,
): List<RetentionAxisTick> {
    if (episodes.isEmpty() || chartWidth <= 0) return emptyList()
    val candidates = episodes.indices.map { index ->
        val center = (index + 0.5) * chartWidth / episodes.size
        val left = (center - labelWidths[index] / 2.0).roundToInt()
            .coerceIn(0, (chartWidth - labelWidths[index]).coerceAtLeast(0))
        RetentionAxisTick(index, left)
    }
    fun fits(ticks: List<RetentionAxisTick>): Boolean =
        ticks.all { labelWidths[it.index] <= chartWidth } &&
            ticks.zipWithNext().all { (a, b) -> a.left + labelWidths[a.index] + gap <= b.left }

    val last = episodes.lastIndex
    for (interval in listOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 500, 1000)) {
        val ticks = candidates.filter { it.index == 0 || it.index == last || episodes[it.index] % interval == 0 }
        if (fits(ticks)) return ticks
    }
    val endpoints = candidates.filter { it.index == 0 || it.index == last }
    return if (fits(endpoints)) endpoints else endpoints.firstOrNull { labelWidths[it.index] <= chartWidth }?.let(::listOf).orEmpty()
}
