package com.nuvio.app.features.home.components

import com.nuvio.app.core.ui.displayRatingText
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.secondaryClick
import com.nuvio.app.core.ui.PosterZoomAnchor
import com.nuvio.app.core.ui.PosterZoomAnchorHolder
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.continueWatchingItemKey
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_resume
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.cw_action_go_to_details
import nuvio.composeapp.generated.resources.home_board_up_next
import org.jetbrains.compose.resources.stringResource

/** How many resume items the board shows itself; the Continue Watching row below holds the rest. */
internal const val HOME_BOARD_RESUME_ITEMS = 4

/** Below this content width the board is the featured title alone and resuming stays in its row. */
internal val HomeBoardSideColumnMinWidth = 900.dp

private val BoardTileShape = RoundedCornerShape(18.dp)

/** A pause after the hover delay, so sweeping the pointer across a row never starts a stream. */
private const val LEAD_TRAILER_DEBOUNCE_MILLIS = 500L
private val BoardSideColumnWidth = 352.dp
private val BoardGap = 14.dp
private val tileScrim = Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color(0xEB08090B))

/** The resume items the board takes: the first is the Resume tile, the rest the Up next list. */
internal fun homeBoardResumeItems(items: List<ContinueWatchingItem>, hasSideColumn: Boolean): List<ContinueWatchingItem> =
    if (hasSideColumn) items.take(HOME_BOARD_RESUME_ITEMS) else emptyList()

/**
 * Desktop Home's opening board: the featured carousel as a tile beside a Resume tile and an Up
 * next list. With nothing to resume, or while [expanded], the featured tile takes the whole width.
 */
