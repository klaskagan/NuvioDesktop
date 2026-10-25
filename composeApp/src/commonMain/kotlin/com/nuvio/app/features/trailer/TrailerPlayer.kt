package com.nuvio.app.features.trailer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.player.EnterImmersivePlayerMode
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerControlsAction
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.isDesktop
import com.nuvio.app.features.player.PlayerResizeMode
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.trailer_unable_to_play
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import nuvio.composeapp.generated.resources.trailer_playback_timeout

@Composable
internal fun TrailerPlayer(
    source: TrailerPlaybackSource?,
    state: TrailerPlaybackState,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = "",
    onExitFullscreen: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null,
    onEscapeFullscreen: (() -> Unit)? = null,
) {
    val error = errorMessage ?: state.error
    EnterImmersivePlayerMode(keepScreenAwake = error == null && state.playWhenReady && !state.snapshot.isEnded)
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (source != null && !isLoading && error == null) {
            key(state, source) {
                TrailerPlaybackSurface(
                    source = source,
                    state = state,
                    resizeMode = state.resizeMode,
                    title = title,
                    fullscreen = onExitFullscreen != null,
                    onBack = onBack,
                    onToggleFullscreen = onToggleFullscreen,
                    onEscapeFullscreen = onEscapeFullscreen,
                )
            }
        }
        when {
            error == null && (isLoading || source != null && state.snapshot.isLoading) -> {
                NuvioLoadingIndicator()
            }
            error != null -> Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.trailer_unable_to_play),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                }
            }
        }
        if (!isDesktop || source == null || isLoading || error != null) TrailerPlayerControls(
            state = state,
            canControlPlayback = source != null && !isLoading && error == null,
            title = title,
            onExitFullscreen = onExitFullscreen,
        )
    }
}

