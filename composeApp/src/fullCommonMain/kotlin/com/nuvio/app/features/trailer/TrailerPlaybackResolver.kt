package com.nuvio.app.features.trailer

import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

actual object TrailerPlaybackResolver {
    private val extractor by lazy { InAppYouTubeExtractor() }
    private val cacheMutex = Mutex()
    private var playbackCache = emptyMap<String, CachedPlaybackSource>()

    actual suspend fun resolveFromYouTubeUrl(youtubeUrl: String, forceRefresh: Boolean, target: TrailerPlaybackTarget): TrailerPlaybackSource? {
        if (youtubeUrl.isBlank()) return null
        val cacheKey = "$target:$youtubeUrl"
        cacheMutex.lock()
        try {
            playbackCache = playbackCache.filterValues { entry ->
                entry.cachedAt.elapsedNow() < PlaybackCacheTtl
            }
            if (forceRefresh) playbackCache = playbackCache - cacheKey
            playbackCache[cacheKey]?.let { cached ->
                TrailerExtractionPlatform.diagnostic(
                    "cache hit ageMs=${cached.cachedAt.elapsedNow().inWholeMilliseconds}",
                )
                return cached.source
            }
        } finally {
            cacheMutex.unlock()
        }

        val source = if (target == TrailerPlaybackTarget.FRAME_PREVIEW) {
            extractor.extractPreviewPlaybackSource(youtubeUrl)
        } else extractor.extractPlaybackSource(youtubeUrl)
        if (source != null) {
            cacheMutex.lock()
            try {
                playbackCache = playbackCache + (
                    cacheKey to CachedPlaybackSource(
                        source = source,
                        cachedAt = TimeSource.Monotonic.markNow(),
                    )
                )
            } finally {
                cacheMutex.unlock()
            }
        }
        return source
    }

    actual suspend fun resolveSingleUrlFromYouTubeUrl(youtubeUrl: String): String? {
        if (youtubeUrl.isBlank()) return null
        return extractor.extractSingleUrl(youtubeUrl)
    }

    private data class CachedPlaybackSource(
        val source: TrailerPlaybackSource,
        val cachedAt: TimeMark,
    )
}

private val PlaybackCacheTtl = 10.minutes
