package com.nuvio.app.features.search

import androidx.compose.animation.AnimatedVisibility
import com.nuvio.app.core.ui.rememberRedesignMenuSurface
import com.nuvio.app.core.ui.LocalRedesignMenuSurface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.features.details.components.DetailBoardTabsHeight
import com.nuvio.app.features.details.components.keepListStillWhilePinned
import dev.chrisbanes.haze.HazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.LocalDetailTopBarState
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.PinnedBarBackground
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import com.nuvio.app.core.ui.LocalBackdropFocusController
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.NavLayer
import com.nuvio.app.core.ui.NavSide
import com.nuvio.app.core.ui.TrimmedLogoState
import com.nuvio.app.core.ui.rememberTrimmedLogo
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.core.ui.navTransitionLayer
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.StreamingCollection
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.LoadMoreOnScrollEffect
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * One discovery session per service, kept for the app's lifetime so returning from a title (or
 * reopening the folder) shows the loaded page instead of reloading it. Separate from Search's
 * session: the two pages keep their own tab, "View all" and hero state.
 */
internal object StreamingProviderSessions {
    private val sessions = mutableMapOf<String, SearchDiscoverySession>()

    /**
     * [freshVisit] is true when the folder is opened anew (not when a title's page is popped off
     * it): the loaded rows are kept, but a "View all" grid left open last time is closed, so the
     * page always opens on its rows.
     */
    fun sessionFor(selection: SearchDiscovery, freshVisit: Boolean = false): SearchDiscoverySession =
        sessions.getOrPut(selection.name) { SearchDiscoverySession().also { it.select(selection) } }
            .also { if (freshVisit) it.view = null }

    fun clear() = sessions.clear()
}

/**
 * A streaming service's page, opened from its folder in the streaming collection. It draws the
 * same rows as Search's provider tag ([streamingProviderPage]) under the folder's logo.
 */
