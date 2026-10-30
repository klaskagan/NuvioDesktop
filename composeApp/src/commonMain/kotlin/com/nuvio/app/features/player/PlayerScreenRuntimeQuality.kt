package com.nuvio.app.features.player

import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.streams.isSelectableForPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun PlayerScreenRuntime.cancelPlayerQuality() {
    qualityGeneration++
    val pending = qualityJob
    pending?.cancel()
    qualityJob = null
    qualityChoices = emptyList()
    qualityBusy = false
    qualityStatus = ""
    qualityRequestedHeight = 0
    qualitySwitching = false
    qualityReplacing = false
    qualityLoadError = null
}

internal fun PlayerScreenRuntime.openPlayerQualityMenu() {
    if (qualityChoices.isEmpty()) discoverPlayerQuality()
}

internal fun PlayerScreenRuntime.discoverPlayerQuality() {
    if (qualitySwitching) return
    if (activeTorrentInfoHash != null || activeSourceAudioUrl != null) {
        qualityChoices = emptyList()
        qualityStatus = "No quick qualities available. Choose a stream in Sources."
        return
    }
    val decodedHeight = standardQualityHeight(playbackSnapshot.videoWidth, playbackSnapshot.videoHeight)
    val current = activeStream?.copy(url = activeSourceUrl)
    val prefix = "${contentType ?: parentMetaType}::$activeVideoId::$activeSeasonNumber::$activeEpisodeNumber::"
    if (sourceStreamsState.requestToken?.startsWith(prefix) != true) {
        qualityChoices = quickQualityChoices(emptyList(), current, decodedHeight)
        qualityStatus = if (qualityChoices.isEmpty()) "Sources are loading…" else ""
        return
    }
    val debridEnabled = DebridSettingsRepository.uiState.value.canResolvePlayableLinks
    val streams = sourceStreamsState.groups.flatMap { it.streams }.filter {
        it.isSelectableForPlayback(debridEnabled) && !it.shouldOpenExternally &&
            (it.playableDirectUrl != null || it.serverTarget != null ||
                com.nuvio.app.features.debrid.DirectDebridPlaybackResolver.shouldResolveToPlayableStream(it))
    }
    qualityChoices = quickQualityChoices(streams, current, decodedHeight)
    qualityStatus = if (qualityChoices.isEmpty()) "No alternative resolutions available" else ""
}

private data class RuntimeQualityCheckpoint(
    val playback: PlayerQualityCheckpoint,
    val url: String,
    val audio: String?,
    val headers: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val streamType: String?,
    val requestedHeight: Int,
    val videoId: String?,
)

