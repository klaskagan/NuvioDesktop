package com.nuvio.app.features.trakt

import com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingHistoryRemover
import com.nuvio.app.features.tracking.TrackingHistoryEvent
import com.nuvio.app.features.tracking.TrackingHistoryPage
import com.nuvio.app.features.tracking.TrackingHistoryProvider
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object TraktHistoryProvider : TrackingHistoryProvider, TrackingHistoryRemover {
    override val providerId = TrackingProviderId.TRAKT
    override val account get() = TraktAuthRepository.historyAccount

    override suspend fun removeEvent(profileId: Int, eventId: String) {
        val session = account.value?.takeIf { it.profileId == profileId }
            ?: throw CancellationException("Trakt history session ended")
        val headers = checkNotNull(TraktAuthRepository.authorizedHeaders(profileId))
        if (account.value != session) throw CancellationException("Trakt history session changed")
        val response = httpRequestRaw(
            method = "POST", url = "https://api.trakt.tv/sync/history/remove",
            headers = headers + ("Content-Type" to "application/json"),
            body = "{\"ids\":[${eventId.toLong()}]}",
        )
        check(response.status in 200..299) { "Trakt history removal failed: ${response.status}" }
        if (account.value != session) throw CancellationException("Trakt history session changed")
    }

    override suspend fun loadPage(profileId: Int, cursor: String?) = loadFilteredPage(profileId, cursor, null)

    override suspend fun loadFilteredPage(profileId: Int, cursor: String?, kind: TrackingMediaKind?): TrackingHistoryPage {
        val session = account.value?.takeIf { it.profileId == profileId }
            ?: throw CancellationException("Trakt history session ended")
        val headers = checkNotNull(TraktAuthRepository.authorizedHeaders(profileId)) {
            "Trakt history authorization unavailable"
        }
        if (account.value != session) throw CancellationException("Trakt history session changed")
        val page = cursor?.toInt() ?: 1
        val type = when (kind) {
            TrackingMediaKind.MOVIE -> "/movies"
            TrackingMediaKind.SHOW, TrackingMediaKind.ANIME -> "/episodes"
            null -> ""
        }
        val response = httpRequestRaw(
            method = "GET",
            url = "https://api.trakt.tv/sync/history$type?page=$page&limit=50&extended=full",
            headers = headers,
            body = "",
        )
        check(response.status in 200..299) { "Trakt history request failed: ${response.status}" }
        if (account.value != session) throw CancellationException("Trakt history session changed")
        return decodeTraktHistoryPage(response, page)
    }
}

private val historyJson = Json { ignoreUnknownKeys = true }

internal fun decodeTraktHistoryPage(response: RawHttpResponse, page: Int): TrackingHistoryPage {
    val pageCount = response.headers.entries
        .firstOrNull { (name, _) -> name.equals("X-Pagination-Page-Count", ignoreCase = true) }
        ?.value?.toIntOrNull()
    return TrackingHistoryPage(
        events = historyJson.decodeFromString<List<HistoryEventDto>>(response.body).mapNotNull { it.toEvent() },
        nextCursor = if (pageCount != null && page < pageCount) (page + 1).toString() else null,
    )
}

@Serializable
private data class HistoryEventDto(
    val id: Long? = null,
    @SerialName("watched_at") val watchedAt: String? = null,
    val type: String? = null,
    val movie: HistoryMediaDto? = null,
    val show: HistoryMediaDto? = null,
    val episode: HistoryEpisodeDto? = null,
) {
    fun toEvent(): TrackingHistoryEvent? {
        val eventId = id ?: return null
        val time = watchedAt?.let(::parseZonedIsoDateTimeToEpochMs) ?: return null
        val media = when (type) {
            "movie" -> movie?.toReference(TrackingMediaKind.MOVIE)
            "episode" -> show?.toReference(TrackingMediaKind.SHOW)?.copy(
                episode = episode?.number?.let { TrackingEpisode(episode.season, it, episode.title) },
            )
            else -> null
        } ?: return null
        return TrackingHistoryEvent(
            id = eventId.toString(),
            watchedAtMillis = time,
            media = media,
            episodeScreenshot = episode?.images?.screenshot.firstTraktImageUrl(),
            landscapeArtwork = movie?.images.traktBestLandscapeUrl(),
        )
    }
}

@Serializable
private data class HistoryEpisodeDto(
    val season: Int? = null,
    val number: Int? = null,
    val title: String? = null,
    val images: CalendarEpisodeImages? = null,
)

@Serializable
private data class HistoryMediaDto(
    val title: String? = null,
    val year: Int? = null,
    val ids: HistoryIdsDto? = null,
    val images: TraktImagesDto? = null,
) {
    fun toReference(kind: TrackingMediaKind) = TrackingMediaReference(
        kind = kind,
        title = title,
        year = year,
        ids = TrackingExternalIds(imdb = ids?.imdb, tmdb = ids?.tmdb, tvdb = ids?.tvdb?.toString(), trakt = ids?.trakt),
        posterUrl = images.traktBestPosterUrl(),
    )
}

@Serializable
private data class HistoryIdsDto(
    val imdb: String? = null,
    val tmdb: Long? = null,
    val tvdb: Long? = null,
    val trakt: Long? = null,
)
