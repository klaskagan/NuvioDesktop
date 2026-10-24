package com.nuvio.app.features.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.catalog.SeasonalCatalogBackControl
import com.nuvio.app.features.home.*
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.flow.distinctUntilChanged
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The configured hero is part of the same scrollable content as the existing sources and movies. */
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
    val selected = uiState.selectedTab?.let { it.copy(items = it.items.map { movie -> movie.copy(posterShape = PosterShape.Landscape) }) }
    val gridState = key(uiState.collectionId, folder.id, if (rows) "rows" else selected?.sourceKey ?: "all") {
        rememberLazyGridState()
    }
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, rows, uiState.selectedTabCanLoadMore, uiState.selectedTabIsLoadingMore, uiState.isLoading) {
        if (rows || uiState.isLoading || !uiState.selectedTabCanLoadMore || uiState.selectedTabIsLoadingMore) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            info.totalItemsCount > 2 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 6
        }.distinctUntilChanged().collect { if (it) currentLoadMore() }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds()) {
        val padding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        val minHeroHeight = (maxHeight * 0.60f).coerceIn(280.dp, 560.dp)
        var heroHeight by remember(folder.id) { mutableIntStateOf(0) }
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
        val artworkRows = remember(rows, sections, keyedMovies, columns, maxWidth, style.widthDp) {
            if (rows) sections.withDuplicateSafeLazyKeys { it.key }.map { keyed ->
                val section = keyed.value.let { it.copy(items = it.items.map { movie -> movie.copy(posterShape = PosterShape.Landscape) }) }
                com.nuvio.app.features.home.components.ArtworkPrefetchRow(keyed.lazyKey,
                    if (com.nuvio.app.features.home.components.preservesOriginalLandscapeArtwork(section.title, section.target)) emptyList()
                    else section.items.take(com.nuvio.app.features.home.components.artworkVisibleCardCount(maxWidth.value, padding.value, style.widthDp) + 1))
            } else com.nuvio.app.features.home.components.artworkGridRows(keyedMovies.map { it.value }, keyedMovies.map { it.lazyKey }, columns)
        }
        val identity = listOf(uiState.collectionId, folder.id, rows, selected?.sourceKey, columns)
        val prepared = com.nuvio.app.features.home.components.rememberGridArtworkPrefetch(gridState, artworkRows, identity, allowPortraitFallback = false, forceShape = PosterShape.Landscape)
        val artworkGates = com.nuvio.app.features.home.components.rememberGridArtworkGates(if (rows) emptyList() else artworkRows, identity)
        CompositionLocalProvider(com.nuvio.app.features.home.components.LocalPreparedLandscapeArtwork provides prepared) {
        CollectionBackdrop(folder, minHeroHeight, heroHeight) { height ->
            if (gridState.firstVisibleItemIndex == 0) -gridState.firstVisibleItemScrollOffset.toFloat() else -height
        }
        LazyVerticalGrid(
            columns = cells, state = gridState,
            modifier = Modifier.fillMaxSize().testTag("collection_grid"),
            contentPadding = PaddingValues(start = if (rows) 0.dp else padding, end = if (rows) 0.dp else padding,
                bottom = nuvioSafeBottomPadding(28.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                CollectionIntroduction(folder, minHeroHeight,
                    Modifier.onSizeChanged { heroHeight = it.height }.padding(horizontal = if (rows) padding else 0.dp))
            }
            if (!rows && uiState.tabs.size > 1) {
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
                items(sections.withDuplicateSafeLazyKeys { it.key }, key = { it.lazyKey }) { keyed ->
                    val section = keyed.value.let { it.copy(items = it.items.map { movie -> movie.copy(posterShape = PosterShape.Landscape) }) }
                    HomeCatalogRowSection(section = section, entries = section.items, sectionPadding = padding, allowPortraitFallback = false,
                        onViewAllClick = if (section.canOpenCatalog(18)) { { onCatalogClick(section) } } else null,
                        watchedKeys = watchedKeys, onPosterClick = onPosterClick)
                }
            } else {
                items(keyedMovies, key = { it.lazyKey }) { keyed ->
                    val movie = keyed.value
                    CompositionLocalProvider(com.nuvio.app.features.home.components.LocalLandscapeRowReveal provides artworkGates[keyed.lazyKey]) {
                        HomePosterCard(item = movie, allowPortraitFallback = false, useLandscapeBackdropMode = true,
                            isWatched = WatchingState.isPosterWatched(watchedKeys, movie), onClick = { onPosterClick(movie) })
                    }
                }
            }
            val empty = if (rows) sections.isEmpty() else selected?.items.isNullOrEmpty()
            if (empty || (!rows && uiState.selectedTabIsLoadingMore)) {
                item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        if (uiState.isLoading || (!rows && uiState.selectedTabIsLoadingMore)) NuvioLoadingIndicator(Modifier.size(32.dp))
                        else Text(selected?.error ?: uiState.tabs.firstOrNull { it.error != null }?.error
                            ?: stringResource(Res.string.collections_folder_empty_items),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        NuvioDesktopVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
        SeasonalCatalogBackControl(padding, onBack)
        }
    }
}
