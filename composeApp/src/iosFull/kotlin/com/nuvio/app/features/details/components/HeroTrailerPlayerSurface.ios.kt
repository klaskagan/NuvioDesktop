package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import com.nuvio.app.features.details.DesktopHeroTrailerEnd
import androidx.compose.ui.Modifier

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
) = Unit

@Composable
internal actual fun rememberDesktopHeroTrailer(
    preview: DesktopHeroTrailerPreview?,
    playWhenReady: Boolean,
    muted: Boolean,
    onReady: () -> Unit,
    onTerminal: (DesktopHeroTrailerEnd) -> Unit,
): (@Composable (Modifier, Boolean) -> Unit)? = null
