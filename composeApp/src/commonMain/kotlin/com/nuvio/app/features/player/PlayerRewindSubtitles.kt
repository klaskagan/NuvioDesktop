package com.nuvio.app.features.player

import com.nuvio.app.isDesktop
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class PlayerRewindSubtitles {
    private var endpointMs: Long? = null
    private var seekConfirmationMs: Long? = null
    private var seekStarted = TimeSource.Monotonic.markNow()

    val isActive: Boolean get() = endpointMs != null

    fun rewind(fromMs: Long, targetMs: Long) {
        if (targetMs >= fromMs) return
        endpointMs = maxOf(endpointMs ?: fromMs, fromMs)
        // Require a position well into the backward jump. A queued pre-seek
        // snapshot can be slightly behind the interpolated starting position.
        seekConfirmationMs = fromMs - maxOf(1L, (fromMs - targetMs) / 2)
        seekStarted = TimeSource.Monotonic.markNow()
    }

    fun observe(positionMs: Long): Boolean {
        val endpoint = endpointMs ?: return false
        val confirmation = seekConfirmationMs
        if (confirmation != null) {
            if (positionMs <= confirmation) {
                seekConfirmationMs = null
            } else if (seekStarted.elapsedNow() >= 5.seconds) {
                return clear()
            }
            return false
        }
        return positionMs >= endpoint && clear()
    }

    fun clearAtForwardTarget(targetMs: Long): Boolean {
        val endpoint = endpointMs ?: return false
        return targetMs >= endpoint && clear()
    }

    fun clear(): Boolean {
        val wasActive = isActive
        endpointMs = null
        seekConfirmationMs = null
        return wasActive
    }
}

internal fun PlayerScreenRuntime.onUserSeekForRewindSubtitles(fromMs: Long, targetMs: Long) {
    if (!isDesktop) return
    if (targetMs >= fromMs) {
        if (targetMs > fromMs && rewindSubtitles.clearAtForwardTarget(targetMs)) restoreSubtitlesAfterRewind()
        return
    }
    if (!playerSettingsUiState.autoShowSubtitlesOnRewindEnabled || qualitySwitching ||
        selectedAddonSubtitleId != null ||
        useCustomSubtitles || hasBurnedInServerSubtitle
    ) return
    val controller = playerController ?: return
    if (!rewindSubtitles.isActive) {
        val tracks = controller.getSubtitleTracks()
        val selected = tracks.firstOrNull { it.isSelected }
            ?: tracks.firstOrNull { it.index == selectedSubtitleIndex }
        if (selected != null && (!selected.isForced || selected.isExternal)) return
        if (selected == null && selectedSubtitleIndex >= 0) return
        val embedded = tracks.filterNot { it.isExternal || it.isForced }
        val targets = resolvePreferredSubtitleLanguageTargets(
            preferredSubtitleLanguage = playerSettingsUiState.preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = playerSettingsUiState.secondaryPreferredSubtitleLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        )
        val preferred = findBestInternalSubtitleTrackIndex(embedded, targets)
        val track = embedded.getOrNull(preferred)
            ?: selected?.language?.let { language ->
                embedded.firstOrNull { subtitleTrackMatchesLanguage(it, language) }
            }
            ?: embedded.firstOrNull()
            ?: return
        // Keep the original selection in runtime state while the temporary track plays.
        selectedSubtitleIndex = selected?.index ?: -1
        controller.selectSubtitleTrack(track.index)
        subtitleTracks = tracks.map { it.copy(isSelected = it.index == track.index) }
        if (!desktopSubtitlesVisible) controller.setSubtitleVisible(true)
    }
    rewindSubtitles.rewind(fromMs, targetMs)
}

internal fun PlayerScreenRuntime.clearRewindSubtitles() {
    if (rewindSubtitles.clear()) restoreSubtitlesAfterRewind()
}

internal fun PlayerScreenRuntime.restoreSubtitlesAfterRewind() {
    playerController?.selectSubtitleTrack(selectedSubtitleIndex)
    if (!desktopSubtitlesVisible) playerController?.setSubtitleVisible(false)
    subtitleTracks = subtitleTracks.map { it.copy(isSelected = it.index == selectedSubtitleIndex) }
}
