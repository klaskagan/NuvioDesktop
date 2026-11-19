package com.nuvio.app.features.player

/**
 * Player features that depend on platform engine or UI support. Gate settings rows and runtime
 * behavior on these flags instead of `isDesktop`, so a platform turns a feature on by
 * implementing it and flipping its flag.
 */
internal expect object PlayerPlatformFeatures {
    /** Temporarily shows subtitles after a manual rewind (PlayerRewindSubtitles). */
    val rewindSubtitles: Boolean

    /** Seek bar thumbnails; needs a platform frame grabber. */
    val seekPreview: Boolean

    /** "More Like This" shelf near the end of a movie. */
    val movieRecommendations: Boolean

    /** Quick quality picker that reloads a cached alternative source (controlled reload). */
    val quickQuality: Boolean
}
