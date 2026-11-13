package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamDebridCacheState

/** Resolution tiers, rather than cropped frame heights (e.g. 1280x688 is 720p). */
internal fun standardQualityHeight(width: Int, height: Int): Int? = when {
    width >= 7680 || height >= 4320 -> 4320
    width >= 3840 || height >= 2160 -> 2160
    width >= 2560 || height >= 1440 -> 1440
    width >= 1920 || height >= 1080 -> 1080
    width >= 1280 || height >= 720 -> 720
    height >= 576 -> 576
    width >= 854 || height >= 480 -> 480
    width >= 640 || height >= 360 -> 360
    width > 0 || height > 0 -> 240
    else -> null
}

internal fun reportedStreamQuality(stream: StreamItem): Int? {
    val text = (listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints.filename) + stream.badges.map { it.name }).joinToString(" ")
    // Prefer the stream's leading quality label to resolution-like numbers elsewhere in a filename.
    val explicit = Regex("(?i)(?<![0-9])(4320|2160|1440|1080|720|576|480|360|240)[pi]\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
    if (explicit != null) return explicit
    if (Regex("(?i)\\b8k\\b").containsMatchIn(text)) return 4320
    if (Regex("(?i)\\b(4k|uhd)\\b").containsMatchIn(text)) return 2160
    val dimensions = Regex("(?i)\\b(\\d{3,4})x(\\d{3,4})\\b").find(text)
    return dimensions?.let { standardQualityHeight(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
}

/** No IO, probing, or language/timeline gates. Keep the original source order within each tier. */
internal fun quickQualityChoices(streams: List<StreamItem>, current: StreamItem? = null, currentHeight: Int? = null): List<PlayerQualityChoice> =
    (streams + listOfNotNull(current?.takeIf { it.playableDirectUrl != null })).mapNotNull { stream -> (if (stream == current) currentHeight ?: reportedStreamQuality(stream) else reportedStreamQuality(stream))?.let { it to stream } }
        .groupBy({ it.first }, { it.second })
        .toList().sortedByDescending { it.first }
        .mapIndexed { index, (height, candidates) ->
            val stream = current?.takeIf { it.playableDirectUrl != null && (currentHeight ?: reportedStreamQuality(it)) == height }
                ?: candidates.minBy { quickQualityAvailability(it) }
            PlayerQualityChoice(index, height, stream.playableDirectUrl.orEmpty(),
                sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request), stream,
                disabledReason = if (stream != current && quickQualityAvailability(stream) == 2)
                    "Requires download — choose a release in Sources" else null)
        }

/** Addon-provided ready/download markers do not require native debrid credentials. */
internal fun quickQualityAvailability(stream: StreamItem): Int {
    val name = stream.name.orEmpty()
    return when {
        stream.debridCacheStatus?.state == StreamDebridCacheState.CACHED ||
            stream.clientResolve?.isCached == true || Regex("\\[[^]\\r\\n]+\\+]").containsMatchIn(name) -> 0
        stream.debridCacheStatus?.state == StreamDebridCacheState.NOT_CACHED ||
            Regex("(?i)\\[[^]\\r\\n]*download[^]\\r\\n]*]").containsMatchIn(name) -> 2
        else -> 1
    }
}
