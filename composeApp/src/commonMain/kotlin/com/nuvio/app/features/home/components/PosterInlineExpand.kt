package com.nuvio.app.features.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.nuvio.app.core.ui.LocalPosterPressAndHold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.nuvio.app.core.ui.displayRatingText
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.secondaryClick
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hero_add_to_library
import nuvio.composeapp.generated.resources.hero_mark_unwatched
import nuvio.composeapp.generated.resources.hero_mark_watched
import nuvio.composeapp.generated.resources.hero_remove_from_library
import nuvio.composeapp.generated.resources.home_poster_play_trailer
import nuvio.composeapp.generated.resources.home_quick_look
import nuvio.composeapp.generated.resources.home_quick_look_mute
import nuvio.composeapp.generated.resources.home_quick_look_unmute
import org.jetbrains.compose.resources.stringResource

/** The widen is slow and eased: it is the one hover that moves the row (Home design, "inline expand"). */
private const val InlineExpandMillis = 300
private val CardShape = RoundedCornerShape(12.dp)
private const val InlinePrefetchRestMillis = 250L

/**
 * "Poster hover · inline expand": once [expanded], the poster widens in place into a 16:9 card the
 * artwork's height, the posters after it sliding right, and crossfades to the card. The card plays
 * the trailer muted inside it with the name, type · year · IMDb and the Trailer, Watched, Library
 * and Quick look buttons. [poster] is the card's usual content; its overlay reports the artwork
 * size (the card's height) and draws the hover ring until the card opens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PosterInlineExpand(
    item: MetaPreview,
    expanded: Boolean,
    hovered: Boolean,
    isWatched: Boolean,
    isSaved: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    /** Press-and-hold opens Quick look; [onLongClick] (the poster menu) stays on right-click. */
    onPressAndHold: () -> Unit,
    onPlayTrailer: (() -> Unit)?,
    onWatched: () -> Unit,
    onSave: () -> Unit,
    onQuickLook: () -> Unit,
    modifier: Modifier,
    poster: @Composable (Modifier, PosterHoverOverlay?) -> Unit,
) {
    var artworkSize by remember { mutableStateOf(IntSize.Zero) }
    val style = rememberPosterCardStyleUiState()
    val trailerEnabled = AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP && style.hoverPreviewTrailerEnabled
    var details by remember(item.stableKey()) { mutableStateOf<MetaDetails?>(null) }
    var source by remember(item.stableKey()) { mutableStateOf<TrailerPlaybackSource?>(null) }
    // Loads while the hover delay runs, so the card opens on its backdrop instead of black. A short
    // rest first keeps a quick skim across the row from fetching every poster it crosses.
    LaunchedEffect(hovered, item.stableKey(), trailerEnabled) {
        if (!hovered) return@LaunchedEffect
        delay(InlinePrefetchRestMillis)
        if (details == null) {
            details = try {
                MetaDetailsRepository.fetch(item.type, item.id)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                null
            }
        }
        if (trailerEnabled && source == null) source = resolveHomePosterHoverTrailerPlaybackSource(item)
    }
    val progress by animateFloatAsState(
        targetValue = if (expanded && artworkSize.height > 0) 1f else 0f,
        animationSpec = tween(InlineExpandMillis, easing = FastOutSlowInEasing),
        label = "poster_inline_expand",
    )
    val overlay: PosterHoverOverlay = { shape ->
        Box(Modifier.matchParentSize().onSizeChanged { artworkSize = it })
        // The ring marks the hovered poster while the delay runs; the card has its own.
        PosterActionStrip(
            shape = shape,
            ringVisible = hovered && !expanded,
            stripVisible = false,
            isWatched = isWatched,
            isSaved = isSaved,
            onPlayTrailer = null,
            onWatched = {},
            onSave = {},
            onQuickLook = {},
        )
    }
    val density = LocalDensity.current
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.graphicsLayer { alpha = 1f - progress }) {
                CompositionLocalProvider(LocalPosterPressAndHold provides onPressAndHold) { poster(Modifier, overlay) }
            }
            if (progress > 0f) {
                val cardWidth = with(density) { (artworkSize.height * 16 / 9).toDp() }
                Box(
                    Modifier.graphicsLayer { alpha = progress }
                        .clip(CardShape)
                        .border(2.dp, Color.White.copy(alpha = 0.95f), CardShape)
                        .testTag("poster_inline_card"),
                ) {
                    // Laid out at its full width from the first frame and revealed by the widening
                    // clip, so nothing inside reflows while it opens.
                    InlineCardContent(
                        item = item,
                        details = details,
                        source = source,
                        trailerEnabled = trailerEnabled,
                        isWatched = isWatched,
                        isSaved = isSaved,
                        onPlayTrailer = onPlayTrailer,
                        onWatched = onWatched,
                        onSave = onSave,
                        onQuickLook = onQuickLook,
                        modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                            .width(cardWidth)
                            .fillMaxSize()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onClick?.invoke() },
                                onLongClick = onPressAndHold,
                            )
                            .secondaryClick(onLongClick),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val posterPlaceable = measurables[0].measure(constraints)
        val expandedWidth = maxOf(posterPlaceable.width, artworkSize.height * 16 / 9)
        val width = lerp(posterPlaceable.width.toFloat(), expandedWidth.toFloat(), progress).toInt()
        val card = measurables.getOrNull(1)?.measure(Constraints.fixed(width, artworkSize.height.coerceAtLeast(1)))
        layout(width, posterPlaceable.height) {
            posterPlaceable.place(0, 0)
            card?.place(0, 0)
        }
    }
}


