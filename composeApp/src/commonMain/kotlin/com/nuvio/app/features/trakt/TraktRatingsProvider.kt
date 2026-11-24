package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingMutationResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRatingProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Trakt ratings (`/sync/ratings`). The user's movie, show, season and episode ratings are read
 * once per profile, page by page, and kept as a snapshot the Rate strip reads synchronously; a
 * rating written here updates that snapshot in place, so the strip never waits on a relisting.
 */
internal object TraktRatingsProvider : TrackingRatingProvider {
    private const val BASE_URL = "https://api.trakt.tv"
    private const val PAGE_LIMIT = 100
    private const val MAX_PAGES = 50
    private const val STALE_MS = 10 * 60_000L
    private val TYPES = listOf("movies", "shows", "seasons", "episodes")

    override val providerId: TrackingProviderId = TrackingProviderId.TRAKT
    override val ratesSeasons: Boolean get() = true

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val snapshot = MutableStateFlow<Snapshot?>(null)
    private var loadJob: Job? = null
    private var loadingProfile: Int? = null

    override val changes: Flow<Any?> get() = snapshot

    internal class Snapshot(val profileId: Int, val loadedAt: Long, val ratings: Map<String, Int>)

    override fun supports(media: TrackingMediaReference): Boolean =
        media.ids.trakt != null || !media.ids.imdb.isNullOrBlank() || media.ids.tmdb != null

    override fun rating(media: TrackingMediaReference): Double? {
        val profileId = ProfileRepository.activeProfileId
        val current = snapshot.value?.takeIf { it.profileId == profileId }
        if (current == null || WatchProgressClock.nowEpochMs() - current.loadedAt > STALE_MS) load(profileId)
        current ?: return null
        return traktRatingKeys(media).firstNotNullOfOrNull { current.ratings[it] }?.toDouble()
    }

    override fun onEnabled() {
        load(ProfileRepository.activeProfileId)
    }

    override suspend fun rate(profileId: Int, media: TrackingMediaReference, rating: Int?): TrackingMutationResult {
        val headers = TraktAuthRepository.authorizedHeaders(profileId)
            ?: return TrackingMutationResult(attemptedCount = 1, notFoundCount = 1)
        val value = rating?.coerceIn(1, 10)
        val body = buildTraktRatingBody(media, value) ?: return TrackingMutationResult(attemptedCount = 1, notFoundCount = 1)
        val response = httpRequestRaw(
            method = "POST",
            url = if (value == null) "$BASE_URL/sync/ratings/remove" else "$BASE_URL/sync/ratings",
            headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json") + headers,
            body = body.toString(),
        )
        if (response.status !in 200..299) error("Trakt rating failed (${response.status})")
        val notFound = traktNotFoundCount(response.body)
        if (notFound == 0) {
            val keys = traktRatingKeys(media)
            snapshot.update { current ->
                current?.takeIf { it.profileId == profileId }?.let {
                    Snapshot(it.profileId, it.loadedAt, if (value == null) it.ratings - keys.toSet() else it.ratings + keys.associateWith { value })
                } ?: current
            }
        }
        return TrackingMutationResult(attemptedCount = 1, notFoundCount = notFound.coerceAtMost(1))
    }

