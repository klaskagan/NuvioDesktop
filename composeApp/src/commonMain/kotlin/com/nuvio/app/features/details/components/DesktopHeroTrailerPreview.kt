package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.features.trailer.HeroTrailerRendition

internal data class DesktopHeroTrailerPreview(
    val rendition: HeroTrailerRendition,
    val title: String,
    val watchUrl: String,
    val visible: Boolean,
)

@Composable
internal expect fun rememberDesktopHeroTrailer(
    preview: DesktopHeroTrailerPreview?,
    playWhenReady: Boolean,
    muted: Boolean,
    onReady: () -> Unit,
    onTerminal: () -> Unit,
): (@Composable (Modifier, Boolean) -> Unit)?
