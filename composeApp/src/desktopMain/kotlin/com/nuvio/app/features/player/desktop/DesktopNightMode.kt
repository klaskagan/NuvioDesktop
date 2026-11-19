package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.NIGHT_MODE_AUDIO_FILTER
import com.nuvio.app.features.player.NIGHT_MODE_AUDIO_FILTER_LABEL
import com.nuvio.app.features.player.nightModeAudioFilterInChain

internal const val DESKTOP_NIGHT_MODE_LABEL = NIGHT_MODE_AUDIO_FILTER_LABEL

// The filter itself lives in commonMain so iOS mpv applies the same chain.
internal const val DESKTOP_NIGHT_MODE_FILTER = NIGHT_MODE_AUDIO_FILTER

internal fun desktopNightModeInChain(filters: String): Boolean = nightModeAudioFilterInChain(filters)
