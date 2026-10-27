package com.nuvio.app.features.player

import com.nuvio.app.features.home.MetaPreview
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
            creditsStartMs >= 0 && creditsStartMs < durationMs && positionMs < creditsStartMs -> 0
        durationMs - positionMs <= 30_000L -> 2
        timing == MovieRecommendationTiming.INTRODB && creditsStartMs != null &&
            creditsStartMs >= 0 && creditsStartMs < durationMs -> 1
        durationMs - positionMs <= 240_000L -> 1
        else -> 0
    }

    fun candidates(items: List<MetaPreview>, currentId: String, watchedKeys: Set<String>): List<MetaPreview> =
        items.asSequence()
            .filterNot { it.id == currentId }
            .filterNot { WatchingState.isPosterWatched(watchedKeys, it) }
            .distinctBy { "${it.type}:${it.id}" }
            .take(10).toList()
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

internal val PlayerScreenRuntime.movieRecommendationCandidates: List<MetaPreview>
    get() {
        if (!parentMetaType.equals("movie", true) ||
            !(contentType ?: parentMetaType).equals("movie", true) ||
            args.onOpenMetaDetails == null || !playerSettingsUiState.movieRecommendationsEnabled ||
            playerMeta?.id != parentMetaId || playerMeta?.type != parentMetaType) return emptyList()
        return MovieRecommendationRules.candidates(playerMeta?.moreLikeThis.orEmpty(), parentMetaId, watchedUiState.watchedKeys)
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
        movieRecommendationDismissedStage = 0
        movieRecommendationDismissedSession = movieRecommendationSessionKey
    }
}
