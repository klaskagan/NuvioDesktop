package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.details.heroTrailerUprightAspect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.nuvio.app.features.details.DesktopHeroTrailerEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.nuvio.app.features.player.desktop.DesktopPlaybackSource
import com.nuvio.app.features.trailer.DesktopHeroPlaylist
import com.nuvio.app.features.trailer.TrailerExtractionPlatform
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.LocalVideoFrameDrawn
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

private const val TrailerFillFrameScale = 1.35f

@Composable
actual fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    startPositionMillis: Long,
    fillFrame: Boolean,
    modifier: Modifier,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
) {
    key(sourceUrl, sourceAudioUrl, startPositionMillis) {
        val surface = rememberDesktopTrailerSession(
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            playWhenReady = playWhenReady,
            muted = muted,
            startPositionMillis = startPositionMillis,
            onReady = onReady,
            onEnded = onEnded,
            onError = onError,
        )
        surface(modifier, fillFrame)
    }
}

@Composable
internal actual fun rememberDesktopHeroTrailer(
    preview: DesktopHeroTrailerPreview?,
    playWhenReady: Boolean,
    muted: Boolean,
    onReady: () -> Unit,
    onTerminal: (DesktopHeroTrailerEnd) -> Unit,
    clock: DesktopHeroTrailerClock?,
): (@Composable (Modifier, Boolean) -> Unit)? = if (preview == null) {
    null
} else {
    key(preview.watchUrl, preview.rendition, preview.attemptIdentity) {
        rememberDesktopTrailerSession(
            sourceUrl = preview.rendition.videoUrl,
            sourceAudioUrl = preview.rendition.audioUrl,
            playWhenReady = playWhenReady,
            muted = muted,
            startPositionMillis = 0,
            onReady = onReady,
            onEnded = { onTerminal(DesktopHeroTrailerEnd.Completed) },
            onError = { onTerminal(DesktopHeroTrailerEnd.Failed) },
            preview = preview,
            clock = clock,
        )
    }
}

