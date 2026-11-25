package com.nuvio.app.features.details.components

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import com.nuvio.app.OverlayEscapeHandler
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Stable
import nuvio.composeapp.generated.resources.compose_action_pause
import nuvio.composeapp.generated.resources.action_play
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.detail_board_credit_open
import nuvio.composeapp.generated.resources.detail_board_credit_episodes
import com.nuvio.app.core.ui.RedesignMenuSurface
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import nuvio.composeapp.generated.resources.player_seek_position
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.nuvio.app.features.home.HOME_BOARD_EXPANDED_MARGIN
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.BiasAlignment
import com.nuvio.app.core.ui.DesktopBackdropVerticalBias
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.PinnedBarBackground
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignTabs
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.secondaryClick
import com.nuvio.app.features.details.DetailBoardProgress
import com.nuvio.app.features.details.DetailBoardTab
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.formatRuntimeForDisplay
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.originalTmdbImageUrl
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.detail_board_collapse_trailer
import nuvio.composeapp.generated.resources.detail_board_collection
import nuvio.composeapp.generated.resources.detail_board_collection_more
import nuvio.composeapp.generated.resources.detail_board_expand_trailer
import nuvio.composeapp.generated.resources.detail_board_progress
import nuvio.composeapp.generated.resources.detail_board_progress_count
import nuvio.composeapp.generated.resources.detail_board_season_short
import nuvio.composeapp.generated.resources.detail_board_your_way
import nuvio.composeapp.generated.resources.detail_logo_content_description
import nuvio.composeapp.generated.resources.detail_mute_trailer
import nuvio.composeapp.generated.resources.detail_unmute_trailer
import nuvio.composeapp.generated.resources.playback_unavailable
import nuvio.composeapp.generated.resources.detail_trailers_cant_play
import nuvio.composeapp.generated.resources.detail_trailers_open_youtube
import nuvio.composeapp.generated.resources.detail_gallery_close
import com.nuvio.app.core.ui.RedesignPillButton
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.nuvio.app.features.details.MetaPerson
import nuvio.composeapp.generated.resources.detail_board_created_by
import nuvio.composeapp.generated.resources.detail_board_directed_by
import nuvio.composeapp.generated.resources.detail_board_starring
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration

/** The board's height; the loading skeleton uses the same value so nothing moves when data arrives. */
internal val DetailBoardHeight = 440.dp

/**
 * The board's height: [DetailBoardHeight], or while [expanded] (the trailer) Home's expanded height,
 * the viewport less [HOME_BOARD_EXPANDED_MARGIN], so the tabs wait below the fold. An unknown
 * viewport keeps the collapsed height.
 */
internal fun detailBoardHeight(viewportHeight: Dp, expanded: Boolean): Dp =
    if (expanded && viewportHeight.isSpecified) {
        (viewportHeight - HOME_BOARD_EXPANDED_MARGIN).coerceAtLeast(DetailBoardHeight)
    } else {
        DetailBoardHeight
    }

/** The pinned tabs row's height. */
internal val DetailBoardTabsHeight = 56.dp

private val BoardSideColumnWidth = 352.dp
private val BoardGap = 14.dp
private val tileScrim = Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color(0xEB08090B))
private const val BoardMotionMillis = 360

/** The featured tile's title column: wide enough for the actions with Rate open. Text inside stays at [BoardTitleTextMaxWidth]. */
internal val BoardTitleColumnMaxWidth = 920.dp
internal val BoardTitleTextMaxWidth = 760.dp

internal fun Modifier.boardTile(border: Color = RedesignColors.hairline): Modifier =
    clip(RedesignTileShape).background(RedesignColors.fill).border(1.dp, border, RedesignTileShape)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.boardClickable(
    interaction: MutableInteractionSource,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
): Modifier = if (onClick == null) this else hoverable(interaction).combinedClickable(
    interactionSource = interaction,
    indication = null,
    role = Role.Button,
    onLongClick = onLongClick,
    onClick = onClick,
).secondaryClick(onLongClick)

@Composable
internal fun BoardKicker(text: String, color: Color = RedesignColors.textMuted, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = redesignText(11.sp, FontWeight.Medium, color, letterSpacing = 0.1.em),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun BoardProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(RedesignColors.textPrimary))
    }
}

/**
 * Desktop Details' opening board, on Home's board geometry: the title as a featured tile beside a
 * column of personal tiles. Without side tiles, or while [expanded] (the trailer), the featured
 * tile takes the whole width.
 */