@Composable
internal fun StreamingProviderScreen(
    collectionId: String,
    folderId: String,
    title: String,
    /** False while another page (a title's details) is open on top of this one. */
    isTopRoute: Boolean = true,
    onBack: () -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection = remember(collectionId, folderId) { StreamingCollection.provider(collectionId, folderId) } ?: return
    // Saved with this back-stack entry: false only on a fresh visit, true when returning from a title.
    var entered by rememberSaveable(selection.name) { mutableStateOf(false) }
    val session = remember(selection) {
        StreamingProviderSessions.sessionFor(selection, freshVisit = !entered).also { entered = true }
    }
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()
    val folder = collections.firstOrNull { it.id == collectionId }?.folders?.firstOrNull { it.id == folderId }
    val active = LocalScreenActive.current

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
    }
    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val homeSettings by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val hideUnplayable by remember {
        SearchRepository.ensurePreferencesLoaded()
        SearchRepository.hideUnplayable
    }.collectAsStateWithLifecycle()
    val rowLayout by remember {
        StreamingRowLayoutRepository.ensureLoaded()
        StreamingRowLayoutRepository.layout
    }.collectAsStateWithLifecycle()

    var scope by rememberSaveable(selection.name) { mutableStateOf(SearchScope.All) }
    val listState = rememberLazyListState()
    val shelfView = session.view
    // The grid's bar keeps its row while it fades out after Back.
    var lastShelfView by remember { mutableStateOf<SearchShelfView?>(null) }
    if (shelfView != null && lastShelfView != shelfView) lastShelfView = shelfView
    val closeGrid = {
        session.view = null
        listState.requestScrollToItem(0)
    }
    val goBack = { if (session.view != null) closeGrid() else onBack() }
    PlatformBackHandler(enabled = shelfView != null, onBack = goBack)
    // Esc is caught at the window, before back handlers: from the grid it returns to the rows, not Home.
    OverlayEscapeHandler(enabled = shelfView != null && isTopRoute) { closeGrid() }

    CompositionLocalProvider(LocalSearchDiscoverySession provides session) {
        val shelves = rememberSearchDiscoveryShelves(
            selection = selection,
            active = active,
            hideUnplayable = hideUnplayable,
            hideUnreleased = homeSettings.hideUnreleasedContent,
        )
        val topTen = rememberSearchTopTen(selection = selection, addons = addonsUiState.addons, active = active)
        val collectionRows = rememberStreamingCollectionRows(
            selection = selection,
            region = shelves.region,
            addons = addonsUiState.addons,
            active = active,
        )
        val discoveryUi = rememberSearchDiscovery(
            selection = selection.takeIf { shelfView != null },
            active = active,
            hideUnplayable = hideUnplayable,
            hideUnreleased = homeSettings.hideUnreleasedContent,
            view = shelfView,
            regionOverride = shelves.region,
        )
        // A row's grid: the bar's menus filter and sort the titles loaded so far.
        var gridFilters by remember(shelfView) {
            mutableStateOf(shelfView?.let { GridSort.forShelf(it.sort) }?.let { StreamingGridFilters(sort = it, defaultSort = it) })
        }
        val progressEntries by remember {
            WatchProgressRepository.ensureLoaded()
            WatchProgressRepository.uiState
        }.collectAsStateWithLifecycle()
        val inProgressIds = remember(progressEntries.entries) {
            progressEntries.entries.filter { !it.isCompleted }.map { it.parentMetaId }.toSet()
        }
        val watchOf: (MetaPreview) -> GridWatch = { item ->
            when {
                WatchingState.isPosterWatched(watchedUiState.watchedKeys, item, fullyWatchedSeriesKeys) -> GridWatch.Watched
                item.id in inProgressIds || item.alternateIds.any(inProgressIds::contains) -> GridWatch.InProgress
                else -> GridWatch.Unwatched
            }
        }
        val gridUi = gridFilters?.let { filters -> discoveryUi.copy(items = filters.apply(discoveryUi.items, watchOf)) } ?: discoveryUi
        LoadMoreOnScrollEffect(
            state = listState,
            canLoadMore = discoveryUi.hasMore && !discoveryUi.failed,
            isLoading = discoveryUi.loading,
            enabled = shelfView != null,
            onLoadMore = discoveryUi.load,
        )

        // Ambient backdrop: the folder's own artwork until the hero's first title is known.
        val backdropMode = LocalShellBackdropMode.current
        val backdrop = remember(backdropMode == ShellBackdropMode.Solid) {
            if (backdropMode != ShellBackdropMode.Solid) BackdropFocusController() else null
        }
        val heroArt = tagHeroItems(topTen, shelves, scope).firstOrNull()?.let { it.banner ?: it.poster }
        val backdropArt = heroArt ?: folder?.heroBackdropUrl
        LaunchedEffect(backdrop, backdropArt) { backdrop?.base = backdropArt }

        val headerScrolled by remember(listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        // The tabs row under the hero is pinned once it reaches the top of the list (Details' tabs row).
        // The tabs row under the hero pins like Details' tabs row, and stays pinned through a switch to a
        // short tab (see TagTabsPinState).
        val tabsPin = rememberTagTabsPinState(listState, selection.name)
        TagTabsSettleEffect(tabsPin, scope)
        val tabsPinned = tabsPin.pinned
        val hazeState = rememberHazeState()
        // The merged bar over this page draws the same surface as the header, so it needs to know
        // when rows are under it and which colour the backdrop has (Tinted).
        val topBarState = LocalDetailTopBarState.current
        // Observed, not read in composition: this scope doesn't recompose while the list scrolls.
        LaunchedEffect(topBarState, backdrop) {
            if (topBarState == null) return@LaunchedEffect
            snapshotFlow { (headerScrolled || tabsPin.holding) to backdrop?.tint }.collect { (scrolled, tint) ->
                topBarState.scrolled = scrolled
                topBarState.tint = tint
            }
        }

        Box(
            modifier.fillMaxSize()
                .then(if (backdrop == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                .testTag("streaming_provider_${selection.name}"),
        ) {
            if (backdrop != null) {
                Box(Modifier.fillMaxSize().navTransitionLayer(NavLayer.Background, NavSide.Above)) {
                    ShellBackdrop(backdrop, showArtwork = backdropMode == ShellBackdropMode.Ambient)
                }
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().navTransitionLayer(NavLayer.Content, NavSide.Above)
                    .padding(start = LocalBackdropContentStartInset.current),
            ) {
                // One grid with Home and the top bar: the same page inset, not Search's centred column.
                val gutter = desktopPageHorizontalPaddingForWidth((maxWidth + LocalBackdropContentStartInset.current).value)
                val contentWidth = maxWidth - gutter * 2
                val posterCardStyle = rememberPosterCardStyleUiState()
                val columns = remember(contentWidth, posterCardStyle.widthDp) {
                    searchGridColumns(contentWidth, posterCardStyle.widthDp, landscape = false)
                }
                // The merged bar is always shown over this page in the top-bar layout. The list starts
                // right at its bottom edge (as on Details), so the pinned tabs row meets it with no strip
                // between. The sidebar layout has nothing on top.
                val topBarLayout = topBarState != null
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val headerTop = if (topBarLayout) statusBar + DesktopMergedTopBarHeight else statusBar + 8.dp
                // Under the bar the header's surface starts below it (no doubled dim); without a bar
                // it runs from the window's top, so nothing scrolls past bare above it.
                val surfaceTop = if (topBarLayout) headerTop else 0.dp
                // Rows draw Home's editorial header (the service as the eyebrow), as on Home.
                val serviceName = title.ifBlank { selection.name }
                val logoUrl = folder?.titleLogoUrl
                val onScopeChange: (SearchScope) -> Unit = { next ->
                    // A tab picked from the pinned row keeps it pinned while the new rows settle.
                    if (next != scope) tabsPin.beforeTabChange()
                    scope = next
                }
                CompositionLocalProvider(
                    LocalBackdropFocusController provides backdrop,
                    LocalRedesignMenuSurface provides rememberRedesignMenuSurface(backdrop),
                    LocalNuvioShelfHeaderStyle provides NuvioShelfHeaderStyle.Editorial,
                ) {
                RedesignTypography(enabled = true) {
                NuvioScreen(
                    horizontalPadding = 0.dp,
                    topPadding = 0.dp,
                    listState = listState,
                    modifier = Modifier.fillMaxSize().padding(top = headerTop),
                    drawBackground = backdrop == null,
                ) {
                    // Rows are blur sources for the header and fade/slide whenever the layout changes.
                    val itemModifier: LazyItemScope.() -> Modifier = {
                        Modifier.animateItem(
                            fadeInSpec = tween(220),
                            placementSpec = tagRowPlacementSpec(),
                            fadeOutSpec = tween(160),
                        ).hazeSource(hazeState)
                    }
                    if (shelfView == null) {
                        // The service's logo alone, top-left above the hero, in the gap under the top bar.
                        item(key = "streaming-logo") {
                            Box(
                                itemModifier().fillMaxWidth().height(StreamingLogoBand).padding(horizontal = gutter),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                StreamingServiceLogo(logoUrl, serviceName, height = 28.dp, maxWidth = 220.dp, textSize = 26.sp)
                            }
                        }
                        streamingProviderPage(
                            selection = selection,
                            shelves = shelves,
                            topTen = topTen,
                            collectionRows = collectionRows,
                            layout = rowLayout,
                            heroMeta = session.heroMeta,
                            scope = scope,
                            gutter = gutter,
                            itemModifier = itemModifier,
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                            onViewAll = {
                                session.view = it
                                listState.requestScrollToItem(0)
                            },
                            onCollectionRowViewAll = onCatalogClick,
                            eyebrow = serviceName,
                            heroTopPadding = 0.dp,
                            onHeroBack = goBack,
                            listState = listState,
                            tabsSettling = { tabsPin.holding },
                            tabsBar = {
                                StreamingTabsBar(
                                    pinned = tabsPinned,
                                    scope = scope,
                                    onScopeChange = onScopeChange,
                                    logoUrl = logoUrl,
                                    serviceName = serviceName,
                                    fallbackCountry = shelves.fallbackCountry,
                                    onBack = goBack,
                                    horizontalPadding = gutter,
                                    hazeState = hazeState,
                                    tint = backdrop?.tint,
                                    plainBackground = MaterialTheme.colorScheme.background.takeIf { backdrop == null },
                                )
                            },
                        )
                    } else {
                        // Nothing sits under the bar: the grid starts right below it.
                        item(key = "streaming-header-space") { Spacer(Modifier.height(StreamingHeaderHeight + 16.dp)) }
                        searchDesktopDiscoveryResults(
                            selection = selection,
                            view = shelfView,
                            onBack = closeGrid,
                            state = gridUi,
                            columns = columns,
                            gutter = gutter,
                            itemModifier = itemModifier,
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                            showHeader = false,
                        )
                    }
                }
                // A row's grid keeps a bar from the start: back and the service's logo, on the same
                // surface as the top bar. The landing page has none; its tabs row pins instead.
                AnimatedVisibility(shelfView != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(140))) {
                    Box(
                        Modifier.padding(top = surfaceTop).fillMaxWidth()
                            .height(headerTop - surfaceTop + StreamingHeaderHeight)
                            .nuvioConsumePointerEvents(),
                    ) {
                        // Shown at once (there is no hero here), so it is frosted from the start.
                        PinnedBarBackground(
                            scrolled = true,
                            hazeState = hazeState,
                            tint = backdrop?.tint,
                            plainBackground = MaterialTheme.colorScheme.background.takeIf { backdrop == null },
                        )
                        val view = shelfView ?: lastShelfView
                        val filters = gridFilters
                        if (view != null) {
                            StreamingGridBar(
                                rowName = shelfTitle(SearchShelfKey(view.sort, view.television ?: true), selection),
                                shown = gridUi.items.size,
                                loaded = discoveryUi.items.size,
                                filters = filters,
                                loadedItems = discoveryUi.items,
                                onFiltersChange = { gridFilters = it },
                                onBack = goBack,
                                logo = { StreamingServiceLogo(logoUrl, serviceName, height = 20.dp, maxWidth = 130.dp, textSize = 18.sp) },
                                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(StreamingHeaderHeight)
                                    .padding(horizontal = gutter),
                            )
                        }
                    }
                }
                }
                }
            }
        }
    }
}

/** The service's logo image, or its name when it has none or the image fails. */
@Composable
private fun StreamingServiceLogo(logoUrl: String?, name: String, height: Dp, maxWidth: Dp, textSize: TextUnit) {
    // The logo's transparent border is cut away, so [height] is the artwork's own height: service
    // logos sit in very different canvases (Crunchyroll's showed at a third of its box).
    val state = rememberTrimmedLogo(logoUrl)
    androidx.compose.animation.Crossfade(state, animationSpec = tween(200), label = "service_logo") { current ->
        when (current) {
            is TrimmedLogoState.Ready -> {
                val width = (height * current.logo.aspectRatio).coerceAtMost(maxWidth)
                androidx.compose.foundation.Image(
                    painter = current.logo.painter,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(height).width(width).testTag("streaming_service_logo"),
                )
            }
            // Holds the logo's height while it loads, so nothing under it moves.
            TrimmedLogoState.Loading -> Spacer(Modifier.height(height))
            TrimmedLogoState.Failed -> Text(
                name,
                style = redesignText(textSize, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("streaming_service_logo"),
            )
        }
    }
}

/**
 * All / Movies / Shows under the hero, as Details' tabs row: a hairline under it while it sits in the
 * page; once the hero has scrolled away it is [pinned] under the top bar on the shared pinned-bar
 * surface, and Back and the service's logo slide in from the left.
 */
@Composable
private fun StreamingTabsBar(
    pinned: Boolean,
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    logoUrl: String?,
    serviceName: String,
    fallbackCountry: String?,
    onBack: () -> Unit,
    horizontalPadding: Dp,
    hazeState: HazeState,
    tint: Color?,
    plainBackground: Color?,
) {
    val pinnedNow by rememberUpdatedState(pinned)
    Box(
        Modifier.fillMaxWidth().height(DetailBoardTabsHeight).keepListStillWhilePinned { pinnedNow }
            .testTag("streaming_tabs_bar"),
    ) {
        PinnedBarBackground(scrolled = pinned, hazeState = hazeState, tint = tint, plainBackground = plainBackground?.takeIf { pinned })
        val hairline by animateColorAsState(if (pinned) Color.Transparent else RedesignColors.hairline, tween(220), label = "streaming_tabs_hairline")
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = horizontalPadding).fillMaxWidth().height(1.dp).background(hairline))
        Row(Modifier.fillMaxSize().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = pinned,
                enter = fadeIn(tween(220)) + expandHorizontally(tween(StreamingBarMotionMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(StreamingBarMotionMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedesignIconButton(
                        contentDescription = stringResource(Res.string.action_back),
                        onClick = onBack,
                        vector = Icons.AutoMirrored.Rounded.ArrowBack,
                        size = 40.dp,
                        modifier = Modifier.testTag("streaming_pinned_back"),
                    )
                    Spacer(Modifier.width(16.dp))
                    StreamingServiceLogo(logoUrl, serviceName, height = 20.dp, maxWidth = 130.dp, textSize = 18.sp)
                    Box(Modifier.padding(horizontal = 18.dp).width(1.dp).height(24.dp).background(RedesignColors.hairline))
                }
            }
            TagScopeTabs(scope, onScopeChange, Modifier.align(Alignment.Bottom))
            Spacer(Modifier.weight(1f))
            TagFallbackNote(fallbackCountry)
            TagRowsCustomizeButton()
        }
    }
}

private val StreamingHeaderHeight = 64.dp

/** The gap between the top bar and the hero that holds the service's logo. */
private val StreamingLogoBand = 64.dp

private const val StreamingBarMotionMillis = 360