@Composable
private fun rememberDesktopTrailerSession(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    startPositionMillis: Long,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    preview: DesktopHeroTrailerPreview? = null,
    clock: DesktopHeroTrailerClock? = null,
): @Composable (Modifier, Boolean) -> Unit {
    val player = rememberVideoPlayerState()
    val preparedSource = remember(sourceUrl, sourceAudioUrl, preview?.rendition) {
        DesktopPlaybackSource(sourceUrl, sourceAudioUrl, true, TrailerExtractionPlatform.defaultHeaders)
    }
    DisposableEffect(preparedSource) { onDispose { preparedSource.close() } }
    val latestPlayWhenReady = rememberUpdatedState(playWhenReady)
    val callbackScope = rememberCoroutineScope()
    val latestOnReady = rememberUpdatedState(onReady)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnError = rememberUpdatedState(onError)
    var mediaReady by remember { mutableStateOf(false) }
    var terminalReported by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        player.onPlaybackEnded = {
            callbackScope.launch {
                if (!terminalReported) {
                    terminalReported = true
                    mediaReady = false
                    TrailerExtractionPlatform.diagnostic("compose player ended")
                    latestOnEnded.value()
                }
            }
        }
        onDispose {
            player.onPlaybackEnded = null
        }
    }

    LaunchedEffect(player, sourceUrl, sourceAudioUrl, startPositionMillis) {
        mediaReady = false
        terminalReported = false
        player.clearError()
        player.loop = false
        player.volume = if (muted) 0f else 1f
        TrailerExtractionPlatform.diagnostic(
            "compose player open ${TrailerExtractionPlatform.describeUrl(sourceUrl)} " +
                "separateAudio=${!sourceAudioUrl.isNullOrBlank()} startMs=$startPositionMillis",
        )
        if (preview != null && TrailerExtractionPlatform.diagnosticsEnabled) {
            val rendition = preview.rendition
            TrailerExtractionPlatform.diagnostic(
                "hero opening title=${preview.title} url=${preview.watchUrl} " +
                    "rendition=${dimensions(rendition.width, rendition.height)} pinnedHls=${rendition.pinnedPlaylist != null} " +
                    "codecs=${rendition.codecs ?: "unknown"}",
            )
        }
        val playlist = try {
            preview?.rendition?.pinnedPlaylist?.let(::DesktopHeroPlaylist)
        } catch (error: IOException) {
            terminalReported = true
            TrailerExtractionPlatform.diagnostic("blocked stage=hero_playlist error=${error::class.simpleName}")
            latestOnError.value()
            return@LaunchedEffect
        }
        try {
            val startPlaying = preview != null && latestPlayWhenReady.value
            player.openUri(
                uri = playlist?.url ?: preparedSource.videoUrl,
                audioUri = preparedSource.audioUrl,
                initializeplayerState = if (startPlaying) InitialPlayerState.PLAY else InitialPlayerState.PAUSE,
            )

            val opened = withTimeoutOrNull(12_000L) {
                snapshotFlow {
                    val hasFrame = player.hasMedia && (preview == null || player.hasVideoFrame)
                    val started = preview == null || !latestPlayWhenReady.value || player.currentTime > 0.0
                    (hasFrame && started) to player.error
                }.first { (hasFrame, error) -> hasFrame || error != null }
            }
            val error = opened?.second ?: player.error
            if (opened == null || error != null) {
                if (!terminalReported) {
                    terminalReported = true
                    TrailerExtractionPlatform.diagnostic(
                        "blocked stage=compose_player_open detail=${error ?: "timeout"}",
                    )
                    latestOnError.value()
                }
                return@LaunchedEffect
            }

            if (startPositionMillis > 0L && player.duration > 0.0) {
                val normalizedPosition = (
                    startPositionMillis.toDouble() /
                        (player.duration * 1_000.0) *
                        1_000.0
                    ).toFloat()
                player.seekTo(normalizedPosition)
            }
            mediaReady = true
            TrailerExtractionPlatform.diagnostic("compose player ready playing=${latestPlayWhenReady.value}")
            latestOnReady.value()
            if (playlist != null) awaitCancellation()
        } finally {
            playlist?.close()
        }
    }

    LaunchedEffect(player, playWhenReady, mediaReady, terminalReported) {
        if (terminalReported || (preview == null && !mediaReady)) return@LaunchedEffect
        if (!mediaReady) {
            // A pinned HLS preview may need to play before its first frame arrives.
            snapshotFlow { player.hasMedia }.first { it }
        }
        if (playWhenReady) player.play() else player.pause()
    }

    if (clock != null) {
        // Whole seconds are all the expanded tile's times show, so the clock moves once a second.
        LaunchedEffect(player, clock) {
            clock.reset()
            snapshotFlow { (player.currentTime * 1_000).toLong() / 1_000 * 1_000 to (player.duration * 1_000).toLong() }
                .collect { (position, duration) ->
                    clock.positionMs = position.coerceAtLeast(0L)
                    clock.durationMs = duration.coerceAtLeast(0L)
                }
        }
        DisposableEffect(player, clock) {
            // compose-media-player seeks on a 0..1000 scale of the clip's length.
            val seeker: (Long) -> Unit = { ms ->
                val durationMs = player.duration * 1_000.0
                if (durationMs > 0.0) player.seekTo((ms / durationMs * 1_000.0).toFloat().coerceIn(0f, 1_000f))
            }
            clock.seeker = seeker
            onDispose { if (clock.seeker === seeker) clock.seeker = null }
        }
    }

    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(player) {
        snapshotFlow { player.error }
            .first { it != null }
            ?.let { error ->
                if (!terminalReported) {
                    terminalReported = true
                    mediaReady = false
                    TrailerExtractionPlatform.diagnostic(
                        "blocked stage=compose_player detail=$error",
                    )
                    latestOnError.value()
                }
            }
    }

    val onFrameDrawn: ((IntSize, IntSize) -> Unit)? =
        if (preview != null && TrailerExtractionPlatform.diagnosticsEnabled) {
            val sizes = remember { HeroFrameMeasurements() }
            val reportFrame: (IntSize, IntSize) -> Unit = { frameBuffer, drawingSurface ->
                val current = frameBuffer to drawingSurface
                val previous = sizes.last
                if (preview.visible && previous != current) {
                    sizes.last = current
                    val event = if (previous == null) "first_frame" else "size_changed"
                    TrailerExtractionPlatform.diagnostic(
                        "hero $event title=${preview.title} url=${preview.watchUrl} " +
                            "rendition=${dimensions(preview.rendition.width, preview.rendition.height)} " +
                            "frameBuffer=${dimensions(frameBuffer.width, frameBuffer.height)} " +
                            "drawingSurfacePx=${dimensions(drawingSurface.width, drawingSurface.height)}",
                    )
                }
            }
            reportFrame
        } else {
            null
        }
    val uprightAspect = preview?.rendition?.let { heroTrailerUprightAspect(it.width, it.height) }
    return { modifier, fillFrame ->
        Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalVideoFrameDrawn provides onFrameDrawn) {
                if (uprightAspect != null) {
                    // A portrait clip stands centred at its own shape on a dark field, never cropped
                    // or stretched to the tile's 16:9.
                    Box(Modifier.fillMaxSize().background(Color(0xE6060708)))
                    VideoPlayerSurface(
                        playerState = player,
                        modifier = Modifier.fillMaxHeight().aspectRatio(uprightAspect),
                        contentScale = ContentScale.FillBounds,
                    )
                    return@CompositionLocalProvider
                }
                VideoPlayerSurface(
                    playerState = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (fillFrame) {
                                scaleX = TrailerFillFrameScale
                                scaleY = TrailerFillFrameScale
                            }
                        },
                    contentScale = if (fillFrame) ContentScale.Crop else ContentScale.Fit,
                )
            }
        }
    }
}

private class HeroFrameMeasurements {
    var last: Pair<IntSize, IntSize>? = null
}

private fun dimensions(width: Int, height: Int): String =
    if (width > 0 && height > 0) "${width}x$height" else "unknown"
