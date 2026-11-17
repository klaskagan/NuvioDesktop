package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import com.nuvio.app.features.details.DesktopHeroTrailerEnd
import androidx.compose.ui.Modifier
import com.nuvio.app.features.trailer.HeroTrailerRendition

internal data class DesktopHeroTrailerPreview(
    val rendition: HeroTrailerRendition,
    val title: String,
    val watchUrl: String,
    val visible: Boolean,
    val attemptIdentity: Any? = null,
)

@Composable
internal expect fun rememberDesktopHeroTrailer(
    preview: DesktopHeroTrailerPreview?,
    playWhenReady: Boolean,
    muted: Boolean,
    onReady: () -> Unit,
    onTerminal: (DesktopHeroTrailerEnd) -> Unit,
): (@Composable (Modifier, Boolean) -> Unit)?
