package com.nuvio.app.features.player

import com.nuvio.app.features.details.MoreLikeThisSource
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktConnectionMode
import com.nuvio.app.features.watching.application.WatchingState

enum class MovieRecommendationTiming { INTRODB, FINAL_FOUR_MINUTES }

internal object MovieRecommendationRules {
    fun stage(
        positionMs: Long, durationMs: Long, isEnded: Boolean,
        timing: MovieRecommendationTiming = MovieRecommendationTiming.FINAL_FOUR_MINUTES,
        creditsStartMs: Long? = null,
    ): Int = when {
        isEnded -> 3
        durationMs <= 0 -> 0
        timing == MovieRecommendationTiming.INTRODB && creditsStartMs != null &&
            creditsStartMs >= 0 && creditsStartMs < durationMs && positionMs - creditsStartMs < 5_000L -> 0
        durationMs - positionMs <= 30_000L -> 2
        timing == MovieRecommendationTiming.INTRODB && creditsStartMs != null &&
            creditsStartMs >= 0 && creditsStartMs < durationMs -> 1
        durationMs - positionMs <= 240_000L -> 1
        else -> 0
    }

    const val LIMIT = 20

    fun candidates(
        items: List<MetaPreview>,
        currentId: String,
        watchedKeys: Set<String>,
        currentAliases: Set<String> = emptySet(),
    ): List<MetaPreview> {
        val seen = (currentAliases + currentId).toMutableSet()
        return items.asSequence()
            .filter { it.type == "movie" }
            .filter { item ->
                val ids = item.alternateIds + item.id
                val distinct = ids.none(seen::contains)
                seen.addAll(ids)
                distinct
            }
            .filter { item ->
                (item.alternateIds + item.id).none { id ->
                    WatchingState.isPosterWatched(watchedKeys, item.copy(id = id))
                }
            }
            .take(LIMIT).toList()
    }
    suspend fun appendFallbackPages(
        primary: List<MetaPreview>,
        hasEnough: (List<MetaPreview>) -> Boolean,
        loadPage: suspend (Int) -> Pair<List<MetaPreview>, Int?>?,
    ): List<MetaPreview> {
        val items = primary.toMutableList()
        val seenFallbackIds = mutableSetOf<String>()
        var page = 1
        while (!hasEnough(items)) {
            val (results, totalPages) = loadPage(page) ?: break
            val newItems = results.filter { seenFallbackIds.add(it.id) }
            if (newItems.isEmpty()) break
            items += newItems
            if (totalPages != null && page >= totalPages) break
            page++
        }
        return items
    }

}

internal val PlayerScreenRuntime.movieRecommendationSessionKey: String
    get() {
        val identity = "${args.launchId}:${profileId}:${parentMetaType}:${parentMetaId}:$activePlaybackKey"
        if (identity != movieRecommendationSessionIdentity) {
            movieRecommendationSessionIdentity = identity
            movieRecommendationSessionGeneration++
        }
        // Do not expose source URLs or credentials through WebView events and logs.
        return "$movieRecommendationRuntimeToken:$movieRecommendationSessionGeneration"
    }

internal val PlayerScreenRuntime.movieRecommendationLookupKey: String
    get() = "$movieRecommendationSessionKey:${playerSettingsUiState.movieRecommendationTiming}:" +
        "${playerSettingsUiState.movieRecommendationsEnabled}:${args.onOpenMetaDetails != null}:" +
        "${contentType ?: parentMetaType}:${playbackSnapshot.durationMs / 1000}"

internal val PlayerScreenRuntime.movieRecommendationPoolKey: Pair<String, Boolean>
    get() = movieRecommendationSessionKey to
        (TraktAuthRepository.uiState.value.mode == TraktConnectionMode.CONNECTED)

internal data class MovieRecommendationPool(
    val key: Pair<String, Boolean>,
    val items: List<MetaPreview>,
    val currentAliases: Set<String>,
    val extraWatchedKeys: Set<String>,
)

internal val PlayerScreenRuntime.movieRecommendationCandidates: List<MetaPreview>
    get() {
        if (!parentMetaType.equals("movie", true) ||
            !(contentType ?: parentMetaType).equals("movie", true) ||
            args.onOpenMetaDetails == null || !playerSettingsUiState.movieRecommendationsEnabled) return emptyList()
        val meta = playerMeta?.takeIf { it.id == parentMetaId && it.type == parentMetaType } ?: return emptyList()
        val key = movieRecommendationPoolKey
        val pool = movieRecommendationPool?.takeIf { it.key == key }
        if (pool == null && (key.second || movieRecommendationPoolLoadingKey == key)) return emptyList()
        return MovieRecommendationRules.candidates(
            pool?.items ?: meta.moreLikeThis,
            parentMetaId,
            watchedUiState.watchedKeys + pool?.extraWatchedKeys.orEmpty(),
            pool?.currentAliases ?: setOfNotNull(meta.imdbId),
        )
    }

internal val PlayerScreenRuntime.movieRecommendationStage: Int
    get() = if (movieRecommendationCandidates.isEmpty() || playbackSnapshotKey != activePlaybackKey) 0
        else if (playerSettingsUiState.movieRecommendationTiming == MovieRecommendationTiming.INTRODB &&
            (movieRecommendationCreditsRequest != movieRecommendationLookupKey || !movieRecommendationCreditsLoaded) &&
            !playbackSnapshot.isEnded) 0
        else MovieRecommendationRules.stage(
            playbackSnapshot.positionMs, playbackSnapshot.durationMs, playbackSnapshot.isEnded,
            playerSettingsUiState.movieRecommendationTiming, movieRecommendationCreditsStartMs,
        )

internal fun PlayerScreenRuntime.movieRecommendationActionKey(id: String, type: String): String =
    "$movieRecommendationSessionKey:$type:$id"

internal fun PlayerScreenRuntime.syncMovieRecommendationDismissal() {
    if (movieRecommendationSessionKey != movieRecommendationDismissedSession || movieRecommendationStage == 0) {
        movieRecommendationsDismissed = false
        movieRecommendationDismissedSession = movieRecommendationSessionKey
    }
}
