package com.nuvio.app.features.player

internal actual object PlayerPlatformFeatures {
    // Android settings storage does not persist this yet.
    actual val rewindSubtitles: Boolean = false
    actual val seekPreview: Boolean = false
    actual val movieRecommendations: Boolean = false
    // The Android engines do not implement qualityCheckpoint/restoreQuickQuality.
    actual val quickQuality: Boolean = false
}
