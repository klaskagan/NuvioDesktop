package com.nuvio.app.features.catalog

import androidx.compose.animation.core.FastOutSlowInEasing
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.collection.rememberPageBackdrop
import com.nuvio.app.features.collection.PageTopBarSurfaceEffect
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.ProvideRedesignMenuSurface
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.collection.FolderGridGap
import com.nuvio.app.features.collection.FolderHero
import com.nuvio.app.features.collection.FolderLandscapeMinCell
import com.nuvio.app.features.collection.FolderPageTop
import com.nuvio.app.features.collection.FolderPosterMinCell
import com.nuvio.app.features.collection.PageGridFilterMenus
import com.nuvio.app.features.collection.PageGridFilters
import com.nuvio.app.features.collection.PageStateCard
import com.nuvio.app.features.collection.applyPageGridFilters
import com.nuvio.app.features.collection.folderHeroHeight
import com.nuvio.app.features.collection.folderHeroItems
import com.nuvio.app.features.collection.pageGridFacets
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.HomePageFilterRow
import com.nuvio.app.features.home.components.HomePageFilterRowHeight
import com.nuvio.app.features.home.components.HomePageNameBand
import com.nuvio.app.features.home.components.bleedHorizontally
import com.nuvio.app.features.home.components.rememberFilterRowPinned
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.home.components.HomePagePinnedBarHeight
import com.nuvio.app.features.home.components.HomePageTopBarHeight
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.components.LoadMoreOnScrollEffect
import com.nuvio.app.features.home.components.preservesOriginalLandscapeArtwork
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop See all (Home design, "See all · catalog grid / scrolled"): a row's › opens the whole
 * catalog on Home's hero tile cycling its first titles, with only Back on it, then a grid that fills
 * the width (7 posters or 5 × 16:9 at a 1360 dp window). The catalog's name, count, sort and filters
 * live only in the shared pinned bar that slides in once the hero scrolls away.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun HomeSeeAllPage(
    title: String,
    target: CatalogTarget,
    uiState: CatalogUiState,
    gridState: LazyGridState,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    networkCondition: NetworkCondition,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    subtitle: String = "",
) {
    // A seasonal catalog keeps its theme's artwork and intro on top instead of the name and hero.
    val seasonalTheme = remember(target) { target.seasonalThemeOrNull() }
    val posterStyle = rememberPosterCardStyleUiState()
    val items = uiState.items
    val landscape = posterStyle.catalogLandscapeModeEnabled ||
        (items.isNotEmpty() && items.all { it.posterShape == PosterShape.Landscape })
    var filters by remember(target) { mutableStateOf(PageGridFilters()) }
    val isWatched: (MetaPreview) -> Boolean = { WatchingState.isPosterWatched(watchedKeys, it, fullyWatchedSeriesKeys) }
    val facets = remember(items) { pageGridFacets(items) }
    val shown = remember(items, filters, watchedKeys, fullyWatchedSeriesKeys) { applyPageGridFilters(items, filters, isWatched) }
    val heroSource = remember(items.take(24)) { folderHeroItems(items.take(24)) }
    val loading = uiState.isLoading && items.isEmpty()
    val failed = !loading && items.isEmpty()
    // The hero follows Home's Hero setting: with it off, the page opens on the grid with the bar shown.
    val heroEnabled by com.nuvio.app.features.home.HomeCatalogSettingsRepository.uiState.collectAsState()
    val hasHero = heroSource.isNotEmpty() && heroEnabled.heroEnabled
    val enhanceArtwork = !preservesOriginalLandscapeArtwork(title, target)
    // A seasonal catalog draws its theme's artwork instead.
    val backdropMode by ThemeSettingsRepository.shellBackdrop.collectAsState()
    val backdrop = rememberPageBackdrop(
        if (seasonalTheme != null) ShellBackdropMode.Solid else backdropMode,
        heroSource.firstOrNull()?.let { it.banner ?: it.poster },
    )

    LoadMoreOnScrollEffect(
        state = gridState,
        canLoadMore = uiState.canLoadMore,
        isLoading = uiState.isLoading,
        enabled = true,
        threshold = 28,
        onLoadMore = onLoadMore,
    )
    val showHero = seasonalTheme == null && (hasHero || loading)
    val rowIndex = if (seasonalTheme != null) 1 else if (showHero) 2 else 1
    val rowPinned by rememberFilterRowPinned(gridState, SeeAllFilterRowKey)
    // The seasonal page keeps its own backdrop running under a clear bar.
    if (seasonalTheme == null) PageTopBarSurfaceEffect(gridState, backdrop)
    // A filter picked while the row is pinned keeps it pinned, restored once the new grid is composed.
    var keepPinned by remember { mutableStateOf(false) }
    LaunchedEffect(filters) {
        if (keepPinned) {
            gridState.scrollToItem(rowIndex)
            keepPinned = false
        }
    }
    val setFilters: (PageGridFilters) -> Unit = { next ->
        keepPinned = rowPinned
        filters = next
    }

    ProvideRedesignMenuSurface(backdrop) {
    RedesignTypography(enabled = true) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .then(if (backdrop == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
            .testTag("home_see_all_page"),
    ) {
        if (backdrop != null) ShellBackdrop(backdrop, showArtwork = backdropMode == ShellBackdropMode.Ambient)
        val padding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        val density = LocalDensity.current
        val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
        var heroExpanded by rememberSaveable { mutableStateOf(false) }
        OverlayEscapeHandler(enabled = heroExpanded) { heroExpanded = false }
        val heroTarget = folderHeroHeight(windowHeight, heroExpanded)
        val heroHeight by animateDpAsState(heroTarget, tween(360, easing = FastOutSlowInEasing), label = "see_all_hero_height")

        var introHeightPx by remember(target) { androidx.compose.runtime.mutableIntStateOf(0) }
        val introMinHeight = (maxHeight * 0.5f).coerceIn(300.dp, 460.dp)
        if (seasonalTheme != null) {
            SeasonalCatalogBackdrop(
                theme = seasonalTheme,
                minHeight = introMinHeight + HomePageTopBarHeight,
                measuredHeightPx = introHeightPx + with(density) { HomePageTopBarHeight.roundToPx() },
                gridState = gridState,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (landscape) FolderLandscapeMinCell else FolderPosterMinCell),
            state = gridState,
            // Clipped under the top bar: the page never runs under its transparent strip.
            modifier = Modifier.fillMaxSize().padding(top = HomePageTopBarHeight).clipToBounds().testTag("catalog_grid"),
            contentPadding = PaddingValues(start = padding, end = padding, bottom = nuvioSafeBottomPadding(28.dp)),
            horizontalArrangement = Arrangement.spacedBy(FolderGridGap),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (seasonalTheme != null) {
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                    SeasonalThemeIntro(
                        eyebrow = stringResource(Res.string.seasonal_spotlight_label),
                        title = title,
                        line = subtitle.ifBlank { stringResource(seasonalTheme.description) },
                        minHeight = introMinHeight,
                        onBack = onBack,
                        modifier = Modifier.onSizeChanged { introHeightPx = it.height }.testTag("seasonal_catalog_intro"),
                    )
                }
            } else {
                item(key = "name", span = { GridItemSpan(maxLineSpan) }) {
                    // The catalog's name above the hero, as the service page's logo.
                    HomePageNameBand(title, logoUrl = null)
                }
            }
            if (showHero) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    FolderHero(
                        items = heroSource,
                        height = heroHeight,
                        expanded = heroExpanded,
                        onToggleExpanded = { heroExpanded = !heroExpanded },
                        onBack = onBack,
                        onItemClick = { onPosterClick?.invoke(it) },
                    )
                }
            }
            stickyHeader(key = SeeAllFilterRowKey) {
                HomePageFilterRow(
                    pinned = rowPinned,
                    tint = backdrop?.tint,
                    name = title,
                    logoUrl = null,
                    onBack = onBack,
                    horizontalPadding = padding,
                    modifier = Modifier.bleedHorizontally(padding),
                    menus = if (failed || loading) null else { { PageGridFilterMenus(filters, facets, setFilters) } },
                    trailing = {
                        if (items.isNotEmpty()) {
                            Text(
                                if (filters.activeCount > 0) stringResource(Res.string.page_bar_count_of, shown.size.toString(), items.size.toString())
                                else stringResource(Res.string.page_bar_titles, items.size),
                                style = redesignText(13.sp, color = RedesignColors.textMuted),
                                maxLines = 1,
                            )
                        }
                        if (filters.isSet) {
                            RedesignPillButton(stringResource(Res.string.action_clear), onClick = { setFilters(PageGridFilters()) })
                        }
                    },
                )
            }
            when {
                loading -> items(if (landscape) 10 else 14, key = { "skeleton_$it" }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                            .clip(RoundedCornerShape(12.dp)).background(RedesignColors.fill),
                    )
                }
                failed || shown.isEmpty() -> item(key = "state", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        val offline = networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable
                        when {
                            failed && offline && !uiState.errorMessage.isNullOrBlank() ->
                                NuvioNetworkOfflineCard(condition = networkCondition, onRetry = onRetry)
                            failed && !uiState.errorMessage.isNullOrBlank() -> PageStateCard(
                                title = stringResource(Res.string.catalog_load_failed_title),
                                message = uiState.errorMessage.orEmpty(),
                                actionLabel = stringResource(Res.string.action_retry),
                                onAction = onRetry,
                            )
                            failed -> PageStateCard(
                                title = stringResource(Res.string.see_all_empty_title),
                                message = stringResource(Res.string.see_all_empty_message),
                                actionLabel = null,
                                onAction = null,
                            )
                            else -> PageStateCard(
                                title = stringResource(Res.string.folder_filtered_empty_title),
                                message = stringResource(Res.string.folder_filtered_empty_message),
                                actionLabel = stringResource(Res.string.page_bar_clear_filters),
                                onAction = { setFilters(PageGridFilters()) },
                            )
                        }
                    }
                }
                else -> items(shown.withDuplicateSafeLazyKeys { it.stableKey() }, key = { it.lazyKey }) { keyed ->
                    val item = keyed.value
                    // No fade-out: a filter re-pins the row (a scroll) while removed posters fade, and the grid then
                    // left those posters stuck on screen behind the empty-state card.
                    Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(260, easing = FastOutSlowInEasing), fadeOutSpec = null)) {
                        HomePosterCard(
                            item = item,
                            fillWidth = true,
                            enhanceLandscapeTitles = enhanceArtwork,
                            useLandscapeBackdropMode = landscape,
                            isWatched = isWatched(item),
                            onClick = onPosterClick?.let { { it(item) } },
                            onLongClick = onPosterLongClick?.let { { it(item) } },
                            allowInlineExpand = false,
                            quickLookRow = shown,
                        )
                    }
                }
            }
            // A short result keeps the row pinned: without room below it the grid clamps its scroll and
            // the row drops back under the hero.
            item(key = "filter_room", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height((maxHeight - HomePageTopBarHeight - HomePageFilterRowHeight - 18.dp).coerceAtLeast(1.dp)))
            }
        }
        NuvioDesktopVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
    }
    }
    }
}

private const val SeeAllFilterRowKey = "filter_row"
