package com.nuvio.app.features.player

/*
 * Quick quality switching for the iOS mpv player. iOS uses the controlled reload path: the runtime
 * loads the new source in the same player and restores this checkpoint, as Windows and Linux do.
 * The buffered offscreen handover in docs/buffered-quality-handover.md stays macOS desktop only.
 * Mirrors NativePlayerController.qualityCheckpoint/restoreQuickQuality.
 */

private fun NuvioPlayerBridge.mpvSet(name: String, value: String): Boolean = runCommand(listOf("set", name, value))

internal fun NuvioPlayerBridge.mpvQualityCheckpoint(): PlayerQualityCheckpoint? {
    val positionSeconds = getProperty("time-pos").toDoubleOrNull() ?: return null
    return PlayerQualityCheckpoint(
        positionMs = (positionSeconds * 1000).toLong(),
        paused = getProperty("pause") == "yes",
        speed = getProperty("speed").toFloatOrNull() ?: getPlaybackSpeed(),
        volume = getProperty("volume").toFloatOrNull() ?: 100f,
        muted = getProperty("mute") == "yes",
        subtitleDelayMs = ((getProperty("sub-delay").toDoubleOrNull() ?: 0.0) * 1000).toInt(),
        tracks = readQualityTracks(::getProperty),
        durationMs = getDurationMs(),
        seekable = getProperty("seekable") == "yes",
    )
}

internal fun NuvioPlayerBridge.restoreMpvQuickQuality(checkpoint: PlayerQualityCheckpoint): Boolean {
    if (getProperty("time-pos").toDoubleOrNull() == null) return false
    val tracks = readQualityTracks(::getProperty)
    for (type in listOf("audio", "sub")) {
        val property = if (type == "audio") "aid" else "sid"
        val selected = checkpoint.tracks.firstOrNull { it.selected && it.type == type }
        if (selected == null) {
            mpvSet(property, "no")
            continue
        }
        if (selected.externalUrl != null && type == "sub") {
            runCommand(listOf("sub-add", selected.externalUrl, "select", selected.title, selected.language.orEmpty()))
            continue
        }
        val matching = qualityTrackMatch(selected, tracks) ?: tracks.firstOrNull {
            it.type == type && selected.language != null &&
                normalizeLanguageCode(it.language) == normalizeLanguageCode(selected.language)
        }
        // Missing language/role keeps the new stream's default; Sources remains the manual escape.
        matching?.let { mpvSet(property, it.id.toString()) }
    }
    setPlaybackSpeed(checkpoint.speed)
    mpvSet("volume", checkpoint.volume.toString())
    setMuted(checkpoint.muted)
    setSubtitleDelayMs(checkpoint.subtitleDelayMs)
    val durationMs = getProperty("duration").toDoubleOrNull()?.let { (it * 1000).toLong() }
    val target = checkpoint.positionMs.coerceAtMost((durationMs?.minus(1000) ?: checkpoint.positionMs).coerceAtLeast(0))
    if (checkpoint.seekable && target > 0) runCommand(listOf("seek", (target / 1000.0).toString(), "absolute+exact"))
    return true
}
