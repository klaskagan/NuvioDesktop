package com.nuvio.app.features.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamQuickPickPanel
import com.nuvio.app.features.streams.StreamQuickPickRequest
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.format.formatLocalTime
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.components.BoardActionPill
import com.nuvio.app.features.details.components.BoardMetaLine
import com.nuvio.app.features.details.components.BoardPlayButton
import com.nuvio.app.features.details.components.BoardTitleLogo
import com.nuvio.app.features.details.components.DetailBoard
import com.nuvio.app.features.details.components.DetailBoardTabsBar
import com.nuvio.app.features.details.components.DetailBoardTabsHeight
import com.nuvio.app.features.details.components.DetailCollectionTile
import com.nuvio.app.features.details.components.DetailFeatureTile
import com.nuvio.app.features.details.components.DetailProgressTile
import com.nuvio.app.features.details.components.DetailRating
import com.nuvio.app.features.details.components.DetailSecondaryAction
import com.nuvio.app.features.details.components.DetailTonightTile
import com.nuvio.app.features.details.components.DetailUpNextTile
import com.nuvio.app.features.details.components.DetailYourWayTile
import com.nuvio.app.features.details.components.ImdbRatingSourceLabel
import com.nuvio.app.features.details.components.ImdbYellow
import com.nuvio.app.features.details.components.RatingsRow
import com.nuvio.app.features.details.components.ratingReference
import com.nuvio.app.features.details.components.rememberRatingState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.WatchProgressClock
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_choose_source
import nuvio.composeapp.generated.resources.detail_board_ends_at
import nuvio.composeapp.generated.resources.detail_board_left
import nuvio.composeapp.generated.resources.detail_board_library
import nuvio.composeapp.generated.resources.detail_board_lists
import nuvio.composeapp.generated.resources.detail_board_tab_cast
import nuvio.composeapp.generated.resources.detail_board_tab_reviews
import nuvio.composeapp.generated.resources.detail_board_tab_stats
import nuvio.composeapp.generated.resources.detail_board_tonight
import nuvio.composeapp.generated.resources.detail_board_trailer
import nuvio.composeapp.generated.resources.detail_board_watched
import nuvio.composeapp.generated.resources.media_movie
import nuvio.composeapp.generated.resources.media_series
import nuvio.composeapp.generated.resources.random_episode_title
import nuvio.composeapp.generated.resources.shuffle_stop
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Download
import com.nuvio.app.features.details.components.DetailTvPicker
import com.nuvio.app.features.details.components.DetailBoardTileHeights
import nuvio.composeapp.generated.resources.detail_board_on_tv
import nuvio.composeapp.generated.resources.detail_board_watch_party
import nuvio.composeapp.generated.resources.detail_board_download
import com.nuvio.app.features.details.components.BoardRateControl
import com.nuvio.app.features.details.components.rememberFavoriteState
import com.nuvio.app.features.details.components.BoardCreditLine
import com.nuvio.app.features.details.components.boardCredits
import nuvio.composeapp.generated.resources.person_role_creator
import nuvio.composeapp.generated.resources.person_role_director
import nuvio.composeapp.generated.resources.person_role_writer

/** The episode the primary button would play, as the Up next tile shows it. */
internal data class DetailBoardUpNext(
    val kicker: String,
    val title: String,
    val artwork: String?,
    val progressFraction: Float?,
    val actionLabel: String,
)

/** What the desktop board needs beyond the shared [DetailSectionArgs]. */
@Stable
/** What the expanded tile offers when a picked trailer can't play in Nuvio. */
internal class DetailBoardTrailerFailure(val onOpenExternally: () -> Unit, val onClose: () -> Unit)

internal class DetailBoardArgs(
    val onPlayAnchor: (Rect) -> Unit,
    val onPinnedPlayAnchor: (Rect) -> Unit,
    val horizontalPadding: Dp,
    val tabs: List<DetailBoardTab>,
    val selectedTab: DetailBoardTab,
    val onSelectTab: (DetailBoardTab) -> Unit,
    val pinned: () -> Boolean,
    val expanded: Boolean,
    /** The picked trailer's title, shown in the expanded tile. */
    val expandedTitle: String? = null,
    /** The picked trailer couldn't play here: the expanded tile says so. */
    val expandedFailure: DetailBoardTrailerFailure? = null,
    val onToggleExpanded: () -> Unit,
    val trailer: (@Composable (Modifier, Boolean) -> Unit)?,
    val trailerReady: Boolean,
    val trailerMuted: Boolean,
    val onMuteToggle: () -> Unit,
    val upNext: DetailBoardUpNext?,
    val progress: DetailBoardProgress?,
    val showProgress: Boolean,
    val movieResumePositionMs: Long?,
    val isCollectionItemWatched: (MetaPreview) -> Boolean,
    val showOverallRatings: Boolean,
    val tint: Color?,
    val onBack: () -> Unit,
    val onHeightChanged: (Int) -> Unit,
    val onArtworkLoaded: (Painter) -> Unit,
)

