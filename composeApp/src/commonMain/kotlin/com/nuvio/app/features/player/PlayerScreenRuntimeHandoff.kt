package com.nuvio.app.features.player

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.handoff.TV_HANDOFF_TTL_MS
import com.nuvio.app.features.handoff.TvHandoffRequest
import com.nuvio.app.features.handoff.openTvHandoff
import com.nuvio.app.features.handoff.tvHandoffEditionHint
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import com.nuvio.app.core.network.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

internal fun PlayerScreenRuntime.continueOnTv() {
    if (isPartyControlLocked || tvHandoffTransferred) return
    cancelTvHandoff?.invoke()
    val openedKey = activePlaybackKey
    val openedAccount = AuthRepository.state.value as? AuthState.Authenticated ?: return
    if (openedAccount.isAnonymous) return
    val preparation = scope.launch {
        val accountId = try {
            withTimeoutOrNull(10_000) {
                SupabaseProvider.client.postgrest.rpc("get_sync_owner").decodeAs<String>()
                    .also { require(it.isNotBlank()) }
            } ?: error("Account lookup timed out")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            playerNotificationMessage = "Could not verify your Nuvio account. Check your connection and try again."
            playerNotificationToken += 1L
            return@launch
        }
        if (!isActive || activePlaybackKey != openedKey || AuthRepository.state.value != openedAccount) return@launch
        cancelTvHandoff = openTvHandoff(
            capture = capture@{ targetDeviceId, senderDeviceId ->
                if (!scope.isActive || playerController == null || activePlaybackKey != openedKey) return@capture null
                val account = AuthRepository.state.value as? AuthState.Authenticated ?: return@capture null
                if (account != openedAccount || account.isAnonymous || playbackSnapshot.durationMs <= 0 || playbackSnapshot.isEnded) return@capture null
                val now = WatchProgressClock.nowEpochMs()
                val position = currentPlaybackPositionMs().coerceAtLeast(0)
                val request = TvHandoffRequest(
                    requestId = "$senderDeviceId-$now", senderDeviceId = senderDeviceId, targetDeviceId = targetDeviceId,
                    accountId = accountId, profileId = profileId, contentType = if (isSeries) "series" else "movie",
                    contentId = parentMetaId, videoId = activeVideoId ?: buildPlaybackVideoId(
                        parentMetaId, activeSeasonNumber, activeEpisodeNumber, activeVideoId),
                    title = title, positionMs = position, durationMs = playbackSnapshot.durationMs,
                    createdAtEpochMs = now, expiresAtEpochMs = now + TV_HANDOFF_TTL_MS,
                    season = activeSeasonNumber, episode = activeEpisodeNumber,
                    editionHint = tvHandoffEditionHint("$activeStreamTitle ${activeStreamSubtitle.orEmpty()} ${activeTorrentFilename.orEmpty()}"),
                )
                if (request.validate(now) != null) return@capture null
                shouldPlay = false
                playerController?.pause()
                playbackSnapshot = playbackSnapshot.copy(positionMs = position, isPlaying = false)
                seekProgressSyncJob?.cancel()
                flushWatchProgress(TrackingScrobbleAction.PAUSE)
                request
            },
            onTransferred = {
                tvHandoffTransferred = true
                if (scope.isActive && activePlaybackKey == openedKey) scope.launch { this@continueOnTv.requestBack() }
            },
            onResumeHere = { setPlaybackPlaying(true) },
        )
    }
    cancelTvHandoff = { preparation.cancel() }
}