@Composable
private fun InlineCardContent(
    item: MetaPreview,
    details: MetaDetails?,
    source: TrailerPlaybackSource?,
    trailerEnabled: Boolean,
    isWatched: Boolean,
    isSaved: Boolean,
    onPlayTrailer: (() -> Unit)?,
    onWatched: () -> Unit,
    onSave: () -> Unit,
    onQuickLook: () -> Unit,
    modifier: Modifier,
) {
    val style = rememberPosterCardStyleUiState()
    // Muted by default (the card is a glance); Play with Sound in Settings starts it unmuted.
    var muted by remember(item.stableKey()) { mutableStateOf(!style.hoverPreviewTrailerSoundEnabled) }
    Box(modifier.background(Color(0xFF151618))) {
        HoverBackdrop(item, details, Modifier.fillMaxSize())
        if (trailerEnabled) {
            HomePosterHoverTrailer(
                playbackSource = source,
                soundEnabled = !muted,
                startPositionSeconds = style.hoverPreviewTrailerStartSeconds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFF060607).copy(alpha = 0.85f), 0.7f to Color.Transparent)))
        if (trailerEnabled) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                StripButton(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    stringResource(if (muted) Res.string.home_quick_look_unmute else Res.string.home_quick_look_mute),
                    30.dp, { muted = !muted }, "poster_inline_mute",
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp, end = 60.dp)) {
            var logoFailed by remember(item.stableKey()) { mutableStateOf(false) }
            val logo = (item.logo ?: details?.logo)?.takeIf { it.isNotBlank() && !logoFailed }
            if (logo != null) {
                NuvioAsyncImage(
                    model = logo,
                    contentDescription = item.name,
                    modifier = Modifier.width(190.dp).height(44.dp),
                    alignment = Alignment.BottomStart,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                )
            } else {
                Text(
                    item.name,
                    style = redesignText(26.sp, FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                if (item.type.equals("movie", ignoreCase = true)) com.nuvio.app.core.i18n.localizedMovieTypeLabel()
                else com.nuvio.app.core.i18n.localizedMediaTypeLabel(item.type),
                (item.releaseInfo ?: details?.releaseInfo)?.takeIf { it.isNotBlank() }?.let(::formatReleaseDateForDisplay),
                displayRatingText(item.imdbRating ?: details?.imdbRating)?.let { "IMDb $it" },
            ).joinToString(" · ")
            Text(
                meta,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                style = redesignText(12.5.sp, FontWeight.Medium, Color(0xFFDFE2E5)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onPlayTrailer != null) {
                    StripButton(Icons.Rounded.PlayArrow, stringResource(Res.string.home_poster_play_trailer), 30.dp, onPlayTrailer, "poster_inline_trailer")
                }
                StripButton(
                    if (isWatched) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (isWatched) Res.string.hero_mark_unwatched else Res.string.hero_mark_watched),
                    30.dp, onWatched, "poster_inline_watched",
                )
                StripButton(
                    if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    stringResource(if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library),
                    30.dp, onSave, "poster_inline_library",
                )
                StripButton(Icons.Outlined.Info, stringResource(Res.string.home_quick_look), 30.dp, onQuickLook, "poster_inline_quick_look")
            }
        }
    }
}
