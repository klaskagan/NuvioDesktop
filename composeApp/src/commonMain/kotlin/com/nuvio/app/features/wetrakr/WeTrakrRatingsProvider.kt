package com.nuvio.app.features.wetrakr

import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingMutationResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRatingProvider
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * WeTrakr as a rating source (Settings → Tracking → Ratings). Ported from the fork's scoped rating
 * API to this branch's [TrackingRatingProvider]: a title or one episode, read from the synced
 * ratings snapshot and written through `/sync/ratings`.
 */
internal class WeTrakrRatingsProvider(
    private val sync: WeTrakrSyncRepository,
    private val api: WeTrakrApiClient,
    private val writes: WeTrakrTrackingWrites,
) : TrackingRatingProvider {
    override val providerId = TrackingProviderId.WETRAKR
    override val changes: Flow<Any?> get() = sync.projectionState

    override fun supports(media: TrackingMediaReference): Boolean = writes.resolve(media) != null

    override fun rating(media: TrackingMediaReference): Double? {
        val target = writes.resolve(media) ?: return null
        val episode = media.episode
        val type = when {
            target.isMovie -> WeTrakrMediaType.MOVIE
            episode != null -> WeTrakrMediaType.EPISODE
            else -> WeTrakrMediaType.SHOW
        }
        val aliases = target.ids.aliases().toSet()
        return sync.currentProjection().snapshot.ratings.firstOrNull { rating ->
            rating.type.equals(type, ignoreCase = true) &&
                rating.ids.aliases().any(aliases::contains) &&
                (episode == null || (rating.season == (episode.season ?: 1) && rating.number == episode.number))
        }?.rating?.takeIf { it > 0.0 }
    }

    override suspend fun rate(profileId: Int, media: TrackingMediaReference, rating: Int?): TrackingMutationResult {
        val scope = sync.currentScope().also {
            if (it.profileId != profileId) throw CancellationException("WeTrakr profile changed")
        }
        val target = writes.resolve(media) ?: return TrackingMutationResult(1, 1)
        val value = rating?.coerceIn(1, 10)
        val path = if (value == null) "/sync/ratings/remove" else "/sync/ratings"
        val response = api.post(scope, path, document(target, media, value))
        if (response.notFoundCount() > 0) return TrackingMutationResult(1, 1)
        val episode = media.episode
        sync.applyRating(
            scope,
            WeTrakrRatingEntry(
                type = when {
                    target.isMovie -> WeTrakrMediaType.MOVIE
                    episode != null -> WeTrakrMediaType.EPISODE
                    else -> WeTrakrMediaType.SHOW
                },
                ids = target.ids,
                season = episode?.let { it.season ?: 1 },
                number = episode?.number,
                rating = value?.toDouble() ?: 0.0,
            ),
            value?.toDouble(),
        )
        sync.refreshAsync(TrackingRefreshIntent.INVALIDATED)
        return TrackingMutationResult(1, 0)
    }

    private fun document(target: WeTrakrTarget, media: TrackingMediaReference, rating: Int?): JsonObject = buildJsonObject {
        val episode = media.episode
        val item = buildJsonObject {
            target.putIdentity(this)
            if (episode == null) {
                rating?.let { put("rating", it) }
            } else {
                put("seasons", buildJsonArray {
                    add(buildJsonObject {
                        put("number", episode.season ?: 1)
                        put("episodes", buildJsonArray {
                            add(buildJsonObject {
                                put("number", episode.number)
                                rating?.let { put("rating", it) }
                            })
                        })
                    })
                })
            }
        }
        put(if (target.isMovie && media.kind != TrackingMediaKind.SHOW) "movies" else "shows", buildJsonArray { add(item) })
    }
}