@Composable
internal fun detailBoardTabLabel(tab: DetailBoardTab, items: List<MetaScreenSectionItem>): String {
    fun title(key: MetaScreenSectionKey) = items.firstOrNull { it.key == key }?.title.orEmpty()
    return when (tab) {
        DetailBoardTab.Episodes -> title(MetaScreenSectionKey.EPISODES)
        DetailBoardTab.Stats -> stringResource(Res.string.detail_board_tab_stats)
        DetailBoardTab.Cast -> stringResource(Res.string.detail_board_tab_cast)
        DetailBoardTab.Reviews -> stringResource(Res.string.detail_board_tab_reviews)
        DetailBoardTab.Trailers -> title(MetaScreenSectionKey.TRAILERS)
        DetailBoardTab.Gallery -> title(MetaScreenSectionKey.GALLERY)
        DetailBoardTab.More -> title(MetaScreenSectionKey.MORE_LIKE_THIS)
        DetailBoardTab.Details -> title(MetaScreenSectionKey.DETAILS)
    }
}

private fun detailBoardTabCount(tab: DetailBoardTab, meta: MetaDetails, args: DetailSectionArgs): Int? = when (tab) {
    DetailBoardTab.Episodes -> meta.videos.count { (it.season ?: 0) > 0 && it.episode != null }.takeIf { it > 0 }
    DetailBoardTab.Cast -> meta.cast.size.takeIf { it > 0 }
    DetailBoardTab.Trailers -> meta.trailers.size.takeIf { it > 0 }
    DetailBoardTab.Gallery -> args.gallery.size.takeIf { it > 0 }
    else -> null
}

/**
 * The desktop Details page as a board: the title tile and personal tiles, the section tabs (which
 * pin), and the selected tab's sections. Sections come from the same [configuredMetaSectionItems]
 * every other layout uses, so a new section needs no board code beyond its tab. What a tab shows is
 * decided from [args] itself, so board-only inputs (gallery, reviews source) always count.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.detailBoardItems(
    settings: MetaScreenSettingsUiState,
    args: DetailSectionArgs,
    board: DetailBoardArgs,
) {
    item(key = "detail-board", contentType = "detail-board") {
        DetailBoardItem(args, board)
    }
    if (board.tabs.isEmpty()) return
    stickyHeader(key = "detail-board-tabs", contentType = "detail-board-tabs") {
        DetailBoardTabsBar(
            tabs = board.tabs,
            selected = board.selectedTab,
            label = { detailBoardTabLabel(it, settings.items) },
            count = { detailBoardTabCount(it, args.meta, args) },
            onSelect = board.onSelectTab,
            pinned = board.pinned(),
            meta = args.meta,
            playLabel = args.playButtonLabel,
            playEnabled = args.isPrimaryPlayEnabled,
            onPlay = args.onPrimaryPlayClick,
            onPlayLongClick = args.onPrimaryPlayLongClick.takeIf { args.showManualPlayOption },
            onBack = board.onBack,
            horizontalPadding = board.horizontalPadding,
            tint = board.tint,
            onPlayAnchor = board.onPinnedPlayAnchor,
        )
    }
    item(key = "detail-board-gap", contentType = "detail-spacer") { Spacer(Modifier.height(20.dp)) }
    val season = args.episodeListCurrentSeason
    if (board.selectedTab == DetailBoardTab.Stats) {
        if (season != null) {
            item(key = "detail-board-stats", contentType = "detail-series-audience") {
                Box(Modifier.animateItem().fillMaxWidth().padding(horizontal = board.horizontalPadding).padding(bottom = 20.dp)) {
                    SeriesAudienceSection(
                        meta = args.meta,
                        season = season,
                        progress = args.seriesProgress,
                        state = args.audienceState,
                        onSeasonSelected = args.onEpisodeListSeasonSelect,
                        onOpenSeason = {
                            args.onEpisodeListSeasonSelect(it)
                            board.onSelectTab(DetailBoardTab.Episodes)
                        },
                    )
                }
            }
        }
        detailBoardFillItem(args)
        return
    }
    val sections = detailBoardTabSections(board.selectedTab, settings.items, args::hasContent)
    configuredMetaSectionItems(
        settings = settings.copy(items = sections, tabLayout = false),
        args = args,
        isTablet = true,
        contentHorizontalPadding = board.horizontalPadding,
        contentMaxWidth = Dp.Unspecified,
        showHeaders = sections.size > 1,
        showAudience = false,
        animateItems = true,
    )
    detailBoardFillItem(args)
}

private const val DetailBoardFillKey = "detail-board-fill"

/** Board, tabs, then the gap: the first item of the selected tab's content. */
private const val DetailBoardFirstContentIndex = 2

