package com.nuvio.app.features.home.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.MediaKeysHandler
import androidx.compose.runtime.CompositionLocalProvider
import com.nuvio.app.core.ui.LocalPosterPressAndHold
import com.nuvio.app.core.ui.PosterHoverStyle
import com.nuvio.app.core.ui.PosterZoomOverlayCoordinator
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.isDesktop
import kotlinx.coroutines.delay

private const val HoverStripCloseDelayMillis = 120L

/**
 * Desktop poster hover ("Poster hover · actions" in the Home design): the hovered poster lifts with
 * a white ring and its action strip at once. Space or the strip's ⓘ opens Quick look
 * ([PosterQuickLook]). With the Inline expand style it instead widens in place into a card after
 * Settings → Hover preview's delay ([PosterInlineExpand]). [content] draws the poster: it applies the
 * modifier to the card and draws the overlay (null when hover is off) inside its artwork.
 */
@Composable
internal fun HomePosterHoverPreview(
    item: MetaPreview,
    isWatched: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** False in grids: a cell has a fixed width, so Inline expand falls back to the action strip. */
    allowInlineExpand: Boolean = true,
    /** The list Quick look's ← → walks; defaults to the enclosing row's ([LocalPosterQuickLookRow]). */
    quickLookRow: List<MetaPreview>? = null,
    content: @Composable (Modifier, PosterHoverOverlay?) -> Unit,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    if (!isDesktop || !posterCardStyle.hoverPreviewEnabled || PosterZoomOverlayCoordinator.isVisible) {
        content(modifier, null)
        return
    }

    val row = quickLookRow ?: LocalPosterQuickLookRow.current
    val currentOnClick by rememberUpdatedState(onClick)
    val target = remember(item, row) { QuickLookTarget(item, row.orEmpty(), onOpenDetails = { currentOnClick?.invoke() }) }
    val anchorInteraction = remember { MutableInteractionSource() }
    val hovered by anchorInteraction.collectIsHoveredAsState()
    val inline = allowInlineExpand && posterCardStyle.hoverStyle == PosterHoverStyle.InlineExpand
    var stripVisible by remember(item.type, item.id) { mutableStateOf(false) }
    var expanded by remember(item.type, item.id) { mutableStateOf(false) }

    LaunchedEffect(hovered, inline, posterCardStyle.hoverPreviewOpenDelayMillis) {
        if (hovered) {
            PosterQuickLook.hover(target)
            if (inline) {
                // Inline expand reflows the row, so it waits for the hover delay: skimming a row
                // must not push posters around.
                stripVisible = false
                delay(posterCardStyle.hoverPreviewOpenDelayMillis.toLong())
                expanded = true
            } else {
                // The strip covers nothing and moves nothing, so it shows at once.
                stripVisible = true
            }
        } else {
            PosterQuickLook.unhover(target)
            delay(HoverStripCloseDelayMillis)
            stripVisible = false
            expanded = false
        }
    }
    DisposableEffect(target) { onDispose { PosterQuickLook.unhover(target) } }
    // Space on the hovered poster opens Quick look. Claimed only while hovered, so Space keeps its
    // usual meaning everywhere else.
    MediaKeysHandler(enabled = hovered && PosterQuickLook.open == null) { key ->
        if (key == Key.Spacebar) {
            PosterQuickLook.open(target)
            true
        } else {
            false
        }
    }

    // Play trailer is an explicit request, so it needs in-app trailers only, not the setting that
    // plays trailers by themselves in Quick look and the inline card.
    val canPlayTrailer = AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP
    val onWatched = rememberPosterWatchedToggle(item)
    WithPosterLibraryToggle(item) { isSaved, toggleSaved ->
        val onPlayTrailer: (() -> Unit)? = if (canPlayTrailer) ({ PosterQuickLook.playTrailer(target) }) else null
        if (inline) {
            PosterInlineExpand(
                item = item,
                expanded = expanded,
                hovered = hovered,
                isWatched = isWatched,
                isSaved = isSaved,
                onClick = onClick,
                onLongClick = onLongClick,
                onPressAndHold = { PosterQuickLook.open(target) },
                onPlayTrailer = onPlayTrailer,
                onWatched = onWatched,
                onSave = toggleSaved,
                onQuickLook = { PosterQuickLook.open(target) },
                modifier = modifier.hoverable(anchorInteraction),
                poster = content,
            )
        } else {
            val overlay: PosterHoverOverlay = { shape ->
                PosterActionStrip(
                    shape = shape,
                    ringVisible = hovered,
                    stripVisible = stripVisible,
                    isWatched = isWatched,
                    isSaved = isSaved,
                    onPlayTrailer = onPlayTrailer,
                    onWatched = onWatched,
                    onSave = toggleSaved,
                    onQuickLook = { PosterQuickLook.open(target) },
                )
            }
            Box(modifier) {
                CompositionLocalProvider(LocalPosterPressAndHold provides { PosterQuickLook.open(target) }) {
                    content(Modifier.hoverable(anchorInteraction), overlay)
                }
            }
        }
    }
}
