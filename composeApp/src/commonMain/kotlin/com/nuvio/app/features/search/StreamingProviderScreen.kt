package com.nuvio.app.features.search

import androidx.compose.animation.AnimatedVisibility
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
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.NavLayer
import com.nuvio.app.core.ui.NavSide
import com.nuvio.app.core.ui.NuvioAsyncImage
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
        val hazeState = rememberHazeState()
        // The merged bar over this page draws the same surface as the header, so it needs to know
        // when rows are under it and which colour the backdrop has (Tinted).
        val topBarState = LocalDetailTopBarState.current
        // Observed, not read in composition: this scope doesn't recompose while the list scrolls.
        LaunchedEffect(topBarState, backdrop) {
            if (topBarState == null) return@LaunchedEffect
            snapshotFlow { headerScrolled to backdrop?.tint }.collect { (scrolled, tint) ->
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
                // The merged bar is always shown over this page in the top-bar layout; the header sits
                // right under it and the two draw one surface. The sidebar layout has nothing on top.
                val topBarLayout = topBarState != null
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val headerTop = if (topBarLayout) statusBar + DesktopMergedTopBarHeight else statusBar + 8.dp
                // Under the bar the header's surface starts below it (no doubled dim); without a bar
                // it runs from the window's top, so nothing scrolls past bare above it.
                val surfaceTop = if (topBarLayout) headerTop else 0.dp
                CompositionLocalProvider(LocalBackdropFocusController provides backdrop) {
                RedesignTypography(enabled = true) {
                NuvioScreen(
                    horizontalPadding = 0.dp,
                    topPadding = 0.dp,
                    listState = listState,
                    modifier = Modifier.fillMaxSize(),
                    drawBackground = backdrop == null,
                ) {
                    // Rows are blur sources for the header and fade/slide whenever the layout changes.
                    val itemModifier: LazyItemScope.() -> Modifier = {
                        Modifier.animateItem(
                            fadeInSpec = tween(220),
                            placementSpec = tween(260, easing = FastOutSlowInEasing),
                            fadeOutSpec = tween(160),
                        ).hazeSource(hazeState)
                    }
                    item(key = "streaming-header-space") { Spacer(Modifier.height(headerTop + StreamingHeaderHeight - 14.dp)) }
                    if (shelfView == null) {
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
                        )
                    } else {
                        searchDesktopDiscoveryResults(
                            selection = selection,
                            view = shelfView,
                            onBack = closeGrid,
                            state = discoveryUi,
                            columns = columns,
                            gutter = gutter,
                            itemModifier = itemModifier,
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                        )
                    }
                }
                // The header: back, the service, its filter and Customize rows, in one row, on the same
                // surface as the bar (Settings → Layout → Top bar background).
                Box(
                    Modifier.padding(top = surfaceTop).fillMaxWidth()
                        .height(headerTop - surfaceTop + StreamingHeaderHeight)
                        .nuvioConsumePointerEvents(),
                ) {
                    PinnedBarBackground(
                        scrolled = headerScrolled,
                        hazeState = hazeState,
                        tint = backdrop?.tint,
                        plainBackground = MaterialTheme.colorScheme.background.takeIf { backdrop == null },
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(StreamingHeaderHeight)
                            .padding(horizontal = gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The 48dp button centres a 24dp glyph; pull it left so the arrow sits on the page edge.
                        NuvioBackButton(
                            onClick = goBack,
                            modifier = Modifier.offset(x = (-12).dp),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            buttonSize = 48.dp,
                            iconSize = 24.dp,
                        )
                        var logoFailed by remember(folder?.titleLogoUrl) { mutableStateOf(false) }
                        val logo = folder?.titleLogoUrl?.takeIf { it.isNotBlank() && !logoFailed }
                        if (logo != null) {
                            NuvioAsyncImage(
                                logo, title,
                                Modifier.height(36.dp).widthIn(max = 180.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                onError = { logoFailed = true },
                            )
                        } else {
                            Text(
                                title.ifBlank { selection.name },
                                style = redesignText(22.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // The grid has no tabs; its controls fade out rather than vanish.
                        AnimatedVisibility(shelfView == null, enter = fadeIn(tween(200)), exit = fadeOut(tween(140))) {
                            TagScopePills(scope, { scope = it }, Modifier.padding(start = 20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(shelfView == null, enter = fadeIn(tween(200)), exit = fadeOut(tween(140))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TagFallbackNote(shelves.fallbackCountry)
                                TagRowsCustomizeButton()
                            }
                        }
                    }
                }
                }
                }
            }
        }
    }
}

private val StreamingHeaderHeight = 60.dp
