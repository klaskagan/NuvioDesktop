package com.nuvio.app.features.trakt

import com.nuvio.app.features.tracking.imdbIdOrNull
import com.nuvio.app.features.tracking.tmdbIdOrNull

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.tracking.TrackingFavoritesProvider
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Trakt favorites (`/sync/favorites`). The user's favorite shows and films are read once, page by
 * page, and kept for a few minutes per profile; a change updates that copy, so the ♥ never waits
 * on a second listing.
 */
internal object TraktFavoritesProvider : TrackingFavoritesProvider {
    private const val BASE_URL = "https://api.trakt.tv"
    private const val PAGE_LIMIT = 100
    private const val MAX_PAGES = 20
    private const val CACHE_MS = 5 * 60_000L

    override val providerId: TrackingProviderId = TrackingProviderId.TRAKT

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mutex = Mutex()
    private var cached: CachedFavorites? = null

    private class CachedFavorites(val profileId: Int, val loadedAt: Long, val ids: MutableSet<String>)

    override suspend fun isFavorite(profileId: Int, media: TrackingMediaReference): Boolean? {
        val keys = keysOf(media).ifEmpty { return null }
        val ids = favorites(profileId) ?: return null
        return keys.any { it in ids }
    }

    override suspend fun setFavorite(profileId: Int, media: TrackingMediaReference, favorite: Boolean): Boolean {
        val headers = TraktAuthRepository.authorizedHeaders(profileId) ?: return false
        val ids = FavoriteIds(
            trakt = media.ids.trakt,
            imdb = media.ids.imdb?.takeIf { it.isNotBlank() },
            tmdb = media.ids.tmdb,
        )
        if (ids.trakt == null && ids.imdb == null && ids.tmdb == null) return false
        val item = FavoriteItem(ids)
        val body = if (media.kind == TrackingMediaKind.MOVIE) FavoriteBody(movies = listOf(item)) else FavoriteBody(shows = listOf(item))
        val response = httpRequestRaw(
            method = "POST",
            url = if (favorite) "$BASE_URL/sync/favorites" else "$BASE_URL/sync/favorites/remove",
            headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json") + headers,
            body = json.encodeToString(body),
        )
        if (response.status !in 200..299) return false
        mutex.withLock {
            cached?.takeIf { it.profileId == profileId }?.ids?.let { set ->
                keysOf(media).forEach { if (favorite) set += it else set -= it }
            }
        }
        return true
    }

    private suspend fun favorites(profileId: Int): Set<String>? = mutex.withLock {
        val now = WatchProgressClock.nowEpochMs()
        cached?.takeIf { it.profileId == profileId && now - it.loadedAt < CACHE_MS }?.let { return@withLock it.ids.toSet() }
        val headers = TraktAuthRepository.authorizedHeaders(profileId) ?: return@withLock null
        val ids = mutableSetOf<String>()
        for (type in listOf("movies", "shows")) {
            var page = 1
            while (page <= MAX_PAGES) {
                val response = httpRequestRaw(
                    method = "GET",
                    url = "$BASE_URL/sync/favorites/$type?page=$page&limit=$PAGE_LIMIT",
                    headers = mapOf("Accept" to "application/json") + headers,
                    body = "",
                )
                if (response.status !in 200..299) return@withLock null
                val items = json.decodeFromString<List<FavoriteEntry>>(response.body.ifBlank { "[]" })
                items.forEach { entry ->
                    entry.movie?.ids?.let { ids += it.keys("movie") }
                    entry.show?.ids?.let { ids += it.keys("show") }
                }
                val pageCount = response.headers.entries.firstOrNull { it.key.equals("X-Pagination-Page-Count", ignoreCase = true) }
                    ?.value?.trim()?.toIntOrNull() ?: 1
                if (items.isEmpty() || page >= pageCount) break
                page++
            }
        }
        cached = CachedFavorites(profileId, now, ids)
        ids.toSet()
    }

    private fun keysOf(media: TrackingMediaReference): List<String> = listOfNotNull(
        media.ids.trakt?.let { "trakt:$it" },
        media.ids.imdb?.takeIf { it.isNotBlank() }?.let { "imdb:$it" },
        media.ids.tmdb?.let { "tmdb:${if (media.kind == TrackingMediaKind.MOVIE) "movie" else "show"}:$it" },
    )

    private fun FavoriteIds.keys(kind: String): List<String> = listOfNotNull(
        trakt?.let { "trakt:$it" },
        imdb?.let { "imdb:$it" },
        tmdb?.let { "tmdb:$kind:$it" },
    )

    @Serializable
    private data class FavoriteIds(val trakt: Long? = null, val imdb: String? = null, val tmdb: Long? = null)

    @Serializable
    private data class FavoriteItem(val ids: FavoriteIds)

    @Serializable
    private data class FavoriteBody(val movies: List<FavoriteItem>? = null, val shows: List<FavoriteItem>? = null)

    @Serializable
    private data class FavoriteMedia(val ids: FavoriteIds? = null)

    @Serializable
    private data class FavoriteEntry(val movie: FavoriteMedia? = null, val show: FavoriteMedia? = null)
}

/** Trakt's page for a title, through its id search ("trakt.tv/search/imdb/tt…" redirects to the title). */
internal object TraktTitleLinks : com.nuvio.app.features.tracking.TrackingTitleLinkProvider {
    override val providerId = com.nuvio.app.features.tracking.TrackingProviderId.TRAKT
    override val label = "Trakt"

    override suspend fun url(meta: com.nuvio.app.features.details.MetaDetails): String? {
        val type = if (meta.type == "movie") "movie" else "show"
        meta.imdbIdOrNull()?.let { return "https://trakt.tv/search/imdb/$it?id_type=$type" }
        return meta.tmdbIdOrNull()?.let { "https://trakt.tv/search/tmdb/$it?id_type=$type" }
    }
}
