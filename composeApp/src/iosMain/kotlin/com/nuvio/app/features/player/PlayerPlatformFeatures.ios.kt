package com.nuvio.app.features.player

internal actual object PlayerPlatformFeatures {
    actual val rewindSubtitles: Boolean = true
    // No iOS frame grabber yet; the desktop one lives in SeekPreviewThumbnailer.
    actual val seekPreview: Boolean = false
    actual val movieRecommendations: Boolean = true
    actual val quickQuality: Boolean = true
}