    private fun load(profileId: Int) {
        if (loadJob?.isActive == true && loadingProfile == profileId) return
        loadJob?.cancel()
        loadingProfile = profileId
        loadJob = scope.launch {
            val ratings = try {
                fetch(profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return@launch
            // A profile switch while the listing ran must not show the old profile's scores.
            if (profileId != ProfileRepository.activeProfileId) return@launch
            snapshot.value = Snapshot(profileId, WatchProgressClock.nowEpochMs(), ratings)
        }
    }

    private suspend fun fetch(profileId: Int): Map<String, Int>? {
        val headers = TraktAuthRepository.authorizedHeaders(profileId) ?: return null
        val ratings = mutableMapOf<String, Int>()
        for (type in TYPES) {
            var page = 1
            while (page <= MAX_PAGES) {
                val response = httpRequestRaw(
                    method = "GET",
                    url = "$BASE_URL/sync/ratings/$type?page=$page&limit=$PAGE_LIMIT",
                    headers = mapOf("Accept" to "application/json") + headers,
                    body = "",
                )
                if (response.status !in 200..299) return null
                val items = json.decodeFromString<List<RatedItem>>(response.body.ifBlank { "[]" })
                items.forEach { item -> item.keys().forEach { ratings[it] = item.rating } }
                val pageCount = response.headers.entries
                    .firstOrNull { it.key.equals("X-Pagination-Page-Count", ignoreCase = true) }
                    ?.value?.trim()?.toIntOrNull() ?: 1
                if (items.isEmpty() || page >= pageCount) break
                page++
            }
        }
        return ratings
    }

    @Serializable
    private data class RatedIds(val trakt: Long? = null, val imdb: String? = null, val tmdb: Long? = null)

    @Serializable
    private data class RatedMedia(val ids: RatedIds? = null, val number: Int? = null, val season: Int? = null)

    @Serializable
    private data class RatedItem(
        val rating: Int,
        val type: String? = null,
        val movie: RatedMedia? = null,
        val show: RatedMedia? = null,
        val season: RatedMedia? = null,
        val episode: RatedMedia? = null,
    ) {
        fun keys(): List<String> {
            movie?.ids?.let { return traktIdKeys("movie", it.trakt, it.imdb, it.tmdb) }
            val showIds = show?.ids ?: return emptyList()
            val base = traktIdKeys("show", showIds.trakt, showIds.imdb, showIds.tmdb)
            val suffix = when {
                episode != null -> ":s${episode.season ?: return emptyList()}:e${episode.number ?: return emptyList()}"
                season != null -> ":s${season.number ?: return emptyList()}"
                else -> ""
            }
            return base.map { it + suffix }
        }
    }
}

private fun traktIdKeys(kind: String, trakt: Long?, imdb: String?, tmdb: Long?): List<String> = listOfNotNull(
    trakt?.let { "$kind:trakt:$it" },
    imdb?.takeIf(String::isNotBlank)?.let { "$kind:imdb:$it" },
    tmdb?.let { "$kind:tmdb:$it" },
)

private fun TrackingMediaReference.isTraktMovie(): Boolean =
    kind == TrackingMediaKind.MOVIE || catalog?.contentType?.lowercase() in setOf("movie", "film")

/** Lookup keys for [media] in the Trakt ratings snapshot: the title's ids, plus its season or episode. */
internal fun traktRatingKeys(media: TrackingMediaReference): List<String> {
    val movie = media.isTraktMovie()
    val base = traktIdKeys(if (movie) "movie" else "show", media.ids.trakt, media.ids.imdb, media.ids.tmdb)
    if (movie) return base
    val suffix = media.episode?.let { ":s${it.season ?: 1}:e${it.number}" }
        ?: media.wholeSeason?.let { ":s$it" }
        ?: ""
    return base.map { it + suffix }
}

/** The `/sync/ratings` (or `/remove` when [rating] is null) body for a movie, show, season or episode. */
internal fun buildTraktRatingBody(media: TrackingMediaReference, rating: Int?): JsonObject? {
    val ids = buildJsonObject {
        media.ids.trakt?.let { put("trakt", it) }
        media.ids.imdb?.takeIf(String::isNotBlank)?.let { put("imdb", it) }
        media.ids.tmdb?.let { put("tmdb", it) }
        media.ids.tvdb?.toLongOrNull()?.takeIf { !media.isTraktMovie() }?.let { put("tvdb", it) }
    }
    if (ids.isEmpty()) return null
    if (media.isTraktMovie()) {
        return buildJsonObject {
            putJsonArray("movies") { add(buildJsonObject { rating?.let { put("rating", it) }; put("ids", ids) }) }
        }
    }
    val episode = media.episode
    val season = media.wholeSeason
    val show = buildJsonObject {
        put("ids", ids)
        when {
            episode != null -> putJsonArray("seasons") {
                add(buildJsonObject {
                    put("number", episode.season ?: 1)
                    putJsonArray("episodes") {
                        add(buildJsonObject { put("number", episode.number); rating?.let { put("rating", it) } })
                    }
                })
            }
            season != null -> putJsonArray("seasons") {
                add(buildJsonObject { put("number", season); rating?.let { put("rating", it) } })
            }
            else -> rating?.let { put("rating", it) }
        }
    }
    return buildJsonObject { putJsonArray("shows") { add(show) } }
}

internal fun traktNotFoundCount(body: String): Int {
    val payload = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return 0
    val notFound = runCatching { payload["not_found"]?.jsonObject }.getOrNull() ?: return 0
    return notFound.values.sumOf { (it as? JsonArray)?.size ?: 0 }
}
