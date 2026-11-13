package com.nuvio.app.features.player.desktop

internal const val DESKTOP_NIGHT_MODE_LABEL = "@nuvio_night"

// Match Mobile Enhanced's mpv stereo downmix, compressor and peak limiter.
internal const val DESKTOP_NIGHT_MODE_FILTER =
    "$DESKTOP_NIGHT_MODE_LABEL:lavfi=[" +
        "aresample=ochl=stereo:clev=1.0:slev=0.707," +
        "acompressor=threshold=0.063:ratio=6:attack=5:release=300:makeup=3.98:detection=rms," +
        "alimiter=limit=0.94:attack=1:release=150:level=0]"

private val nightModeFilterEntry = Regex("(?:^|,)\\s*$DESKTOP_NIGHT_MODE_LABEL:")

internal fun desktopNightModeInChain(filters: String): Boolean = nightModeFilterEntry.containsMatchIn(filters)
