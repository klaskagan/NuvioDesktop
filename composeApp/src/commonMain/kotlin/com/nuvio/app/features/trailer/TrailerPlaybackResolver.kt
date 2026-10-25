package com.nuvio.app.features.trailer

/** Renderer requirements must be part of stream selection and the cache identity. */
enum class TrailerPlaybackTarget { NATIVE_PLAYER, FRAME_PREVIEW }

expect object TrailerPlaybackResolver {
    suspend fun resolveFromYouTubeUrl(youtubeUrl: String, forceRefresh: Boolean = false, target: TrailerPlaybackTarget = TrailerPlaybackTarget.NATIVE_PLAYER): TrailerPlaybackSource?

    /** One URL with video and audio together, for playing a YouTube video as a stream. */
    suspend fun resolveSingleUrlFromYouTubeUrl(youtubeUrl: String): String?
}
