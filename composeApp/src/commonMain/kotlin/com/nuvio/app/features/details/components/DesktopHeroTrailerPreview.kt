package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * Where the desktop hero trailer is, for the expanded tile's elapsed and total times and its seek
 * bar. Players that can't tell leave both at 0, and the tile then shows no times; players that
 * can't seek leave [seeker] null, and the bar is then display-only.
 */
@Stable
internal class DesktopHeroTrailerClock {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    /** Moves the playing trailer to a position in milliseconds; set by the player session. */
    var seeker by mutableStateOf<((Long) -> Unit)?>(null)

    val canSeek: Boolean get() = seeker != null && durationMs > 0L

    /** Seeks to [ms], kept inside the clip, and shows the new time at once. */
    fun seekTo(ms: Long) {
        val seek = seeker ?: return
        if (durationMs <= 0L) return
        val target = ms.coerceIn(0L, durationMs)
        positionMs = target / 1_000 * 1_000
        seek(target)
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    fun reset() {
        positionMs = 0L
        durationMs = 0L
    }
}

@Composable
internal expect fun rememberDesktopHeroTrailer(
    preview: DesktopHeroTrailerPreview?,
    playWhenReady: Boolean,
    muted: Boolean,
    onReady: () -> Unit,
    onTerminal: (DesktopHeroTrailerEnd) -> Unit,
    clock: DesktopHeroTrailerClock? = null,
): (@Composable (Modifier, Boolean) -> Unit)?
