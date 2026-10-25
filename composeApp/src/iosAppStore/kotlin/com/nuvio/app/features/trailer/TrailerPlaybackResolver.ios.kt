package com.nuvio.app.features.trailer

actual object TrailerPlaybackResolver {
    actual suspend fun resolveFromYouTubeUrl(youtubeUrl: String, forceRefresh: Boolean, target: TrailerPlaybackTarget): TrailerPlaybackSource? = null

    actual suspend fun resolveSingleUrlFromYouTubeUrl(youtubeUrl: String): String? = null
}