package com.nuvio.app.features.player

/** mpv audio filter label for Night Mode, shared by every mpv-based player. */
internal const val NIGHT_MODE_AUDIO_FILTER_LABEL = "@nuvio_night"

// Stereo downmix, compressor and peak limiter, matching the mobile "Enhanced" mpv chain.
internal const val NIGHT_MODE_AUDIO_FILTER =
    "$NIGHT_MODE_AUDIO_FILTER_LABEL:lavfi=[" +
        "aresample=ochl=stereo:clev=1.0:slev=0.707," +
        "acompressor=threshold=0.063:ratio=6:attack=5:release=300:makeup=3.98:detection=rms," +
        "alimiter=limit=0.94:attack=1:release=150:level=0]"

private val nightModeFilterEntry = Regex("(?:^|,)\\s*$NIGHT_MODE_AUDIO_FILTER_LABEL:")

/** Whether an mpv `af` property value contains the Night Mode filter. */
internal fun nightModeAudioFilterInChain(filters: String): Boolean = nightModeFilterEntry.containsMatchIn(filters)

/** Toggles Night Mode on the active engine; shows [errorMessage] when the engine refuses. */
internal fun PlayerScreenRuntime.toggleNightMode(enabled: Boolean, errorMessage: String) {
    val controller = playerController ?: return
    val applied = controller.setNightModeEnabled(enabled)
    nightModeActive = controller.isNightModeActive()
    if (!applied) showGestureMessage(errorMessage)
}
