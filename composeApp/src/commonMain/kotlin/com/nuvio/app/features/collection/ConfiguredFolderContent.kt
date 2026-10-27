package com.nuvio.app.features.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import com.nuvio.app.navigation.LocalUseNativeNavigation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.*
import com.nuvio.app.isDesktop
import com.nuvio.app.features.catalog.SeasonalCatalogBackControl
import com.nuvio.app.features.home.*
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.flow.distinctUntilChanged
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The configured hero is part of the same scrollable content as the existing sources and movies. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ConfiguredFolderContent(
    uiState: FolderDetailUiState,
    sections: List<HomeCatalogSection>,
    watchedKeys: Set<String>,
    onBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
) {
    val folder = uiState.artworkFolder ?: return
    val rows = uiState.viewMode != FolderViewMode.TABBED_GRID
    val seasonalGrid = uiState.seasonalGrid
    val displayedTab = if (!rows && seasonalGrid != null) uiState.selectedTab?.copy(
        items = seasonalGrid.items, isLoading = seasonalGrid.isLoading, error = seasonalGrid.error,
        isLoadingMore = seasonalGrid.isLoading && seasonalGrid.items.isNotEmpty()) else uiState.selectedTab
    val loading = if (!rows && seasonalGrid != null) seasonalGrid.isLoading else uiState.isLoading
    val selected = displayedTab?.let { it.copy(items = it.items.map { movie -> movie.copy(posterShape = PosterShape.Landscape) }) }
    val rowState = key(uiState.collectionId, folder.id, "rows") { rememberLazyGridState() }
    val mediaState = key(uiState.collectionId, folder.id, if (seasonalGrid == null) selected?.sourceKey else "seasonal") { rememberLazyGridState() }
    val gridState = if (rows) rowState else mediaState
    val queryIdentity = selected?.sourceKey to seasonalGrid?.query
    var previousQuery by remember(uiState.collectionId, folder.id) { mutableStateOf(queryIdentity) }
    // Capture before the replacement dataset is measured: a shorter intermediate
    // layout may already have clamped the live state by the time the effect runs.
    val queryAnchor = remember(queryIdentity) {
        mediaState.introductionVisible() to mediaState.firstVisibleItemScrollOffset
    }
    LaunchedEffect(queryIdentity) {
        // Keep a visible introduction in place. Only reset the results when already past it.
        if (seasonalGrid != null && previousQuery != queryIdentity && !rows) {
            if (queryAnchor.first) mediaState.requestScrollToItem(0, queryAnchor.second)
            else mediaState.requestScrollToItem(1)
        }
        previousQuery = queryIdentity
    }
    // Prepare the target state before publishing the mode so its first measured frame is already anchored.
    val toggleMode = {
        val from = if (rows) rowState else mediaState
        val to = if (rows) mediaState else rowState
        if (from.introductionVisible()) {
            to.requestScrollToItem(0, from.firstVisibleItemScrollOffset)
        } else if (to.introductionVisible()) {
            to.requestScrollToItem(1)
        }
        FolderDetailRepository.setSeasonalViewMode(if (rows) FolderViewMode.TABBED_GRID else FolderViewMode.ROWS)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds()) {
        val padding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        val chromeHeight = if (LocalUseNativeNavigation.current) 0.dp else
            with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + if (isDesktop) 92.dp else 104.dp
        val minHeroHeight = (maxHeight * 0.60f).coerceIn(280.dp, 560.dp)
        var heroHeight by remember(folder.id) { mutableIntStateOf(0) }
        var controlsHeight by remember(folder.id) { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val normalBringIntoView = LocalBringIntoViewSpec.current
        val pinnedHeight by rememberUpdatedState(controlsHeight)
        val minimumTouchHeight = LocalViewConfiguration.current.minimumTouchTargetSize.height
        val labelLineHeight = MaterialTheme.typography.labelLarge.lineHeight
        val chipPadding = MaterialTheme.nuvio.components.chipVerticalPadding
        val maximumControlHeight = with(density) {
            maxOf(minimumTouchHeight.toPx(), labelLineHeight.toPx() + 2 * chipPadding.toPx())
        }
        val pinnedBringIntoView = remember(normalBringIntoView, maximumControlHeight) {
            PinnedFilterBringIntoViewSpec(normalBringIntoView, { pinnedHeight }, maximumControlHeight)
        }
        val style = rememberPosterCardStyleUiState()
        val baseWidth = catalogPosterBaseWidthDp(style.widthDp)
        val cells = if (rows) GridCells.Fixed(1) else GridCells.FixedSize(
            landscapePosterWidth(baseWidth),
        )
        val keyedMovies = remember(selected?.items) { selected?.items.orEmpty().withDuplicateSafeLazyKeys { it.stableKey() } }
        val columns = if (rows) 1 else {
            com.nuvio.app.features.home.components.artworkFixedGridColumns(constraints.maxWidth, padding,
                landscapePosterWidth(baseWidth))
        }
        var mediaRowHeight by remember(folder.id, baseWidth, density, queryIdentity) { mutableIntStateOf(0) }
        val mediaRowCount = (keyedMovies.size + columns - 1) / columns
        // Use current-result measurements; a taller card from an old query must not
        // underestimate the space needed below a shorter replacement result.
        // Short successful results need the same minimum viewport as loading/empty/error.
        // Otherwise LazyGrid clamps its scroll offset and brings the introduction back.
        val keyedSections = sections.withDuplicateSafeLazyKeys { it.key }
        val sectionHeights = remember(folder.id, maxWidth, baseWidth, density) { mutableStateMapOf<Any, Int>() }
        val resultContentHeight = if (rows) {
            with(density) { keyedSections.sumOf { sectionHeights[it.lazyKey] ?: 0 }.toDp() } + 18.dp * keyedSections.size
        } else {
            with(density) { controlsHeight.toDp() } + 18.dp +
                (with(density) { mediaRowHeight.toDp() } + 18.dp) * mediaRowCount
        }
        val minimumFooterHeight = (maxHeight - chromeHeight - resultContentHeight).coerceAtLeast(0.dp)
        val artworkRows = remember(rows, sections, keyedMovies, columns, maxWidth, style.widthDp) {
            if (rows) sections.withDuplicateSafeLazyKeys { it.key }.map { keyed ->
                val section = keyed.value.let { it.copy(items = it.items.map { movie -> movie.copy(posterShape = PosterShape.Landscape) }) }
                com.nuvio.app.features.home.components.ArtworkPrefetchRow(keyed.lazyKey,
                    if (com.nuvio.app.features.home.components.preservesOriginalLandscapeArtwork(section.title, section.target)) emptyList()
                    else section.items.take(com.nuvio.app.features.home.components.artworkVisibleCardCount(maxWidth.value, padding.value, style.widthDp) + 1))
            } else com.nuvio.app.features.home.components.artworkGridRows(keyedMovies.map { it.value }, keyedMovies.map { it.lazyKey }, columns)
        }
        val identity = listOf(uiState.collectionId, folder.id, rows, selected?.sourceKey, seasonalGrid?.query, columns)
        val pageWindow = com.nuvio.app.features.home.components.rememberMediaPrefetchWindow(identity)
        val prepared = com.nuvio.app.features.home.components.rememberGridArtworkPrefetch(gridState, artworkRows, identity, allowPortraitFallback = false, forceShape = PosterShape.Landscape, window = pageWindow)
        com.nuvio.app.features.home.components.MediaPagePrefetchEffect(
            rows = artworkRows, window = pageWindow, viewport = gridState, identity = identity,
            canLoadMore = uiState.selectedTabCanLoadMore,
            isLoading = loading || uiState.selectedTabIsLoadingMore,
            enabled = !rows, onLoadMore = onLoadMore,
            visibleKeys = { gridState.layoutInfo.visibleItemsInfo.map { it.key } },
        )
        val artworkGates = com.nuvio.app.features.home.components.rememberGridArtworkGates(if (rows) emptyList() else artworkRows, identity, prepared)
        CompositionLocalProvider(com.nuvio.app.features.home.components.LocalPreparedLandscapeArtwork provides prepared) {
        CollectionBackdrop(folder, minHeroHeight, heroHeight + with(LocalDensity.current) { chromeHeight.roundToPx() }) { height ->
            if (gridState.firstVisibleItemIndex == 0) -gridState.firstVisibleItemScrollOffset.toFloat() else -height
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides
            if (!rows && seasonalGrid != null) pinnedBringIntoView else normalBringIntoView) {
        LazyVerticalGrid(
            columns = cells, state = gridState,
            modifier = Modifier.fillMaxSize().padding(top = chromeHeight).clipToBounds().testTag("collection_grid"),
            contentPadding = PaddingValues(start = if (rows) 0.dp else padding, end = if (rows) 0.dp else padding,
                bottom = nuvioSafeBottomPadding(28.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                CollectionIntroduction(folder, (minHeroHeight - chromeHeight).coerceAtLeast(0.dp),
                    Modifier.onSizeChanged { heroHeight = it.height }.padding(horizontal = if (rows) padding else 0.dp),
                    subtitle = SeasonalCollection.theme(folder)?.let { stringResource(it.description) }.orEmpty())
            }
            if (!rows && seasonalGrid != null) {
                stickyHeader(key = "seasonal_filters") {
                    Box(Modifier.fillMaxWidth().testTag("seasonal_filters").onSizeChanged { controlsHeight = it.height }.background(MaterialTheme.colorScheme.background).padding(vertical = 12.dp)) {
                        // Popup menus keep their own ordinary keyboard/scroll behavior.
                        CompositionLocalProvider(LocalBringIntoViewSpec provides normalBringIntoView) {
                            SeasonalGridControls(uiState)
                        }
                    }
                }
            }
            if (!rows && seasonalGrid == null && uiState.tabs.size > 1) {
                item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        ScrollableTabRow(uiState.selectedTabIndex, edgePadding = 0.dp,
                            containerColor = MaterialTheme.colorScheme.background, divider = {}) {
                            uiState.tabs.forEachIndexed { index, tab ->
                                Tab(selected = index == uiState.selectedTabIndex, onClick = { onTabSelected(index) }, text = {
                                    Text(if (tab.isAllTab) stringResource(Res.string.collections_tab_all) else tab.label,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                })
                            }
                        }
                    }
                }
            }
            if (rows) {
                items(keyedSections, key = { it.lazyKey }) { keyed ->
                    val section = keyed.value.let { it.copy(items = it.items.map { movie -> movie.copy(posterShape = PosterShape.Landscape) }) }
                    HomeCatalogRowSection(section = section, modifier = Modifier.onSizeChanged { sectionHeights[keyed.lazyKey] = it.height },
                        entries = section.items, sectionPadding = padding, allowPortraitFallback = false,
                        onViewAllClick = if (section.canOpenCatalog(18)) { { onCatalogClick(section) } } else null,
                        watchedKeys = watchedKeys, onPosterClick = onPosterClick)
                }
            } else {
                items(keyedMovies, key = { it.lazyKey }) { keyed ->
                    val movie = keyed.value
                    CompositionLocalProvider(com.nuvio.app.features.home.components.LocalLandscapeRowReveal provides artworkGates[keyed.lazyKey]) {
                        HomePosterCard(item = movie, modifier = Modifier.onSizeChanged {
                            if (mediaRowHeight == 0 || it.height < mediaRowHeight) mediaRowHeight = it.height
                        }, allowPortraitFallback = false, useLandscapeBackdropMode = true,
                            isWatched = WatchingState.isPosterWatched(watchedKeys, movie), onClick = { onPosterClick(movie) })
                    }
                }
            }
            val empty = if (rows) sections.isEmpty() else selected?.items.isNullOrEmpty()
            val error = if (!rows && seasonalGrid != null) seasonalGrid.error else uiState.tabs.firstOrNull { it.error != null }?.error
            val showStatus = empty || (!rows && uiState.selectedTabIsLoadingMore) || error != null || (!rows && seasonalGrid?.hasMore == false)
            if (showStatus || seasonalGrid != null) {
                item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().testTag("collection_status").then(
                        if (seasonalGrid != null) Modifier.heightIn(min = minimumFooterHeight) else Modifier
                    ).padding(if (showStatus) 32.dp else 0.dp), contentAlignment = Alignment.Center) {
                        if (showStatus) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (loading || (!rows && uiState.selectedTabIsLoadingMore)) NuvioLoadingIndicator(Modifier.size(32.dp))
                            else Text(error ?: if (!empty && seasonalGrid?.hasMore == false) stringResource(Res.string.seasonal_end_results)
                                else stringResource(Res.string.collections_folder_empty_items), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (error != null && seasonalGrid != null) TextButton(onClick = {
                                if (rows) FolderDetailRepository.retrySeasonalRows() else FolderDetailRepository.retrySeasonalGrid()
                            }) { Text(stringResource(Res.string.action_retry)) }
                        }
                    }
                }
            }
        }
        }
        NuvioDesktopVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
        SeasonalCatalogBackControl(padding, onBack)
        if (seasonalGrid != null) {
            IconButton(onClick = toggleMode,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 32.dp, end = padding)
                    .size(48.dp).background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)) {
                Icon(if (rows) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda,
                    contentDescription = stringResource(if (rows) Res.string.seasonal_show_grid else Res.string.seasonal_show_rows),
                    tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        }
    }
}

/** A lazy layout may retain index zero for its trailing gap after the hero itself is offscreen. */
private fun LazyGridState.introductionVisible(): Boolean {
    if (firstVisibleItemIndex != 0) return false
    val introduction = layoutInfo.visibleItemsInfo.firstOrNull { it.key == "intro" } ?: return true
    return introduction.offset.y + introduction.size.height > layoutInfo.viewportStartOffset
}

/** The foundation sticky-header wrapper offsets requests even for controls inside that header. */
internal class PinnedFilterBringIntoViewSpec(
    private val normal: BringIntoViewSpec,
    private val headerHeight: () -> Int,
    private val maximumControlHeight: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val header = headerHeight()
        if (header > 0 && size <= minOf(header.toFloat(), maximumControlHeight) && offset >= -header && offset < header) return 0f
        return normal.calculateScrollDistance(offset, size, containerSize)
    }
}
