package com.nuvio.app.features.details.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.resources.stringResource

/** The board's height; the loading skeleton uses the same value so nothing moves when data arrives. */
internal val DetailBoardHeight = 440.dp

/** The pinned tabs row's height. */
internal val DetailBoardTabsHeight = 56.dp

private val BoardSideColumnWidth = 352.dp
private val BoardGap = 14.dp
private val tileScrim = Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color(0xEB08090B))
private const val BoardMotionMillis = 360

private fun Modifier.boardTile(border: Color = RedesignColors.hairline): Modifier =
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
private fun BoardKicker(text: String, color: Color = RedesignColors.textMuted, modifier: Modifier = Modifier) {
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
    side: @Composable ColumnScope.() -> Unit,
    featured: @Composable BoxScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().height(DetailBoardHeight).testTag("detail_board")) {
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
    Row(
        modifier.height(44.dp).clip(CircleShape).background(fill).border(1.dp, RedesignColors.hairline, CircleShape)
            .boardClickable(interaction, onClick, onLongClick)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = redesignText(14.sp, FontWeight.SemiBold, content), maxLines = 1)
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
        Modifier.align(Alignment.BottomStart).padding(start = 28.dp, end = 28.dp, bottom = 24.dp).widthIn(max = 760.dp)
            .graphicsLayer { alpha = textAlpha },
        content = titleBlock,
    )
}

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
    Column(modifier.boardTile().padding(horizontal = 16.dp, vertical = 14.dp).testTag("detail_board_progress")) {
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
    }
}

/** Watch it your way: other ways into the title (shuffle, choosing a source, lists). */
@Composable
internal fun DetailYourWayTile(actions: List<DetailSecondaryAction>, modifier: Modifier = Modifier) {
    Column(modifier.boardTile().padding(horizontal = 12.dp, vertical = 12.dp).testTag("detail_board_your_way")) {
        BoardKicker(stringResource(Res.string.detail_board_your_way), modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.take(3).forEach { action ->
                val interaction = remember(action.label) { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val fill by animateColorAsState(
                    when {
                        action.isActive -> RedesignColors.textPrimary
                        hovered -> RedesignColors.fillStrong
                        else -> RedesignColors.fill
                    },
                    tween(160),
                    label = "your_way_fill",
                )
                val content = if (action.isActive) RedesignOnLight else RedesignColors.textPrimary
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(fill)
                        .boardClickable(interaction, action.onClick, action.onLongClick)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Icon(action.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(action.label, style = redesignText(12.5.sp, FontWeight.SemiBold, content), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Column(modifier.boardTile().padding(horizontal = 12.dp, vertical = 12.dp).testTag("detail_board_collection")) {
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
                    Box(Modifier.fillMaxHeight().width(52.dp).clip(RoundedCornerShape(8.dp)).background(RedesignColors.fill)) {
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
    Column(modifier.boardTile().padding(horizontal = 16.dp, vertical = 14.dp).testTag("detail_board_tonight")) {
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
    Box(modifier.fillMaxWidth().height(DetailBoardTabsHeight).testTag("detail_board_tabs")) {
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
internal fun DetailBoardSkeleton(horizontalPadding: Dp, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("detail_board_skeleton")) {
        DetailBoard(
            expanded = false,
            hasSide = true,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            side = {
                Box(Modifier.weight(1f).fillMaxWidth().boardTile())
                Box(Modifier.height(118.dp).fillMaxWidth().boardTile())
                Box(Modifier.height(104.dp).fillMaxWidth().boardTile())
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