@Composable
internal fun DetailBoard(
    expanded: Boolean,
    hasSide: Boolean,
    modifier: Modifier = Modifier,
    /** The page's viewport height, which an expanded board grows into. */
    viewportHeight: Dp = Dp.Unspecified,
    side: @Composable ColumnScope.() -> Unit,
    featured: @Composable BoxScope.() -> Unit,
) {
    // Grows with the side column's fold, on Home's timing, so the trailer opens in one movement.
    val height by animateDpAsState(
        detailBoardHeight(viewportHeight, expanded),
        tween(BoardMotionMillis, easing = FastOutSlowInEasing),
        label = "detail_board_height",
    )
    Row(modifier.fillMaxWidth().height(height).testTag("detail_board")) {
        Box(Modifier.weight(1f).fillMaxHeight().boardTile().testTag("detail_board_featured"), content = featured)
        AnimatedVisibility(
            visible = hasSide && !expanded,
            enter = fadeIn(tween(240, delayMillis = 120)) + expandHorizontally(tween(BoardMotionMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
            exit = fadeOut(tween(160)) + shrinkHorizontally(tween(BoardMotionMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
        ) {
            Column(
                Modifier.padding(start = BoardGap).width(BoardSideColumnWidth).fillMaxHeight().testTag("detail_board_side"),
                verticalArrangement = Arrangement.spacedBy(BoardGap),
                content = side,
            )
        }
    }
}

/** The white primary pill: Play, Resume or the next episode. Right-click or long-press chooses a source. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoardPlayButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            !enabled -> RedesignColors.fillStrong
            hovered -> Color.White
            else -> RedesignColors.textPrimary
        },
        tween(180),
        label = "board_play_fill",
    )
    val content = if (enabled) RedesignOnLight else RedesignColors.textMuted
    Row(
        modifier.height(if (compact) 36.dp else 44.dp).clip(CircleShape).background(fill)
            .hoverable(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .secondaryClick(onLongClick.takeIf { enabled })
            .padding(start = if (compact) 12.dp else 18.dp, end = if (compact) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = content, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (enabled) label else stringResource(Res.string.playback_unavailable),
            style = redesignText(if (compact) 13.5.sp else 14.5.sp, FontWeight.SemiBold, content),
            maxLines = 1,
        )
    }
}

/** A secondary action beside Play. Filled while [active] (in library, watched, shuffling). */
@Composable
internal fun BoardActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Only the icon, while the Rate strip borrows the row's width. */
    iconOnly: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            active -> RedesignColors.textPrimary
            hovered -> RedesignColors.fillStrong
            else -> Color(0x33101114)
        },
        tween(180),
        label = "board_action_fill",
    )
    val content by animateColorAsState(if (active) RedesignOnLight else RedesignColors.textPrimary, tween(180), label = "board_action_content")
    val end by animateDpAsState(if (iconOnly) 13.dp else 18.dp, tween(BoardMotionMillis, easing = FastOutSlowInEasing), label = "board_action_end")
    val start by animateDpAsState(if (iconOnly) 13.dp else 14.dp, tween(BoardMotionMillis, easing = FastOutSlowInEasing), label = "board_action_start")
    Row(
        modifier.height(44.dp).clip(CircleShape).background(fill).border(1.dp, RedesignColors.hairline, CircleShape)
            .boardClickable(interaction, onClick, onLongClick)
            .semantics { contentDescription = label }
            .padding(start = start, end = end),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        AnimatedVisibility(
            visible = !iconOnly,
            // Shrink toward the icon so a half-hidden label reads "Libr", never "rary".
            enter = fadeIn(tween(200)) + expandHorizontally(tween(BoardMotionMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(BoardMotionMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = redesignText(14.sp, FontWeight.SemiBold, content), maxLines = 1)
            }
        }
    }
}

/**
 * The featured tile: artwork (and the trailer over it once it plays), the shared back control, the
 * title block and the actions. While [expanded] the text steps back and the trailer has the tile.
 */
@Composable
internal fun BoxScope.DetailFeatureTile(
    meta: MetaDetails,
    expanded: Boolean,
    trailer: (@Composable (Modifier, Boolean) -> Unit)?,
    /** A picked trailer: its title, controls and times show while [expanded]. */
    expandedTrailer: DetailExpandedTrailer? = null,
    expandedFailure: com.nuvio.app.features.details.DetailBoardTrailerFailure? = null,
    trailerReady: Boolean,
    trailerMuted: Boolean,
    onMuteToggle: () -> Unit,
    onToggleExpanded: () -> Unit,
    onBack: () -> Unit,
    onArtworkLoaded: (androidx.compose.ui.graphics.painter.Painter) -> Unit,
    titleBlock: @Composable ColumnScope.() -> Unit,
) {
    val trailerAlpha by animateFloatAsState(if (trailerReady) 1f else 0f, tween(BoardMotionMillis), label = "board_trailer_alpha")
    val textAlpha by animateFloatAsState(if (expanded) 0f else 1f, tween(240), label = "board_text_alpha")
    val scrimAlpha by animateFloatAsState(if (expanded) 0.25f else 1f, tween(BoardMotionMillis), label = "board_scrim_alpha")
    val artwork = meta.background?.takeIf { it.isNotBlank() } ?: meta.poster
    if (artwork != null) {
        NuvioAsyncImage(
            model = originalTmdbImageUrl(artwork),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alignment = BiasAlignment(0.2f, DesktopBackdropVerticalBias),
            contentScale = ContentScale.Crop,
            onSuccess = { onArtworkLoaded(it.painter) },
        )
    }
    trailer?.invoke(Modifier.fillMaxSize().graphicsLayer { alpha = trailerAlpha }, true)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = scrimAlpha }.background(
            Brush.horizontalGradient(
                0f to Color(0xE6060708),
                0.42f to Color(0xA8060708),
                0.74f to Color.Transparent,
            ),
        ),
    )
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrimAlpha }.background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x99060708))))

    NuvioBackButton(
        onClick = onBack,
        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        containerColor = Color.Black.copy(alpha = 0.34f),
        showContainerOnDesktop = true,
        contentColor = RedesignColors.textPrimary,
        buttonSize = 48.dp,
        iconSize = 24.dp,
    )
    if (trailer != null) {
        Row(
            // A dark plate behind the frosted buttons keeps them readable over a bright trailer frame.
            Modifier.align(Alignment.TopEnd).padding(12.dp).graphicsLayer { alpha = trailerAlpha }
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.34f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RedesignIconButton(
                contentDescription = stringResource(if (trailerMuted) Res.string.detail_unmute_trailer else Res.string.detail_mute_trailer),
                onClick = onMuteToggle.takeIf { trailerReady },
                vector = if (trailerMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                size = 40.dp,
                modifier = Modifier.testTag("detail_board_mute"),
            )
            RedesignIconButton(
                contentDescription = stringResource(if (expanded) Res.string.detail_board_collapse_trailer else Res.string.detail_board_expand_trailer),
                onClick = onToggleExpanded.takeIf { trailerReady || expanded },
                vector = if (expanded) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                size = 40.dp,
                modifier = Modifier.testTag("detail_board_expand"),
            )
        }
    }
    Column(
        // 920dp fits the action row with the Rate strip open (✕ and ✓ included); the title block keeps
        // its text to 760dp itself. Overflowing this column instead hid ✕ and ✓ from the hand cursor,
        // which finds click targets by their (parent-clipped) semantics bounds.
        Modifier.align(Alignment.BottomStart).padding(start = 28.dp, end = 28.dp, bottom = 24.dp).widthIn(max = BoardTitleColumnMaxWidth)
            .graphicsLayer { alpha = textAlpha },
        content = titleBlock,
    )
    AnimatedVisibility(
        visible = expanded && expandedFailure != null,
        enter = fadeIn(tween(BoardMotionMillis)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 28.dp, vertical = 22.dp).testTag("detail_board_trailer_failed"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.detail_trailers_cant_play), style = redesignText(16.sp, FontWeight.SemiBold))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RedesignPillButton(
                    label = stringResource(Res.string.detail_trailers_open_youtube),
                    onClick = { expandedFailure?.onOpenExternally?.invoke() },
                    primary = true,
                    modifier = Modifier.testTag("detail_board_trailer_open_youtube"),
                )
                RedesignPillButton(
                    label = stringResource(Res.string.detail_gallery_close),
                    onClick = { expandedFailure?.onClose?.invoke() },
                )
            }
        }
    }
    // Keeps the last picked clip while the overlay fades out after collapse.
    var shownTrailer by remember { mutableStateOf(expandedTrailer) }
    if (expandedTrailer != null) shownTrailer = expandedTrailer
    AnimatedVisibility(
        visible = expanded && expandedTrailer != null && expandedFailure == null,
        enter = fadeIn(tween(BoardMotionMillis)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(Modifier.fillMaxSize()) { shownTrailer?.let { ExpandedTrailerOverlay(meta, it) } }
    }
}

