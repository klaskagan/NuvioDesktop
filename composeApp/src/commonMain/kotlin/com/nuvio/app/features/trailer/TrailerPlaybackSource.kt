package com.nuvio.app.features.trailer

data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    internal val heroRenditions: List<HeroTrailerRendition> = emptyList(),
) {
    internal constructor(renditions: List<HeroTrailerRendition>) : this(
        renditions.first().videoUrl,
        renditions.first().audioUrl,
        renditions,
    )
}

data class HeroTrailerRendition internal constructor(
    val videoUrl: String,
    val audioUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val bandwidth: Long = 0,
    val pinnedPlaylist: String? = null,
    val codecs: String? = null,
)
