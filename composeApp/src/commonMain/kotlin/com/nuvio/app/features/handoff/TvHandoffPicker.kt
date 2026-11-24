package com.nuvio.app.features.handoff

import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.player.PlayerControlTvDevice
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The paired TVs and the state of an open handoff, for in-app pickers: the desktop player's
 * controls and Details' On TV popover. Display names only; pairing credentials never leave the picker.
 */
internal data class TvHandoffPlaybackView(
    val devices: List<PlayerControlTvDevice>,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val canResume: Boolean = false,
    val status: String = "",
)

/** Whether this platform can send playback to a paired TV (desktop only today). */
internal expect val tvHandoffSupported: Boolean

/** The paired TVs, plus the open handoff's state once [openTvHandoff] has prepared one. */
internal expect fun tvHandoffPlaybackView(): TvHandoffPlaybackView

/** Sends the prepared handoff to the paired TV at [index]. */
internal expect fun selectTvHandoffDevice(index: Int)

/** Calls [changed] whenever the handoff state changes, until [stopObservingTvHandoffPlayback] with the same owner. */
internal expect fun observeTvHandoffPlayback(owner: Any, changed: () -> Unit)

internal expect fun stopObservingTvHandoffPlayback(owner: Any)

/**
 * The Nuvio account that owns synced data, which the TV checks against its own sign-in. Null when
 * the lookup fails or times out.
 */
internal suspend fun resolveTvHandoffAccountId(): String? = try {
    withTimeoutOrNull(10_000) {
        SupabaseProvider.client.postgrest.rpc("get_sync_owner").decodeAs<String>().takeIf { it.isNotBlank() }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/**
 * A handoff request for a title that is not playing yet (Details' On TV): it resumes from the saved
 * position, or starts from the beginning with the runtime as its duration. Null when neither a saved
 * duration nor a runtime is known, or the request would be invalid.
 */
internal fun detailsTvHandoffRequest(
    senderDeviceId: String,
    targetDeviceId: String,
    accountId: String,
    profileId: Int,
    isSeries: Boolean,
    contentId: String,
    videoId: String,
    title: String,
    season: Int?,
    episode: Int?,
    savedPositionMs: Long?,
    savedDurationMs: Long?,
    runtimeMinutes: Int?,
    nowEpochMs: Long,
): TvHandoffRequest? {
    val duration = savedDurationMs?.takeIf { it > 0 } ?: runtimeMinutes?.takeIf { it > 0 }?.let { it * 60_000L } ?: return null
    val position = (savedPositionMs ?: 0L).coerceIn(0L, duration - 1)
    val request = TvHandoffRequest(
        requestId = "$senderDeviceId-$nowEpochMs",
        senderDeviceId = senderDeviceId,
        targetDeviceId = targetDeviceId,
        accountId = accountId,
        profileId = profileId,
        contentType = if (isSeries) "series" else "movie",
        contentId = contentId,
        videoId = videoId,
        title = title,
        positionMs = position,
        durationMs = duration,
        createdAtEpochMs = nowEpochMs,
        expiresAtEpochMs = nowEpochMs + TV_HANDOFF_TTL_MS,
        season = season.takeIf { isSeries },
        episode = episode.takeIf { isSeries },
    )
    return request.takeIf { it.validate(nowEpochMs) == null }
}