@Composable
internal fun HomeBoard(
    height: Dp,
    expanded: Boolean,
    resumeItems: List<ContinueWatchingItem>,
    onResumeClick: ((ContinueWatchingItem) -> Unit)?,
    onResumeLongPress: ((ContinueWatchingItem) -> Unit)?,
    modifier: Modifier = Modifier,
    onResumeDetails: ((ContinueWatchingItem) -> Unit)? = null,
    featured: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth().height(height).testTag("home_board")) {
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(BoardTileShape)
                .border(1.dp, RedesignColors.hairline, BoardTileShape),
        ) { featured() }
        val resume = resumeItems.firstOrNull()
        // While the featured tile is expanded it takes the whole board and the side column folds away.
        AnimatedVisibility(
            visible = resume != null && !expanded,
            enter = fadeIn(tween(240, delayMillis = 120)) + expandHorizontally(tween(360, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
            exit = fadeOut(tween(160)) + shrinkHorizontally(tween(360, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
        ) {
            val shown = resume ?: return@AnimatedVisibility
            val upNext = resumeItems.drop(1)
            Column(
                Modifier.padding(start = BoardGap).width(BoardSideColumnWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(BoardGap),
            ) {
                HomeResumeTile(
                    item = shown,
                    onClick = onResumeClick,
                    onLongPress = onResumeLongPress,
                    onDetails = onResumeDetails,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (upNext.isNotEmpty()) {
                    HomeUpNextCard(
                        items = upNext,
                        onClick = onResumeClick,
                        onLongPress = onResumeLongPress,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun ContinueWatchingItem.boardArtwork(): String? =
    background ?: episodeThumbnail ?: imageUrl ?: poster

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.boardClickable(
    interaction: MutableInteractionSource,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    zoomImageUrl: String? = null,
    zoomCornerRadius: Dp = 18.dp,
): Modifier = if (onClick == null) this else hoverable(interaction).posterCardClickable(
    // The shared poster click: right-click and press-and-hold lift this card into the same
    // zoomed options menu the Continue Watching row opens. The board keeps its own hover look.
    onClick = onClick,
    onLongClick = onLongClick,
    zoomImageUrl = zoomImageUrl,
    zoomCornerRadius = zoomCornerRadius,
    hoverScaleEnabled = false,
)

@Composable
private fun HomeResumeTile(
    item: ContinueWatchingItem,
    onClick: ((ContinueWatchingItem) -> Unit)?,
    onLongPress: ((ContinueWatchingItem) -> Unit)?,
    modifier: Modifier = Modifier,
    onDetails: ((ContinueWatchingItem) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(180), label = "resume_border")
    Box(
        modifier.clip(BoardTileShape).background(RedesignColors.fill).border(1.dp, border, BoardTileShape)
            .boardClickable(interaction, onClick?.let { { it(item) } }, onLongPress?.let { { it(item) } }, item.boardArtwork())
            .testTag("home_board_resume"),
    ) {
        NuvioAsyncImage(
            model = item.boardArtwork(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(tileScrim))
        if (onDetails != null) {
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                HomeResumeDetailsButton(onClick = { onDetails(item) })
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(ResumeControlHeight).clip(CircleShape)
                    .background(RedesignColors.textPrimary).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF0B0C0E), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text(stringResource(Res.string.action_resume), style = redesignText(12.5.sp, FontWeight.Bold, Color(0xFF0B0C0E)))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.compose_settings_page_continue_watching).uppercase(),
                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textSecondary, letterSpacing = 0.1.em),
                maxLines = 1,
            )
            Text(
                text = item.title,
                style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = redesignText(12.5.sp, FontWeight.Medium, RedesignColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.progressFraction > 0f) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f))) {
                    Box(Modifier.fillMaxWidth(item.progressFraction.coerceIn(0f, 1f)).fillMaxHeight().background(RedesignColors.textPrimary))
                }
            }
        }
    }
}

private val ResumeControlHeight = 26.dp
private val UpNextThumbRadius = 7.dp

/** The Resume tile's small (i): opens the title's Details while the tile itself resumes. */
@Composable
private fun HomeResumeDetailsButton(onClick: () -> Unit) {
    // Styled like the Resume pill beside it: white fill, dark icon.
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(ResumeControlHeight).clip(CircleShape).background(RedesignColors.textPrimary)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag("home_board_resume_details"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.cw_action_go_to_details),
            tint = Color(0xFF0B0C0E),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun HomeUpNextCard(
    items: List<ContinueWatchingItem>,
    onClick: ((ContinueWatchingItem) -> Unit)?,
    onLongPress: ((ContinueWatchingItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(BoardTileShape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, BoardTileShape)
            .padding(horizontal = 10.dp, vertical = 12.dp).testTag("home_board_up_next"),
    ) {
        Text(
            text = stringResource(Res.string.home_board_up_next).uppercase(),
            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        items.forEach { item ->
            androidx.compose.runtime.key(continueWatchingItemKey(item)) {
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val fill by animateColorAsState(if (hovered) RedesignColors.fill else Color.Transparent, tween(160), label = "up_next_row")
                // The options menu zooms the row's 16:9 still, not the thin row itself.
                var thumbBounds by remember { mutableStateOf<Rect?>(null) }
                val artwork = item.boardArtwork()
                val openOptions = onLongPress?.let { longPress ->
                    {
                        thumbBounds?.let { bounds ->
                            PosterZoomAnchorHolder.stash(PosterZoomAnchor(bounds, artwork, UpNextThumbRadius))
                        }
                        longPress(item)
                    }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
                        .then(
                            if (onClick == null) Modifier else Modifier.hoverable(interaction).combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onLongClick = openOptions,
                                onClick = { onClick(item) },
                            ).secondaryClick(openOptions),
                        )
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NuvioAsyncImage(
                        model = artwork,
                        contentDescription = null,
                        modifier = Modifier.width(64.dp).height(36.dp)
                            .onGloballyPositioned { thumbBounds = it.boundsInRoot() }
                            .clip(RoundedCornerShape(UpNextThumbRadius)).background(RedesignColors.fill),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(item.title, style = redesignText(13.5.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotBlank()) {
                            Text(item.subtitle, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.size(26.dp).clip(CircleShape).background(RedesignColors.fillStrong), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = RedesignColors.textPrimary, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

/**
 * The wide card that opens a Home row: the row's first title with its landscape art, a meta line
 * and a line of plot. It is as tall as the row's posters so the shelf keeps one baseline.
 *
 * The card is there from the first frame. Catalog previews often carry no landscape art, so it is
 * looked up in place (the same lookup landscape poster cards use) and fades in; the poster is the
 * last resort. Showing the card only once art was known made the row jump after it had loaded.
 */
@Composable
internal fun HomeRowLeadCard(
    item: MetaPreview,
    width: Dp,
    height: Dp,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(180), label = "lead_border")
    Box(
        modifier.width(width).height(height).clip(shape).background(RedesignColors.fill).border(1.dp, border, shape)
            .boardClickable(interaction, onClick, onLongClick, item.banner ?: item.landscapePoster ?: item.poster, 14.dp).testTag("home_row_lead"),
    ) {
        val supplied = item.banner ?: item.landscapePoster
        val lookup = rememberLandscapeMediaArtwork(item.id, item.type, enabled = supplied.isNullOrBlank() && isTitleArtworkMedia(item.type))
        val art = supplied?.takeIf { it.isNotBlank() }
            ?: lookup.artwork?.let { it.cleanBackdrop ?: it.backdrop }
            ?: item.poster.takeIf { !lookup.isLoading }
        Crossfade(targetState = art, animationSpec = tween(260), label = "lead_art", modifier = Modifier.fillMaxSize()) { url ->
            if (url != null) {
                NuvioAsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Hovering plays the title's trailer inside the card, under the same settings as the poster
        // hover preview (trailer on hover, sound, start position).
        val posterCardStyle = rememberPosterCardStyleUiState()
        val trailersEnabled = AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
            posterCardStyle.hoverPreviewEnabled && posterCardStyle.hoverPreviewTrailerEnabled
        val screenActive = LocalScreenActive.current
        var trailer by remember(item.type, item.id) { mutableStateOf<TrailerPlaybackSource?>(null) }
        LaunchedEffect(hovered, trailersEnabled, screenActive, item.type, item.id) {
            if (!hovered || !trailersEnabled || !screenActive) {
                trailer = null
                return@LaunchedEffect
            }
            delay(posterCardStyle.hoverPreviewOpenDelayMillis.toLong() + LEAD_TRAILER_DEBOUNCE_MILLIS)
            trailer = resolveHomePosterHoverTrailerPlaybackSource(item)
        }
        HomePosterHoverTrailer(
            playbackSource = trailer,
            soundEnabled = posterCardStyle.hoverPreviewTrailerSoundEnabled,
            startPositionSeconds = posterCardStyle.hoverPreviewTrailerStartSeconds,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(tileScrim))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp)) {
            val meta = listOfNotNull(
                item.genres.firstOrNull(),
                item.releaseInfo?.takeIf { it.isNotBlank() },
                displayRatingText(item.imdbRating)?.let { "★ $it" },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta.uppercase(),
                    style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textSecondary, letterSpacing = 0.08.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.name,
                style = redesignText(17.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.description?.takeIf { it.isNotBlank() }?.let { plot ->
                // The plot steps back while the trailer plays; its space stays so the title doesn't move.
                val plotAlpha by animateFloatAsState(if (trailer != null) 0f else 1f, tween(260), label = "lead_plot")
                Text(
                    text = plot,
                    style = redesignText(12.5.sp, color = RedesignColors.textSecondary, lineHeight = 17.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).graphicsLayer { alpha = plotAlpha },
                )
            }
        }
    }
}