/** A trailer picked from the Trailers tab or the Trailer button, as the expanded tile shows it. */
@Stable
internal class DetailExpandedTrailer(
    val title: String,
    /** "Severance · YouTube": the title and where the clip comes from. */
    val source: String,
    val clock: DesktopHeroTrailerClock?,
    val paused: Boolean,
    val onPauseToggle: () -> Unit,
    val playLabel: String,
    val playEnabled: Boolean,
    val onPlay: () -> Unit,
    val onPlayLongClick: (() -> Unit)?,
)

/** A clip time as the expanded tile shows it: 0:41, 2:24, 1:02:05. */
internal fun trailerClockTime(ms: Long): String {
    val total = (ms / 1_000L).coerceAtLeast(0L)
    val seconds = (total % 60).toString().padStart(2, '0')
    val hours = total / 3_600
    val minutes = (total / 60) % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}

/** The expanded tile's clip overlay: logo and clip title, play/pause and Play, and the clip's times. */
@Composable
private fun BoxScope.ExpandedTrailerOverlay(meta: MetaDetails, trailer: DetailExpandedTrailer) {
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xD9060708))))
    Row(
        Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 56.dp).testTag("detail_board_trailer_info"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoardTitleLogo(meta, height = 56.dp, maxWidth = 150.dp)
        Column(Modifier.widthIn(max = 560.dp)) {
            Text(
                trailer.title,
                style = redesignText(16.sp, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("detail_board_trailer_title"),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                trailer.source,
                style = redesignText(12.5.sp, color = RedesignColors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Row(
        Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 50.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedesignIconButton(
            contentDescription = stringResource(if (trailer.paused) Res.string.action_play else Res.string.compose_action_pause),
            onClick = trailer.onPauseToggle,
            vector = if (trailer.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            size = 38.dp,
            modifier = Modifier.testTag("detail_board_trailer_pause"),
        )
        BoardPlayButton(
            label = trailer.playLabel,
            enabled = trailer.playEnabled,
            onClick = trailer.onPlay,
            onLongClick = trailer.onPlayLongClick,
            compact = true,
            modifier = Modifier.testTag("detail_board_trailer_play"),
        )
    }
    val clock = trailer.clock
    if (clock != null && clock.durationMs > 0L) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .testTag("detail_board_trailer_time"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val timeStyle = redesignText(12.sp, color = RedesignColors.textSecondary)
            Text(trailerClockTime(clock.positionMs), style = timeStyle)
            BoardSeekBar(
                positionMs = clock.positionMs,
                durationMs = clock.durationMs,
                onSeek = clock::seekTo.takeIf { clock.canSeek },
                modifier = Modifier.weight(1f),
            )
            Text(trailerClockTime(clock.durationMs), style = timeStyle)
        }
    }
}

/**
 * The expanded clip's progress bar. It stays the frame's quiet 3dp line until hovered; then, like
 * the player's seek bar, it thickens, shows its handle and the time under the pointer, and a click
 * or drag seeks there. Without [onSeek] it only shows progress.
 */
@Composable
private fun BoardSeekBar(positionMs: Long, durationMs: Long, onSeek: ((Long) -> Unit)?, modifier: Modifier = Modifier) {
    val latestOnSeek by rememberUpdatedState(onSeek)
    var widthPx by remember { mutableStateOf(0) }
    var pointerX by remember { mutableStateOf<Float?>(null) }
    var dragMs by remember { mutableStateOf<Long?>(null) }
    val seekable = onSeek != null && durationMs > 0L
    fun msAt(x: Float): Long = if (widthPx <= 0) 0L else ((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong()
    val active = seekable && (pointerX != null || dragMs != null)
    val trackHeight by animateDpAsState(if (active) 5.dp else 3.dp, tween(160))
    val handleAlpha by animateFloatAsState(if (active) 1f else 0f, tween(160))
    val shownMs = dragMs ?: positionMs
    val fraction = if (durationMs > 0L) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val seekDescription = stringResource(Res.string.player_seek_position)
    val density = LocalDensity.current
    Box(
        modifier.height(20.dp).onSizeChanged { widthPx = it.width }
            .semantics { contentDescription = seekDescription }
            .testTag("detail_board_trailer_seek")
            .then(
                if (!seekable) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand).pointerInput(durationMs) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val x = change.position.x
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    pointerX = x
                                    if (dragMs != null) { dragMs = msAt(x); change.consume() }
                                }
                                PointerEventType.Exit -> if (dragMs == null) pointerX = null
                                PointerEventType.Press -> { dragMs = msAt(x); change.consume() }
                                PointerEventType.Release -> {
                                    dragMs?.let { latestOnSeek?.invoke(it) }
                                    dragMs = null
                                    if (x < 0f || x > size.width || change.position.y !in 0f..size.height.toFloat()) pointerX = null
                                    change.consume()
                                }
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(CircleShape).background(Color.White.copy(alpha = 0.28f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(RedesignColors.textPrimary))
        }
        Box(
            Modifier.offset { IntOffset((fraction * widthPx - 6.dp.toPx()).roundToInt(), 0) }
                .size(12.dp).graphicsLayer { alpha = handleAlpha }.clip(CircleShape).background(RedesignColors.textPrimary),
        )
        val hoverX = pointerX
        if (seekable && hoverX != null) {
            var bubbleWidth by remember { mutableStateOf(0) }
            Text(
                trailerClockTime(dragMs ?: msAt(hoverX)),
                style = redesignText(12.sp, FontWeight.SemiBold),
                modifier = Modifier
                    .offset {
                        val x = (hoverX - bubbleWidth / 2f).coerceIn(0f, (widthPx - bubbleWidth).toFloat().coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), with(density) { -26.dp.roundToPx() })
                    }
                    .onSizeChanged { bubbleWidth = it.width }
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
                    .testTag("detail_board_trailer_seek_time"),
            )
        }
    }
}

/**
 * Playback keys for a clip picked from Trailers while it plays expanded: Space or K pauses, ← and →
 * seek 10 seconds, M mutes. Returns false for any other key, so it reaches the focused control.
 */
internal fun handleExpandedTrailerKey(
    key: Key,
    clock: DesktopHeroTrailerClock?,
    onPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
): Boolean = when (key) {
    Key.Spacebar, Key.K -> { onPauseToggle(); true }
    Key.M -> { onMuteToggle(); true }
    Key.DirectionLeft, Key.DirectionRight -> {
        val seekable = clock?.takeIf { it.canSeek }
        seekable?.seekBy(if (key == Key.DirectionLeft) -TrailerKeySeekMs else TrailerKeySeekMs)
        seekable != null
    }
    else -> false
}

internal const val TrailerKeySeekMs = 10_000L

/** The title's logo, or its name where there is none or it fails to load. */
@Composable
internal fun BoardTitleLogo(meta: MetaDetails, height: Dp, maxWidth: Dp, modifier: Modifier = Modifier) {
    var failed by remember(meta.id, meta.logo) { mutableStateOf(false) }
    val logo = meta.logo?.takeIf { it.isNotBlank() && !failed }
    if (logo != null) {
        NuvioAsyncImage(
            model = logo,
            contentDescription = stringResource(Res.string.detail_logo_content_description, meta.name),
            modifier = modifier.widthIn(max = maxWidth).height(height),
            alignment = Alignment.CenterStart,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
        )
    } else {
        Text(
            text = meta.name,
            style = redesignText(if (height >= 80.dp) 44.sp else 20.sp, FontWeight.ExtraBold, letterSpacing = RedesignTightTracking),
            maxLines = if (height >= 80.dp) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.widthIn(max = if (height >= 80.dp) 560.dp else maxWidth),
        )
    }
}

/** "Series · Drama, Mystery · 2022– · 2 seasons · 49 min" and the certification. */
@Composable
internal fun BoardMetaLine(meta: MetaDetails, typeLabel: String?, modifier: Modifier = Modifier) {
    val parts = listOfNotNull(
        typeLabel,
        meta.genres.take(3).joinToString(", ").takeIf { it.isNotBlank() },
        desktopYearLabel(meta),
        desktopSeasonCountLabel(meta),
        formatRuntimeForDisplay(meta.runtime)?.takeIf { it.isNotBlank() },
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = parts.joinToString("  ·  "),
            style = redesignText(14.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        meta.ageRating?.takeIf { it.isNotBlank() }?.let { rating ->
            Spacer(Modifier.width(12.dp))
            Text(
                text = rating,
                style = redesignText(12.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Up next: the episode the primary button would play, on its still, with what is left of it. */
@Composable
internal fun DetailUpNextTile(
    kicker: String,
    title: String,
    artwork: String?,
    progressFraction: Float?,
    actionLabel: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(180), label = "up_next_border")
    Box(modifier.boardTile(border).boardClickable(interaction, onClick, onLongClick).testTag("detail_board_up_next")) {
        NuvioAsyncImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(tileScrim))
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp).clip(CircleShape).background(RedesignColors.textPrimary)
                .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = RedesignOnLight, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text(actionLabel, style = redesignText(12.5.sp, FontWeight.Bold, RedesignOnLight), maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            BoardKicker(kicker, RedesignColors.textSecondary)
            Text(title, style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progressFraction != null && progressFraction > 0f) {
                Spacer(Modifier.height(10.dp))
                BoardProgressBar(progressFraction, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Your progress: episodes watched of those aired, and a bar per season. A season row opens that season. */
@Composable
internal fun DetailProgressTile(
    progress: DetailBoardProgress?,
    onOpenSeason: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.boardTile().testTag("detail_board_progress").padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = DetailBoardTileBottomPadding + 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BoardKicker(stringResource(Res.string.detail_board_progress), modifier = Modifier.weight(1f))
            Text(
                // Until watch history has loaded there is no honest number to show.
                text = progress?.let { stringResource(Res.string.detail_board_progress_count, it.watched, it.aired) } ?: "–",
                style = redesignText(20.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Long-running shows scroll their seasons inside the tile instead of running past its padding.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        progress?.seasons?.forEach { season ->
            val interaction = remember(season.season) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateColorAsState(if (hovered) RedesignColors.fill else Color.Transparent, tween(160), label = "progress_row")
            val label = stringResource(Res.string.detail_board_season_short, season.season)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(fill)
                    .boardClickable(interaction, { onOpenSeason(season.season) })
                    .semantics { contentDescription = "$label ${season.watched}/${season.aired}" }
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = redesignText(12.5.sp, FontWeight.SemiBold, RedesignColors.textSecondary), modifier = Modifier.width(30.dp), maxLines = 1)
                BoardProgressBar(if (season.aired > 0) season.watched.toFloat() / season.aired else 0f, Modifier.weight(1f))
                Text(
                    "${season.watched}/${season.aired}",
                    style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textMuted),
                    modifier = Modifier.padding(start = 10.dp).widthIn(min = 34.dp),
                    maxLines = 1,
                )
            }
        }
    }}
}

/**
 * Side-tile heights inside the 440 dp board (design doc "Side-column heights"). The first tile in
 * the column takes the spare height instead of its own, so the column always ends flush.
 */
internal object DetailBoardTileHeights {
    val UpNext = 132.dp
    val Progress = 136.dp
    val YourWay = 144.dp
    val Collection = 138.dp
    val Tonight = 130.dp
}

/** Least space between a side tile's content and its bottom edge. */
internal val DetailBoardTileBottomPadding = 10.dp

private const val YourWayColumns = 3
private const val YourWayMaxActions = 6
private val YourWayCellHeight = 46.dp
private val YourWayCellGap = 6.dp

/**
 * Watch it your way: other ways into the title (On TV, Watch party, Download, Shuffle, Choose
 * source, Lists) as a three-column grid of up to six. Unavailable actions are left out by the caller
 * and the grid reflows; an action with a [DetailSecondaryAction.popup] opens it under its cell.
 */
@Composable
internal fun DetailYourWayTile(actions: List<DetailSecondaryAction>, modifier: Modifier = Modifier) {
    val shown = actions.take(YourWayMaxActions)
    Column(
        modifier.boardTile().animateContentSize(tween(BoardMotionMillis, easing = FastOutSlowInEasing))
            .testTag("detail_board_your_way").padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        BoardKicker(stringResource(Res.string.detail_board_your_way), modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(YourWayCellGap)) {
            shown.chunked(YourWayColumns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YourWayCellGap)) {
                    row.forEach { action -> YourWayCell(action, Modifier.weight(1f)) }
                    repeat(YourWayColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun YourWayCell(action: DetailSecondaryAction, modifier: Modifier) {
    val interaction = remember(action.label) { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val popupState = remember { MutableTransitionState(false) }
    val active = action.isActive || popupState.targetState
    val fill by animateColorAsState(
        when {
            active -> RedesignColors.textPrimary
            hovered -> RedesignColors.fillStrong
            else -> RedesignColors.fill
        },
        tween(160),
        label = "your_way_fill",
    )
    val content = if (active) RedesignOnLight else RedesignColors.textPrimary
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().height(YourWayCellHeight).clip(RoundedCornerShape(12.dp)).background(fill)
                .boardClickable(interaction, {
                    action.onClick()
                    if (action.popup != null) popupState.targetState = !popupState.targetState
                }, action.onLongClick)
                .then(action.tag?.let { Modifier.testTag(it) } ?: Modifier)
                .padding(horizontal = 9.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
            Text(action.label, style = redesignText(12.sp, FontWeight.SemiBold, content), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        val popup = action.popup
        if (popup != null && (popupState.currentState || popupState.targetState)) {
            val dismiss = { popupState.targetState = false }
            OverlayEscapeHandler { dismiss() }
            val offsetY = with(LocalDensity.current) { (YourWayCellHeight + 6.dp).roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, offsetY),
                onDismissRequest = dismiss,
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = popupState,
                    enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = TransformOrigin(0f, 0f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = TransformOrigin(0f, 0f)),
                ) {
                    popup(dismiss)
                }
            }
        }
    }
}

/** Collection: the first films of the title's collection, ticked when watched. */
@Composable
internal fun DetailCollectionTile(
    name: String,
    items: List<MetaPreview>,
    isWatched: (MetaPreview) -> Boolean,
    onOpen: ((MetaPreview) -> Unit)?,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.boardTile().testTag("detail_board_collection").padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BoardKicker("${stringResource(Res.string.detail_board_collection)} · $name", modifier = Modifier.weight(1f))
            if (items.size > 2) {
                val interaction = remember { MutableInteractionSource() }
                Text(
                    stringResource(Res.string.detail_board_collection_more, items.size - 2),
                    style = redesignText(12.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                    modifier = Modifier.boardClickable(interaction, onShowAll),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.take(2).forEach { item ->
                val interaction = remember(item.id) { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val fill by animateColorAsState(if (hovered) RedesignColors.fill else Color.Transparent, tween(160), label = "collection_row")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(fill)
                        .boardClickable(interaction, onOpen?.let { { it(item) } }).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)).background(RedesignColors.fill)) {
                        NuvioAsyncImage(model = item.poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (isWatched(item)) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(RedesignColors.textPrimary),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = RedesignOnLight, modifier = Modifier.size(13.dp)) }
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(item.name, style = redesignText(13.sp, FontWeight.SemiBold, lineHeight = 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.releaseInfo?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Tonight: how long the film runs (or has left) and when it would end if started now. */
@Composable
internal fun DetailTonightTile(
    kicker: String,
    headline: String,
    line: String?,
    progressFraction: Float?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.boardTile().testTag("detail_board_tonight").padding(horizontal = 16.dp, vertical = 14.dp)) {
        BoardKicker(kicker)
        Spacer(Modifier.height(4.dp))
        Text(headline, style = redesignText(24.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1)
        if (line != null) Text(line, style = redesignText(12.5.sp, color = RedesignColors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progressFraction != null && progressFraction > 0f) {
            Spacer(Modifier.height(10.dp))
            BoardProgressBar(progressFraction, Modifier.fillMaxWidth())
        }
    }
}

/**
 * The section tabs under the board. Once the board has scrolled away the row is [pinned]: it takes
 * the shared pinned-bar surface and shows Back, the logo and the primary button, so playing stays
 * one click away from any tab.
 */
@Composable
internal fun DetailBoardTabsBar(
    tabs: List<DetailBoardTab>,
    selected: DetailBoardTab,
    label: @Composable (DetailBoardTab) -> String,
    count: (DetailBoardTab) -> Int?,
    onSelect: (DetailBoardTab) -> Unit,
    pinned: Boolean,
    meta: MetaDetails,
    playLabel: String,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onPlayLongClick: (() -> Unit)?,
    onBack: () -> Unit,
    horizontalPadding: Dp,
    tint: Color?,
    modifier: Modifier = Modifier,
    onPlayAnchor: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val pinnedNow by rememberUpdatedState(pinned)
    Box(modifier.fillMaxWidth().height(DetailBoardTabsHeight).keepListStillWhilePinned { pinnedNow }.testTag("detail_board_tabs")) {
        PinnedBarBackground(scrolled = pinned, hazeState = null, tint = tint)
        val hairline by animateColorAsState(if (pinned) Color.Transparent else RedesignColors.hairline, tween(220), label = "board_tabs_hairline")
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = horizontalPadding).fillMaxWidth().height(1.dp).background(hairline))
        Row(Modifier.fillMaxSize().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = pinned,
                enter = fadeIn(tween(220)) + expandHorizontally(tween(BoardMotionMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(BoardMotionMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedesignIconButton(
                        contentDescription = stringResource(Res.string.action_back),
                        onClick = onBack,
                        vector = Icons.AutoMirrored.Rounded.ArrowBack,
                        size = 40.dp,
                        modifier = Modifier.testTag("detail_board_pinned_back"),
                    )
                    Spacer(Modifier.width(16.dp))
                    BoardTitleLogo(meta, height = 32.dp, maxWidth = 150.dp)
                    Spacer(Modifier.width(24.dp))
                }
            }
            RedesignTabs(
                items = tabs,
                selected = selected,
                label = label,
                onSelect = onSelect,
                count = count,
                modifier = Modifier.align(Alignment.Bottom),
            )
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = pinned, enter = fadeIn(tween(220)), exit = fadeOut(tween(140))) {
                BoardPlayButton(playLabel, playEnabled, onPlay, onPlayLongClick, compact = true, modifier = Modifier.testTag("detail_board_pinned_play").onGloballyPositioned { onPlayAnchor(it.boundsInRoot()) })
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).clip(RoundedCornerShape(height / 2)).background(RedesignColors.fill))
}

/**
 * The board while the title loads: the real board, tabs row and first shelf at their real sizes
 * with blank content, so nothing moves when the data arrives.
 */
@Composable
internal fun DetailBoardSkeleton(horizontalPadding: Dp, onBack: () -> Unit, isSeries: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("detail_board_skeleton")) {
        DetailBoard(
            expanded = false,
            hasSide = true,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            // The same column the board builds: Up next, Your progress, Watch it your way for a
            // series; Collection, Tonight, Watch it your way for a film.
            side = {
                Box(Modifier.weight(1f).fillMaxWidth().boardTile().testTag("detail_board_skeleton_tile"))
                Box(Modifier.height(if (isSeries) DetailBoardTileHeights.Progress else DetailBoardTileHeights.Tonight).fillMaxWidth().boardTile().testTag("detail_board_skeleton_tile"))
                Box(Modifier.height(DetailBoardTileHeights.YourWay).fillMaxWidth().boardTile().testTag("detail_board_skeleton_tile"))
            },
        ) {
            NuvioBackButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                containerColor = Color.Black.copy(alpha = 0.34f),
                showContainerOnDesktop = true,
                contentColor = RedesignColors.textPrimary,
                buttonSize = 48.dp,
                iconSize = 24.dp,
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 24.dp)) {
                Box(Modifier.width(300.dp).height(72.dp).clip(RoundedCornerShape(14.dp)).background(RedesignColors.fill))
                Spacer(Modifier.height(22.dp))
                SkeletonBar(420.dp, 14.dp)
                Spacer(Modifier.height(16.dp))
                SkeletonBar(560.dp, 12.dp)
                Spacer(Modifier.height(9.dp))
                SkeletonBar(520.dp, 12.dp)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBar(168.dp, 44.dp)
                    SkeletonBar(112.dp, 44.dp)
                    SkeletonBar(118.dp, 44.dp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(DetailBoardTabsHeight).padding(horizontal = horizontalPadding)) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                listOf(74.dp, 70.dp, 88.dp, 64.dp, 96.dp).forEach { SkeletonBar(it, 12.dp) }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
        }
        Row(Modifier.padding(start = horizontalPadding, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) {
                Column {
                    Box(Modifier.width(262.dp).height(147.dp).clip(RoundedCornerShape(14.dp)).background(RedesignColors.fill))
                    Spacer(Modifier.height(10.dp))
                    SkeletonBar(180.dp, 12.dp)
                }
            }
        }
    }
}

/** The crew and leading cast a credit line names: "Created by" or "Directed by", then "Starring". */
internal data class BoardCredits(val lead: List<MetaPerson>, val starring: List<MetaPerson>)

internal fun boardCredits(meta: MetaDetails, isSeries: Boolean, creatorRole: String, directorRole: String, writerRole: String): BoardCredits {
    val crewRoles = setOf(creatorRole, directorRole, writerRole, "Creator", "Director", "Writer").map { it.lowercase() }.toSet()
    val wanted = (if (isSeries) listOf(creatorRole, "Creator") else listOf(directorRole, "Director")).map { it.lowercase() }
    val lead = meta.cast.filter { it.role?.lowercase() in wanted }.distinctBy { it.name }
        .ifEmpty { if (isSeries) emptyList() else meta.director.map { MetaPerson(it, directorRole) } }
        .take(3)
    val starring = meta.cast.filter { it.role?.lowercase() !in crewRoles }.distinctBy { it.name }.take(3)
    return BoardCredits(lead, starring)
}

/**
 * Between the ratings and the overview: who made it and who is in it, as one line of prose that
 * wraps at word boundaries. Every name opens that person; nothing is ever cut off. Resting on a name
 * shows a small card with their portrait and role. Hidden when there is no one to name.
 */
@Composable
internal fun BoardCreditLine(
    credits: BoardCredits,
    isSeries: Boolean,
    onPerson: ((MetaPerson) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (credits.lead.isEmpty() && credits.starring.isEmpty()) return
    val leadLabel = stringResource(if (isSeries) Res.string.detail_board_created_by else Res.string.detail_board_directed_by)
    val starringLabel = stringResource(Res.string.detail_board_starring)
    val nameStyle = SpanStyle(color = Color(0xFFDFE3E7), fontWeight = FontWeight.SemiBold)
    val hoverStyle = TextLinkStyles(style = nameStyle, hoveredStyle = SpanStyle(color = RedesignColors.textPrimary, textDecoration = TextDecoration.Underline))
    val names = remember(credits, leadLabel, starringLabel, onPerson) {
        val spans = mutableListOf<CreditNameSpan>()
        val text = buildAnnotatedString {
            fun group(label: String, people: List<MetaPerson>) {
                append(label)
                append(' ')
                people.forEachIndexed { index, person ->
                    val start = length
                    if (onPerson != null) {
                        withLink(LinkAnnotation.Clickable(tag = person.name, styles = hoverStyle) { onPerson(person) }) { append(person.name) }
                    } else {
                        withStyle(nameStyle) { append(person.name) }
                    }
                    spans += CreditNameSpan(start, length, person)
                    if (index < people.lastIndex) append(", ")
                }
            }
            if (credits.lead.isNotEmpty()) group(leadLabel, credits.lead)
            if (credits.lead.isNotEmpty() && credits.starring.isNotEmpty()) append("  ·  ")
            if (credits.starring.isNotEmpty()) group(starringLabel, credits.starring)
        }
        text to spans.toList()
    }
    val (text, spans) = names
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The name under the pointer and the box of its last line, where the card hangs from.
    var pointed by remember { mutableStateOf<Pair<MetaPerson, Rect>?>(null) }
    var card by remember { mutableStateOf<Pair<MetaPerson, Rect>?>(null) }
    val cardState = remember { MutableTransitionState(false) }
    LaunchedEffect(pointed?.first) {
        val target = pointed
        if (target == null) {
            cardState.targetState = false
        } else {
            // A short rest before it opens, so sweeping across the line doesn't flash cards.
            delay(if (cardState.currentState) 80 else 420)
            card = target
            cardState.targetState = true
        }
    }
    Box(modifier.widthIn(max = 620.dp)) {
        Text(
            text,
            style = redesignText(13.5.sp, color = RedesignColors.textMuted, lineHeight = 19.sp),
            onTextLayout = { layout = it },
            modifier = Modifier.testTag("detail_board_credit_line").pointerInput(spans) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position
                        pointed = when {
                            event.type == PointerEventType.Exit || position == null -> null
                            else -> layout?.let { result -> creditNameAt(result, spans, position) }
                        }
                    }
                }
            },
        )
        val shown = card
        if (shown != null && (cardState.currentState || cardState.targetState)) {
            val density = LocalDensity.current
            val gap = with(density) { 8.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(shown.second.left.roundToInt(), shown.second.bottom.roundToInt() + gap),
                properties = PopupProperties(focusable = false),
            ) {
                AnimatedVisibility(
                    visibleState = cardState,
                    enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = TransformOrigin(0f, 0f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = TransformOrigin(0f, 0f)),
                ) {
                    CreditHoverCard(shown.first, isSeries, opens = onPerson != null)
                }
            }
        }
    }
}

private data class CreditNameSpan(val start: Int, val end: Int, val person: MetaPerson)

/** The name at [position], with the box of the line it sits on there (a name can wrap). */
private fun creditNameAt(layout: TextLayoutResult, spans: List<CreditNameSpan>, position: Offset): Pair<MetaPerson, Rect>? {
    val line = layout.getLineForVerticalPosition(position.y)
    if (position.y < layout.getLineTop(line) || position.y > layout.getLineBottom(line)) return null
    if (position.x < layout.getLineLeft(line) || position.x > layout.getLineRight(line)) return null
    val offset = layout.getOffsetForPosition(position)
    val span = spans.firstOrNull { offset in it.start until it.end } ?: return null
    // getOffsetForPosition snaps to the nearest character, so check the pointer is really on it.
    val box = layout.getBoundingBox(offset.coerceAtMost(span.end - 1))
    if (position.x < box.left - 2f && offset == span.start) return null
    val first = maxOf(span.start, layout.getLineStart(line))
    val last = minOf(span.end, layout.getLineEnd(line)) - 1
    if (last < first) return null
    val left = layout.getBoundingBox(first).left
    val right = layout.getBoundingBox(last).right
    return span.person to Rect(left, layout.getLineTop(line), right, layout.getLineBottom(line))
}

/** The credit line's card: the same 2:3 portrait as Cast & crew, the name, the role and the episode count. */
@Composable
private fun CreditHoverCard(person: MetaPerson, isSeries: Boolean, opens: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.width(300.dp).clip(shape).background(RedesignMenuSurface).border(1.dp, RedesignColors.hairline, shape)
            .padding(12.dp).testTag("detail_board_credit_card"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PersonPortrait(person.copy(episodeCount = null), isSeries = false, modifier = Modifier.width(84.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.name, style = redesignText(15.sp, FontWeight.Bold, lineHeight = 19.sp))
            person.role?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = redesignText(13.sp, color = RedesignColors.textSecondary, lineHeight = 17.sp))
            }
            person.episodeCount?.takeIf { isSeries && it > 0 }?.let {
                Text(stringResource(Res.string.detail_board_credit_episodes, it), style = redesignText(12.5.sp, color = RedesignColors.textMuted))
            }
            if (opens) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(Res.string.detail_board_credit_open), style = redesignText(12.sp, color = RedesignColors.textMuted))
            }
        }
    }
}

/**
 * Clicking a tab focuses it, and focus asks every scrolling parent to bring the tab into view. For
 * the pinned row that request reaches the page list, which scrolls to the row's place in the list
 * and drags the row (and the page) off the top. The row is always on screen while pinned, so its
 * requests stop here; unpinned, they pass on as usual.
 */
private fun Modifier.keepListStillWhilePinned(pinned: () -> Boolean): Modifier = this then KeepListStillElement(pinned)

private data class KeepListStillElement(val pinned: () -> Boolean) : ModifierNodeElement<KeepListStillNode>() {
    override fun create() = KeepListStillNode(pinned)
    override fun update(node: KeepListStillNode) { node.pinned = pinned }
}

private class KeepListStillNode(var pinned: () -> Boolean) : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        if (pinned()) return
        val own = requireLayoutCoordinates()
        bringIntoView {
            if (!childCoordinates.isAttached || !own.isAttached) null
            else boundsProvider()?.translate(own.localPositionOf(childCoordinates, Offset.Zero))
        }
    }
}