@Composable
private fun TrailerPlaybackSurface(
    source: TrailerPlaybackSource,
    state: TrailerPlaybackState,
    resizeMode: PlayerResizeMode,
    title: String,
    fullscreen: Boolean,
    onBack: (() -> Unit)?,
    onToggleFullscreen: (() -> Unit)?,
    onEscapeFullscreen: (() -> Unit)?,
) {
    val presentationId = remember { state.presentationId }
    val resumePositionMs = remember { state.snapshot.positionMs }
    var restoringPosition by remember { mutableStateOf(resumePositionMs > 0L && !isDesktop) }
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember { mutableStateOf(PlayerPlaybackSnapshot()) }
    var tracksRestored by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }

    LaunchedEffect(state, source, presentationId) {
        if (!state.awaitStartup() && active && presentationId == state.presentationId) {
            controller?.pause()
            state.error = getString(Res.string.trailer_playback_timeout)
        }
    }

    LaunchedEffect(controller, snapshot.isLoading, snapshot.durationMs, restoringPosition) {
        val player = controller
        if (restoringPosition && player != null && !snapshot.isLoading && snapshot.durationMs > 0L &&
            presentationId == state.presentationId
        ) {
            player.seekTo(resumePositionMs.coerceAtMost(snapshot.durationMs))
            restoringPosition = false
        }
    }
    DisposableEffect(state) {
        onDispose {
            active = false
            if (state.controller === controller) state.controller = null
        }
    }
    PlatformPlayerSurface(
        sourceUrl = source.videoUrl,
        sourceAudioUrl = source.audioUrl,
        useYoutubeChunkedPlayback = true,
        modifier = Modifier.fillMaxSize(),
        playWhenReady = state.playWhenReady && !restoringPosition && !state.snapshot.isEnded &&
            presentationId == state.presentationId,
        resizeMode = resizeMode,
        initialPositionMs = if (isDesktop) resumePositionMs else null,
        useNativeController = isDesktop,
        playerControlsState = PlayerControlsState(
            controlsVisible = state.controlsVisible,
            presentationFullscreen = fullscreen, title = title, pauseOverlayEnabled = false,
            isPlaying = state.snapshot.isPlaying, isLoading = state.snapshot.isLoading,
            durationMs = state.snapshot.durationMs, positionMs = state.snapshot.positionMs,
            bufferedPositionMs = state.snapshot.bufferedPositionMs,
            playbackSpeedLabel = "${state.playbackSpeed}x", resizeModeLabel = resizeMode.name,
        ),
        onPlayerControlsAction = { action ->
            when (action) {
                PlayerControlsAction.ToggleChrome -> { state.controlsVisible = !state.controlsVisible; true }
                PlayerControlsAction.Back -> onBack.invokeIfPresent()
                PlayerControlsAction.TogglePlayback, PlayerControlsAction.KeyboardTogglePlayback -> { state.togglePlayback(); true }
                PlayerControlsAction.SeekBack, PlayerControlsAction.KeyboardSeekBack -> { state.seekTo(state.snapshot.positionMs - 10_000); true }
                PlayerControlsAction.SeekForward, PlayerControlsAction.KeyboardSeekForward -> { state.seekTo(state.snapshot.positionMs + 10_000); true }
                PlayerControlsAction.ResizeMode -> {
                    val modes = PlayerResizeMode.entries
                    state.resizeMode = modes[(modes.indexOf(state.resizeMode) + 1) % modes.size]
                    true
                }
                else -> false
            }
        },
        onPlayerControlsEvent = { type, value ->
            when (type) {
                "cursorActivity", "keepChromeVisible" -> { state.controlsVisible = true; true }
                "hideChrome" -> { state.controlsVisible = false; true }
                "toggleFullscreen" -> (if (value >= 0.5) onEscapeFullscreen else onToggleFullscreen).invokeIfPresent()
                "setPlaybackState", "setPlaybackStateQuiet" -> { state.setPlaybackIntent(value >= 0.5); true }
                "setPlaybackSpeed" -> {
                    state.playbackSpeed = value.toFloat().coerceIn(0.25f, 4f)
                    controller?.setPlaybackSpeed(state.playbackSpeed)
                    true
                }
                "selectAudioTrack" -> {
                    val tracks = controller?.getAudioTracks().orEmpty()
                    val selected = tracks.firstOrNull { it.id == value.toInt().toString() }
                        ?: tracks.firstOrNull { it.index == value.toInt() }
                    selected?.let { state.audioTrackIndex = it.index; controller?.selectAudioTrack(it.index) }
                    true
                }
                "selectBuiltInSubtitleTrack" -> {
                    state.subtitleTrackIndex = value.toInt()
                    controller?.selectSubtitleTrack(value.toInt())
                    true
                }
                else -> false
            }
        },
        onPlayerControlsScrubChange = { state.scrubPositionMs = it; true },
        onPlayerControlsScrubFinished = { state.seekTo(it); true },
        onControllerReady = {
            if (controller !== it) it.setPlaybackSpeed(state.playbackSpeed)
            controller = it
            if (active && presentationId == state.presentationId) state.controller = it
        },
        onSnapshot = {
            snapshot = it
            if (active && !tracksRestored && !it.isLoading && it.durationMs > 0) {
                state.audioTrackIndex?.let { index -> controller?.selectAudioTrack(index) }
                state.subtitleTrackIndex?.let { index -> controller?.selectSubtitleTrack(index) }
                tracksRestored = true
            }
            val awaitingDesktopResume = isDesktop && resumePositionMs > 0 && (it.isLoading || it.durationMs <= 0)
            if (active && !restoringPosition && !awaitingDesktopResume && presentationId == state.presentationId) state.snapshot = it
        },
        onError = { if (active && presentationId == state.presentationId) state.error = it },
    )
    if (restoringPosition) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

/** A paused but prepared trailer counts as ready; buffering must not leave startup spinning forever. */
internal suspend fun TrailerPlaybackState.awaitStartup(timeoutMs: Long = 20_000L): Boolean =
    withTimeoutOrNull(timeoutMs) {
        snapshotFlow { error != null || (!snapshot.isLoading && snapshot.durationMs > 0) }.first { it }
    } != null

private fun (() -> Unit)?.invokeIfPresent(): Boolean {
    this?.invoke() ?: return false
    return true
}