internal fun PlayerScreenRuntime.selectPlayerQuality(height: Int) {
    if (com.nuvio.app.features.party.PartyRepository.state.value.party != null) return
    val choice = qualityChoices.firstOrNull { it.height == height && it.disabledReason == null } ?: return
    if (choice.url.isNotBlank() && choice.url == activeSourceUrl) { qualityRequestedHeight = height; return }
    val generation = ++qualityGeneration
    val previousJob = qualityJob
    previousJob?.cancel()
    qualityJob = scope.launch {
        previousJob?.join()
        if (generation != qualityGeneration) return@launch
        qualitySwitching = true
        qualityLoadingHeight = height
        qualityBusy = true
        qualityStatus = "Switching to ${height}p…"
        var preparedChoice = choice
        var staged: BufferedPlayerQuality? = null
        var handoverCheckpoint: PlayerQualityCheckpoint? = null
        val adapter = object : QualitySwitchPlayback<RuntimeQualityCheckpoint> {
            override suspend fun capture(): RuntimeQualityCheckpoint {
                val playback = playerController?.qualityCheckpoint() ?: error("Player unavailable")
                return RuntimeQualityCheckpoint(playback, activeSourceUrl, activeSourceAudioUrl, activeSourceHeaders,
                    activeSourceResponseHeaders, activeStreamType, qualityRequestedHeight, activeVideoId).also { capturedPaused = playback.paused }
            }
            override suspend fun loadAndRestore(candidate: PlayerQualityChoice, checkpoint: RuntimeQualityCheckpoint) {
                var stream = candidate.stream
                if (stream != null && com.nuvio.app.features.debrid.DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) {
                    val resolved = com.nuvio.app.features.debrid.DirectDebridPlaybackResolver.resolveToPlayableStream(stream, activeSeasonNumber, activeEpisodeNumber)
                    stream = (resolved as? com.nuvio.app.features.debrid.DirectDebridPlayableResult.Success)?.stream ?: error("Source resolution failed")
                }
                if (stream?.serverTarget != null) stream = com.nuvio.app.features.servers.ServerPlayback.prepare(stream)
                preparedChoice = if (stream != null) candidate.copy(url = stream.playableDirectUrl ?: error("Source unavailable"),
                    headers = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request), stream = stream) else candidate
                check(preparedChoice.url.isNotBlank())
                qualityStatus = "Loading ${if (height == 2160) "4K" else "${height}p"} in the background…"
                staged = playerController?.prepareBufferedQuality(preparedChoice, checkpoint.playback)
                if (generation != qualityGeneration || activeVideoId != checkpoint.videoId || qualityDisposed) throw CancellationException()
                flushWatchProgress(if (staged == null) com.nuvio.app.features.tracking.TrackingScrobbleAction.PAUSE else com.nuvio.app.features.tracking.TrackingScrobbleAction.START)
                qualityReplacing = true
                val restored = staged?.promote() ?: checkpoint.playback
                handoverCheckpoint = restored
                capturedPaused = restored.paused
                shouldPlay = if (staged == null) false else !capturedPaused
                qualityLoadError = null
                errorMessage = null
                activeInitialPositionMs = restored.positionMs
                activeInitialProgressFraction = null
                initialSeekApplied = false
                activeSourceAudioUrl = null
                activeSourceHeaders = preparedChoice.headers
                activeSourceResponseHeaders = emptyMap()
                activeStreamType = preparedChoice.stream?.streamType ?: checkpoint.streamType
                activeSourceUrl = preparedChoice.url
            }
            override suspend fun verify(candidate: PlayerQualityChoice, checkpoint: RuntimeQualityCheckpoint) {
                if (staged == null) awaitQualitySource(preparedChoice.url, checkpoint.playback)
                else {
                    // Keep source-reset guards until Compose has adopted the existing renderer.
                    delay(16)
                    while (playerControllerSourceUrl != preparedChoice.url || playerController == null ||
                        playbackSnapshotKey != activePlaybackKey || playbackSnapshot.isLoading) {
                        if (qualityLoadError != null) error("Buffered player adoption failed")
                        delay(16)
                    }
                    initialSeekApplied = true
                }
                if (generation != qualityGeneration || activeVideoId != checkpoint.videoId) throw CancellationException()
            }
            override suspend fun rollback(checkpoint: RuntimeQualityCheckpoint) {
                // Navigation or explicit source selection owns any newer source.
                if (qualityDisposed || activeVideoId != checkpoint.videoId || activeSourceUrl != preparedChoice.url) return
                activeSourceAudioUrl = checkpoint.audio
                activeSourceHeaders = checkpoint.headers
                activeSourceResponseHeaders = checkpoint.responseHeaders
                activeStreamType = checkpoint.streamType
                activeInitialPositionMs = (handoverCheckpoint ?: checkpoint.playback).positionMs
                initialSeekApplied = false
                qualityLoadError = null
                errorMessage = null
                activeSourceUrl = checkpoint.url
                qualityRequestedHeight = checkpoint.requestedHeight
                awaitQualitySource(checkpoint.url, handoverCheckpoint ?: checkpoint.playback)
                preserveQualitySelectionFlags()
                shouldPlay = !(handoverCheckpoint ?: checkpoint.playback).paused
            }
            private var capturedPaused = true
            override suspend fun commit(candidate: PlayerQualityChoice) {
                qualityRequestedHeight = height
                preparedChoice.stream?.let { stream ->
                    activeStream = stream
                    activeSourceIdentityKey = stream.playerSourceIdentityKey()
                    activeSourceFilename = stream.behaviorHints.filename
                    activeStreamTitle = stream.streamLabel
                    activeStreamSubtitle = stream.streamSubtitle
                    activeStreamFilename = streamPlaybackFilename(stream, preparedChoice.url)
                    activeProviderName = stream.addonName
                    activeProviderAddonId = stream.addonId
                    currentStreamBingeGroup = stream.behaviorHints.bingeGroup
                    currentStreamFilename = stream.behaviorHints.filename
                    currentStreamInfoHash = stream.infoHash
                    externalSubtitles = stream.externalSubtitles
                }
                preserveQualitySelectionFlags()
                shouldPlay = !capturedPaused
            }
        }
        try {
            when (val result = QualitySwitchTransaction(adapter).switch(choice)) {
                QualitySwitchResult.Committed -> qualityStatus = ""
                is QualitySwitchResult.Rejected -> qualityStatus = result.reason
                is QualitySwitchResult.Failed -> {
                    qualityStatus = if (!qualityReplacing)
                        "Could not buffer the new quality. Current stream kept."
                    else if (result.rollbackFailure == null)
                        "Quality switch failed. Previous source restored; choose a resolution to retry."
                    else "Could not restore playback. Use Sources or retry playback."
                    com.nuvio.app.core.ui.NuvioToastController.show(qualityStatus)
                }
            }
        } finally {
            // Cleanup must not keep a failed selection breathing while native disposal waits.
            if (generation == qualityGeneration) { qualityBusy = false; qualitySwitching = false; qualityReplacing = false }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { staged?.cancel() }
            if (preparedChoice.stream?.serverTarget != null && activeSourceUrl != preparedChoice.url)
                com.nuvio.app.features.servers.ServerPlayback.stop(preparedChoice.url)
        }
    }
}

private fun PlayerScreenRuntime.preserveQualitySelectionFlags() {
    preferredAudioSelectionApplied = true
    preferredSubtitleSelectionApplied = true
    trackPreferenceRestoreApplied = true
    isUserExplicitAudioSelection = true
    isUserExplicitSubtitleSelection = true
    refreshTracks()
}

private suspend fun PlayerScreenRuntime.awaitQualitySource(url: String, checkpoint: PlayerQualityCheckpoint) {
    // Allow Compose to dispose/recreate the old source before observing readiness.
    delay(100)
    while (true) {
        if (activeSourceUrl != url) throw CancellationException()
        if (qualityLoadError != null) error("Native load failed")
        val controller = playerController
        if (playerControllerSourceUrl == url && controller != null && !playbackSnapshot.isLoading) {
            if (controller.restoreQuickQuality(checkpoint)) return
        }
        delay(100)
    }
}
