package com.nuvio.app.features.details

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.MovieRecommendationRules
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.trakt.TraktRelatedRepository
import com.nuvio.app.features.watching.sync.TraktWatchedSyncAdapter
import kotlinx.coroutines.withTimeoutOrNull

/** What [UnwatchedRecommendations.load] gathered: the pool to filter, and what counts as this title or as watched. */
internal data class RecommendationPool(
    val items: List<MetaPreview>,
    val currentAliases: Set<String>,
    val extraWatchedKeys: Set<String>,
)

/**
 * The paging-and-fill behind "unwatched" recommendations, shared by the player's end-credits strip
 * and Details' More like this (docs/movie-recommendations.md). With Trakt connected it reads every
 * page of related titles in Trakt's order (stopping at the last, an empty or a repeated page), then
 * tops up from TMDB's recommendations for this title until [MovieRecommendationRules.LIMIT]
 * unwatched ones qualify. Trakt's watched history adds the TMDB IDs it knows. Without Trakt the
 * title's own recommendations are the pool.
 */
internal object UnwatchedRecommendations {
    suspend fun load(
        meta: MetaDetails,
        traktConnected: Boolean,
        watchedKeys: Set<String>,
        profileId: Int,
    ): RecommendationPool {
        val isSeries = meta.type == "series"
        val tmdbType = if (isSeries) "tv" else "movie"
        val previewType = if (isSeries) "series" else "movie"
        val tmdbId = withTimeoutOrNull(3_000L) {
            TmdbService.ensureTmdbId(meta.id, if (isSeries) "series" else "movie", fallbackImdbId = meta.imdbId)
        }
        val aliases = setOfNotNull(meta.imdbId, tmdbId?.let { "tmdb:$it" })
        val related = when {
            traktConnected -> TraktRelatedRepository.getRelatedForPlayback(meta)
                .ifEmpty { meta.moreLikeThis.takeIf { meta.moreLikeThisSource == MoreLikeThisSource.TRAKT }.orEmpty() }
            meta.moreLikeThisSource == MoreLikeThisSource.TRAKT -> withTimeoutOrNull(4_000L) {
                MetaDetailsRepository.fetch(meta.type, meta.id, cacheResult = false)
            }?.moreLikeThis.orEmpty()
            else -> meta.moreLikeThis
        }
        val primary = MetaDetailsRepository.playbackRecommendationArtwork(meta, related)
        val extraWatched = if (traktConnected) TraktWatchedSyncAdapter.pullExtraWatchedKeys(profileId) else emptySet()
        val items = if (traktConnected && tmdbId != null) {
            MovieRecommendationRules.appendFallbackPages(
                primary = primary,
                hasEnough = {
                    MovieRecommendationRules.candidates(it, meta.id, watchedKeys + extraWatched, aliases, previewType).size ==
                        MovieRecommendationRules.LIMIT
                },
            ) { page ->
                withTimeoutOrNull(3_000L) { TmdbMetadataService.recommendationPage(tmdbId, tmdbType, page) }
                    ?.let { (results, total) -> MetaDetailsRepository.playbackRecommendationArtwork(meta, results) to total }
            }
        } else {
            primary
        }
        return RecommendationPool(items, aliases, extraWatched)
    }

    /** Up to [MovieRecommendationRules.LIMIT] titles of [meta]'s type from [pool] that are not watched. */
    fun unwatched(pool: RecommendationPool, meta: MetaDetails, watchedKeys: Set<String>): List<MetaPreview> =
        MovieRecommendationRules.candidates(
            pool.items,
            meta.id,
            watchedKeys + pool.extraWatchedKeys,
            pool.currentAliases,
            if (meta.type == "series") "series" else "movie",
        )
}