private fun LazyListScope.detailBoardFillItem(args: DetailSectionArgs) {
    item(key = DetailBoardFillKey, contentType = "detail-spacer") {
        val density = LocalDensity.current
        val tabsPx = with(density) { DetailBoardTabsHeight.roundToPx() }
        val fillPx by remember(args.detailListState, tabsPx) {
            derivedStateOf {
                val info = args.detailListState.layoutInfo
                detailBoardFillPx(
                    visible = info.visibleItemsInfo.map { DetailBoardVisibleItem(it.index, it.size, it.key == DetailBoardFillKey) },
                    viewportPx = info.viewportEndOffset - info.viewportStartOffset,
                    tabsPx = tabsPx,
                    firstContentIndex = DetailBoardFirstContentIndex,
                )
            }
        }
        Spacer(Modifier.height(with(density) { fillPx.toDp() }))
    }
}

@Composable
private fun DetailBoardItem(args: DetailSectionArgs, board: DetailBoardArgs) {
    val meta = args.meta
    val isSeries = args.hasEpisodes || meta.type == "series"
    // On TV is used most, then Watch party (the user's order); unavailable actions are left out.
    val yourWay = buildList {
        args.tvHandoff?.let { handoff ->
            add(
                DetailSecondaryAction(
                    label = stringResource(Res.string.detail_board_on_tv),
                    icon = Icons.Rounded.Tv,
                    onClick = handoff::open,
                    tag = "detail_your_way_tv",
                    popup = { dismiss -> DetailTvPicker(handoff, dismiss) },
                ),
            )
        }
        args.onWatchPartyClick?.let { party ->
            add(DetailSecondaryAction(label = stringResource(Res.string.detail_board_watch_party), icon = Icons.Rounded.Groups, onClick = party, tag = "detail_your_way_party"))
        }
        args.onDownloadClick?.let { download ->
            add(DetailSecondaryAction(label = stringResource(Res.string.detail_board_download), icon = Icons.Rounded.Download, onClick = download, tag = "detail_your_way_download"))
        }
        args.onShuffleClick?.let { onShuffle ->
            add(
                DetailSecondaryAction(
                    label = stringResource(if (args.shuffleEnabled) Res.string.shuffle_stop else Res.string.random_episode_title),
                    icon = Icons.Rounded.Shuffle,
                    isActive = args.shuffleEnabled,
                    onClick = onShuffle,
                    tag = "detail_your_way_shuffle",
                ),
            )
        }
        args.onPrimaryPlayLongClick?.takeIf { args.showManualPlayOption && args.isPrimaryPlayEnabled }?.let { choose ->
            add(DetailSecondaryAction(label = stringResource(Res.string.detail_board_choose_source), icon = Icons.Rounded.Tune, onClick = choose, tag = "detail_your_way_choose"))
        }
        args.onSaveLongClick?.let { lists ->
            add(DetailSecondaryAction(label = stringResource(Res.string.detail_board_lists), icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = lists, tag = "detail_your_way_lists"))
        }
    }
    val collection = meta.collectionItems.takeIf { !isSeries && args.hasCollectionSection }
    val remainingMs = if (isSeries) null else detailBoardRemainingMs(meta.runtime, board.movieResumePositionMs)
    val upNext = board.upNext.takeIf { isSeries }
    val showProgress = isSeries && board.showProgress
    val hasSide = upNext != null || showProgress || collection != null || remainingMs != null || yourWay.isNotEmpty()

    DetailBoard(
        expanded = board.expanded,
        hasSide = hasSide,
        modifier = Modifier.padding(horizontal = board.horizontalPadding).onSizeChanged { board.onHeightChanged(it.height) },
        side = {
            // The first tile present takes the spare height, so the column always ends flush with the board.
            var filled = false
            fun fill(height: Dp): Modifier = if (filled) Modifier.fillMaxWidth().height(height) else Modifier.fillMaxWidth().weight(1f).also { filled = true }
            if (upNext != null) {
                DetailUpNextTile(
                    kicker = upNext.kicker,
                    title = upNext.title,
                    artwork = upNext.artwork,
                    progressFraction = upNext.progressFraction,
                    actionLabel = upNext.actionLabel,
                    onClick = args.onPrimaryPlayClick.takeIf { args.isPrimaryPlayEnabled },
                    onLongClick = args.onPrimaryPlayLongClick.takeIf { args.showManualPlayOption },
                    modifier = fill(DetailBoardTileHeights.UpNext),
                )
            }
            if (collection != null) {
                DetailCollectionTile(
                    name = meta.collectionName.orEmpty(),
                    items = collection,
                    isWatched = board.isCollectionItemWatched,
                    onOpen = args.onOpenMeta,
                    onShowAll = { board.onSelectTab(DetailBoardTab.More) },
                    modifier = fill(DetailBoardTileHeights.Collection),
                )
            }
            if (showProgress) {
                DetailProgressTile(
                    progress = board.progress,
                    onOpenSeason = {
                        args.onEpisodeListSeasonSelect(it)
                        board.onSelectTab(DetailBoardTab.Episodes)
                    },
                    modifier = fill(DetailBoardTileHeights.Progress),
                )
            }
            if (remainingMs != null) {
                val resumed = (board.movieResumePositionMs ?: 0L) > 0L
                val runtime = formatRuntimeFromMinutes((remainingMs / 60_000L).toInt().coerceAtLeast(1))
                val endsAt = remember(remainingMs) { formatLocalTime(WatchProgressClock.nowEpochMs() + remainingMs) }
                val totalMs = (parseRuntimeMinutes(meta.runtime) ?: 0) * 60_000L
                DetailTonightTile(
                    kicker = stringResource(Res.string.detail_board_tonight),
                    headline = if (resumed) stringResource(Res.string.detail_board_left, runtime) else runtime,
                    line = stringResource(Res.string.detail_board_ends_at, endsAt),
                    progressFraction = if (resumed && totalMs > 0) (board.movieResumePositionMs ?: 0L).toFloat() / totalMs else null,
                    modifier = fill(DetailBoardTileHeights.Tonight),
                )
            }
            // Your way sizes to its rows, so two actions don't leave an empty second row.
            if (yourWay.isNotEmpty()) DetailYourWayTile(yourWay, if (filled) Modifier.fillMaxWidth() else fill(DetailBoardTileHeights.YourWay))
        },
    ) {
        DetailFeatureTile(
            meta = meta,
            expanded = board.expanded,
            expandedTitle = board.expandedTitle,
            expandedFailure = board.expandedFailure,
            trailer = board.trailer,
            trailerReady = board.trailerReady,
            trailerMuted = board.trailerMuted,
            onMuteToggle = board.onMuteToggle,
            onToggleExpanded = board.onToggleExpanded,
            onBack = board.onBack,
            onArtworkLoaded = board.onArtworkLoaded,
        ) {
            BoardTitleLogo(meta, height = 104.dp, maxWidth = 440.dp)
            Spacer(Modifier.height(14.dp))
            BoardMetaLine(meta, stringResource(if (isSeries) Res.string.media_series else Res.string.media_movie))
            BoardRatings(meta, args.isMdbListActive, board.showOverallRatings)
            val creatorRole = stringResource(Res.string.person_role_creator)
            val directorRole = stringResource(Res.string.person_role_director)
            val writerRole = stringResource(Res.string.person_role_writer)
            val credits = remember(meta, isSeries) { boardCredits(meta, isSeries, creatorRole, directorRole, writerRole) }
            if (credits.lead.isNotEmpty() || credits.starring.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                BoardCreditLine(credits, isSeries, onPerson = args.onCastClick?.let { open -> { person -> open(person, null) } })
            }
            meta.description?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = overview,
                    style = redesignText(14.5.sp, color = Color(0xFFDFE3E7), lineHeight = 21.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(580.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            // While the Rate strip is open, Library, Watched and Trailer give up their labels so the
            // row keeps its width.
            var rateOpen by remember(meta.id) { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BoardPlayButton(
                    label = args.playButtonLabel,
                    enabled = args.isPrimaryPlayEnabled,
                    onClick = args.onPrimaryPlayClick,
                    onLongClick = args.onPrimaryPlayLongClick.takeIf { args.showManualPlayOption },
                    modifier = Modifier.testTag("detail_board_play").onGloballyPositioned { board.onPlayAnchor(it.boundsInRoot()) },
                )
                BoardActionPill(
                    label = stringResource(Res.string.detail_board_library),
                    icon = if (args.isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                    active = args.isSaved,
                    onClick = args.onSaveClick,
                    onLongClick = args.onSaveLongClick,
                    iconOnly = rateOpen,
                    modifier = Modifier.testTag("detail_board_library"),
                )
                BoardActionPill(
                    label = stringResource(Res.string.detail_board_watched),
                    icon = if (args.isWatched) Icons.Rounded.Check else Icons.Rounded.CheckCircleOutline,
                    active = args.isWatched,
                    onClick = args.onWatchedClick,
                    iconOnly = rateOpen,
                    modifier = Modifier.testTag("detail_board_watched"),
                )
                val trailer = remember(meta.trailers) { rankDesktopHeroTrailers(meta.trailers).firstOrNull() ?: meta.trailers.firstOrNull() }
                if (trailer != null) {
                    BoardActionPill(
                        label = stringResource(Res.string.detail_board_trailer),
                        icon = Icons.Rounded.Movie,
                        onClick = { args.onTrailerClick(trailer) },
                        iconOnly = rateOpen,
                        modifier = Modifier.testTag("detail_board_trailer"),
                    )
                }
                val ratingMedia = remember(meta) { meta.ratingReference() }
                val ratingState by rememberRatingState(ratingMedia)
                val favoriteState = rememberFavoriteState(ratingMedia)
                // Rate B rates the whole title; episodes are rated from their card's menu.
                if (ratingState.isAvailable || favoriteState.isAvailable) {
                    BoardRateControl(media = ratingMedia, rating = ratingState, favorite = favoriteState, onOpenChange = { rateOpen = it })
                }
            }
        }
    }
}

/** MDBList's rating row when it is on; otherwise the IMDb score, as the page has always shown. */
@Composable
private fun BoardRatings(meta: MetaDetails, isMdbListActive: Boolean, showOverallRatings: Boolean) {
    if (isMdbListActive && meta.externalRatings.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        RatingsRow(ratings = meta.externalRatings, maxItems = 6)
        return
    }
    val imdb = meta.imdbRating?.takeIf { raw -> raw.toDoubleOrNull()?.let { it > 0.0 } == true }
    if (imdb != null && showOverallRatings) {
        Spacer(Modifier.height(10.dp))
        val style = redesignText(14.5.sp, FontWeight.Bold, ImdbYellow)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImdbRatingSourceLabel(storeTextStyle = style, storeTextColor = ImdbYellow)
            Spacer(Modifier.width(6.dp))
            Text(imdb, style = style.copy(color = RedesignColors.textPrimary))
        }
    }
}

/**
 * Quick pick over the board: a scrim, and the panel under the button that opened it (above it when
 * there is no room below). Escape, the scrim and the close button all dismiss it.
 */
@Composable
internal fun BoxScope.DetailQuickPickOverlay(
    open: Boolean,
    anchor: Rect?,
    request: StreamQuickPickRequest,
    onPick: (StreamItem) -> Unit,
    onAllSources: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    if (open) {
        // Esc is caught at the window before any back handler, so an open panel claims it.
        DisposableEffect(Unit) {
            val handler: () -> Boolean = { currentDismiss(); true }
            AppKeyboardShortcuts.overlayEscape = handler
            onDispose { if (AppKeyboardShortcuts.overlayEscape === handler) AppKeyboardShortcuts.overlayEscape = null }
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var panel by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot(); container = it.size }) {
        AnimatedVisibility(visible = open, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color(0x8C050607))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    .testTag("stream_quick_pick_scrim"),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 12 },
            exit = fadeOut(tween(140)),
            modifier = Modifier.offset {
                val margin = with(density) { 16.dp.roundToPx() }
                val gap = with(density) { 12.dp.roundToPx() }
                val left = (anchor?.left ?: 0f) - origin.x
                val below = (anchor?.bottom ?: 0f) - origin.y + gap
                val above = (anchor?.top ?: 0f) - origin.y - gap - panel.height
                val x = left.roundToInt().coerceIn(margin, (container.width - panel.width - margin).coerceAtLeast(margin))
                val y = if (below + panel.height <= container.height - margin || above < margin) below.roundToInt() else above.roundToInt()
                IntOffset(x, y.coerceAtLeast(margin))
            },
        ) {
            StreamQuickPickPanel(
                request = request,
                onPick = onPick,
                onAllSources = onAllSources,
                onDismiss = onDismiss,
                modifier = Modifier.onSizeChanged { panel = it },
            )
        }
    }
}
