package com.nuvio.app.features.collection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignMenuButton
import com.nuvio.app.core.ui.RedesignMenuOption
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeHeroMetaLoadEffect
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.canOpenCatalog
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeHeroSection
import com.nuvio.app.features.home.components.HomePageFilterRow
import com.nuvio.app.features.home.components.HomePageFilterRowHeight
import com.nuvio.app.features.home.components.HomePageNameBand
import com.nuvio.app.features.home.components.bleedHorizontally
import com.nuvio.app.features.home.components.rememberFilterRowPinned
import com.nuvio.app.features.home.components.HomePagePinnedBarHeight
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.components.HomePageTopBarHeight
import com.nuvio.app.features.home.components.LoadMoreOnScrollEffect
import com.nuvio.app.features.home.components.LocalHomeHeroHeightCap
import com.nuvio.app.features.home.rememberHomeHeroState
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The page's first content sits this far down: under the 78 dp top bar, plus the design's 18 dp. */
internal val FolderPageTop = 96.dp
internal val FolderGridGap = 14.dp
/** Narrowest cell before the grid drops a column: 5 × 16:9 or 7 posters at a 1360 dp window. */
internal val FolderLandscapeMinCell = 236.dp
internal val FolderPosterMinCell = 160.dp
private const val FolderHeroItemLimit = 8

/** Folders open on Home's hero tile at 430 dp of an 850 dp window. */
internal fun folderHeroHeight(windowHeight: Dp, expanded: Boolean): Dp =
    if (expanded) (windowHeight - 150.dp).coerceAtLeast(320.dp) else (windowHeight * (430f / 850f)).coerceIn(300.dp, 560.dp)

/** The folder's titles with a backdrop, for the hero carousel; posters only when none have one. */
internal fun folderHeroItems(items: List<MetaPreview>): List<MetaPreview> {
    val distinct = items.distinctBy { it.stableKey() }
    return distinct.filter { !it.banner.isNullOrBlank() }.ifEmpty { distinct }.take(FolderHeroItemLimit)
}

/**
 * Desktop folder page (Home design, "Folder · grid / rows / scrolled / user collection"): every
 * folder opens on Home's hero tile cycling its titles, with only Back on it, then the grid or rows.
 * The name, catalog picker and filters live only in the shared pinned bar that slides in once the
 * hero scrolls away. Seasonal Explore keeps its theme page ([ConfiguredFolderContent]).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun HomeFolderPage(
    uiState: FolderDetailUiState,
    sections: List<HomeCatalogSection>,
    watchedKeys: Set<String>,
    rowStyles: Map<String, HomeRowStyle>,
    onBack: () -> Unit,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onCatalogPosterLongClick: ((String, MetaPreview) -> Unit)?,
    onEditCollection: (() -> Unit)? = null,
) {
    val folder = uiState.folder
    val name = folder?.title ?: uiState.collectionTitle
    val landscape = uiState.artworkFolder != null || folder?.posterShape == PosterShape.Landscape
    var rows by rememberSaveable(uiState.collectionId, folder?.id) {
        mutableStateOf(uiState.viewMode != FolderViewMode.TABBED_GRID)
    }
    var filters by remember(uiState.collectionId, folder?.id) { mutableStateOf(PageGridFilters()) }
    val catalogTabs = uiState.tabs.filter { !it.isAllTab }
    val selectedTab = uiState.selectedTab
    val tabItems = selectedTab?.items.orEmpty().map { if (landscape) it.copy(posterShape = PosterShape.Landscape) else it }
    val allItems = remember(uiState.tabs) {
        (uiState.tabs.firstOrNull { it.isAllTab }?.items ?: catalogTabs.flatMap { it.items })
    }
    val facets = remember(tabItems) { pageGridFacets(tabItems) }
    val shown = remember(tabItems, filters, watchedKeys) {
        applyPageGridFilters(tabItems, filters) { WatchingState.isPosterWatched(watchedKeys, it) }
    }
    val heroSource = remember(allItems) { folderHeroItems(allItems) }
    val loading = uiState.isLoading || (selectedTab?.isLoading == true && tabItems.isEmpty())
    val error = selectedTab?.error?.takeIf { tabItems.isEmpty() }
    val empty = !loading && error == null && (if (rows) sections.isEmpty() else tabItems.isEmpty())
    // The hero follows Home's Hero setting: with it off, the page opens on the grid with the bar shown.
    val heroEnabled by com.nuvio.app.features.home.HomeCatalogSettingsRepository.uiState.collectAsState()
    val hasHero = heroSource.isNotEmpty() && heroEnabled.heroEnabled

    val gridState = rememberLazyGridState()
    LoadMoreOnScrollEffect(
        state = gridState,
        canLoadMore = uiState.selectedTabCanLoadMore,
        isLoading = loading || uiState.selectedTabIsLoadingMore,
        enabled = !rows,
        threshold = 20,
        onLoadMore = FolderDetailRepository::loadMoreSelectedTab,
    )
    val showHero = hasHero || loading
    // The filter row is the grid's sticky header: after the name band and the hero.
    val rowIndex = if (showHero) 2 else 1
    val rowPinned by rememberFilterRowPinned(gridState, FolderFilterRowKey)
    // Switching Grid / Rows, a catalog or a filter while the row is pinned keeps it pinned: restored once
    // the new layout is composed (a request made in the click handler was measured against the old one).
    var keepPinned by remember { mutableStateOf(false) }
    LaunchedEffect(rows, uiState.selectedTabIndex, filters) {
        if (keepPinned) {
            gridState.scrollToItem(rowIndex)
            keepPinned = false
        }
    }
    val changeKeepingPlace: (() -> Unit) -> Unit = { change ->
        keepPinned = rowPinned
        change()
    }

    RedesignTypography(enabled = true) {
    CompositionLocalProvider(LocalNuvioShelfHeaderStyle provides NuvioShelfHeaderStyle.Editorial) {
    BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).testTag("home_folder_page")) {
        val padding = desktopPageHorizontalPaddingForWidth(maxWidth.value)
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        var heroExpanded by rememberSaveable { mutableStateOf(false) }
        OverlayEscapeHandler(enabled = heroExpanded) { heroExpanded = false }
        val heroTarget = folderHeroHeight(windowHeight, heroExpanded)
        val heroHeight by animateDpAsState(heroTarget, tween(360, easing = FastOutSlowInEasing), label = "folder_hero_height")
        val minCell = if (landscape) FolderLandscapeMinCell else FolderPosterMinCell
        val inset = if (rows) padding else 0.dp

        LazyVerticalGrid(
            columns = if (rows) GridCells.Fixed(1) else GridCells.Adaptive(minCell),
            state = gridState,
            // Clipped under the top bar: the page never runs under its transparent strip.
            modifier = Modifier.fillMaxSize().padding(top = HomePageTopBarHeight).clipToBounds().testTag("collection_grid"),
            contentPadding = PaddingValues(
                start = if (rows) 0.dp else padding,
                end = if (rows) 0.dp else padding,
                bottom = nuvioSafeBottomPadding(28.dp),
            ),
            horizontalArrangement = Arrangement.spacedBy(FolderGridGap),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "name", span = { GridItemSpan(maxLineSpan) }) {
                // The folder's name above the hero, as the service page's logo; the grid's gap is folded in.
                HomePageNameBand(name, folder?.titleLogoUrl, Modifier.padding(horizontal = inset).padding(bottom = 0.dp))
            }
            if (showHero) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(horizontal = inset)) {
                        FolderHero(
                            items = heroSource,
                            height = heroHeight,
                            expanded = heroExpanded,
                            onToggleExpanded = { heroExpanded = !heroExpanded },
                            onBack = onBack,
                            onItemClick = onPosterClick,
                        )
                    }
                }
            }
            stickyHeader(key = FolderFilterRowKey) {
                HomePageFilterRow(
                    pinned = rowPinned,
                    name = name,
                    logoUrl = folder?.titleLogoUrl,
                    onBack = onBack,
                    horizontalPadding = padding,
                    modifier = if (rows) Modifier else Modifier.bleedHorizontally(padding),
                    menus = if (empty || error != null || loading) null else {
                        {
                            if (catalogTabs.size > 1) {
                                val allLabel = stringResource(Res.string.page_bar_all_catalogs)
                                RedesignMenuButton(
                                    label = selectedTab?.let { if (it.isAllTab) allLabel else it.label } ?: allLabel,
                                    options = uiState.tabs.mapIndexed { index, tab ->
                                        RedesignMenuOption(index, if (tab.isAllTab) allLabel else tab.label, tab.items.size.takeIf { it > 0 })
                                    },
                                    selected = uiState.selectedTabIndex,
                                    onSelect = { index -> changeKeepingPlace { FolderDetailRepository.selectTab(index) } },
                                    active = selectedTab?.isAllTab == false,
                                    tag = "folder_catalog_picker",
                                )
                            }
                            if (!rows) PageGridFilterMenus(filters, facets) { next -> changeKeepingPlace { filters = next } }
                        }
                    },
                    trailing = {
                        if (!rows && filters.isSet) {
                            RedesignPillButton(stringResource(Res.string.action_clear), onClick = { changeKeepingPlace { filters = PageGridFilters() } })
                        }
                        RedesignPillGroup(
                            options = listOf(false, true),
                            selected = rows,
                            label = { stringResource(if (it) Res.string.page_bar_rows else Res.string.page_bar_grid) },
                            onSelect = { next -> if (next != rows) changeKeepingPlace { rows = next } },
                            optionTag = { if (it) "folder_rows" else "folder_grid" },
                        )
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
                error != null || empty || (!rows && shown.isEmpty()) -> item(key = "state", span = { GridItemSpan(maxLineSpan) }) {
                    val filteredOut = !rows && tabItems.isNotEmpty() && shown.isEmpty()
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        when {
                            error != null -> PageStateCard(
                                title = stringResource(Res.string.folder_error_title),
                                message = error,
                                actionLabel = null,
                                onAction = null,
                            )
                            filteredOut -> PageStateCard(
                                title = stringResource(Res.string.folder_filtered_empty_title),
                                message = stringResource(Res.string.folder_filtered_empty_message),
                                actionLabel = stringResource(Res.string.page_bar_clear_filters),
                                onAction = { changeKeepingPlace { filters = PageGridFilters() } },
                            )
                            else -> PageStateCard(
                                title = stringResource(Res.string.folder_empty_title),
                                message = stringResource(Res.string.folder_empty_message),
                                actionLabel = null,
                                onAction = onEditCollection,
                            )
                        }
                    }
                }
                rows -> items(sections.withDuplicateSafeLazyKeys { it.key }, key = { it.lazyKey }) { keyed ->
                    val section = keyed.value.let { s -> if (landscape) s.copy(items = s.items.map { it.copy(posterShape = PosterShape.Landscape) }) else s }
                    HomeCatalogRowSection(
                        section = section,
                        entries = section.items,
                        sectionPadding = padding,
                        allowPortraitFallback = !landscape,
                        rowStyle = rowStyles[section.key] ?: HomeRowStyle.Automatic,
                        eyebrow = catalogTabs.firstOrNull { it.label == section.title }?.let(::folderRowEyebrow)
                            ?: section.addonName.takeIf { it.isNotBlank() },
                        onPosterLongClick = onCatalogPosterLongClick?.let { open -> { item -> open(section.key, item) } },
                        onViewAllClick = if (section.canOpenCatalog(18)) { { onCatalogClick(section) } } else null,
                        watchedKeys = watchedKeys,
                        onPosterClick = onPosterClick,
                    )
                }
                else -> items(shown.withDuplicateSafeLazyKeys { it.stableKey() }, key = { it.lazyKey }) { keyed ->
                    val item = keyed.value
                    // No fade-out: a filter re-pins the row (a scroll) while removed posters fade, and the grid then
                    // left those posters stuck on screen behind the empty-state card.
                    Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(260, easing = FastOutSlowInEasing), fadeOutSpec = null)) {
                        HomePosterCard(
                            item = item,
                            fillWidth = true,
                            useLandscapeBackdropMode = landscape,
                            allowPortraitFallback = !landscape,
                            respectGlobalPosterStyle = !landscape,
                            isWatched = WatchingState.isPosterWatched(watchedKeys, item),
                            onClick = { onPosterClick(item) },
                            quickLookRow = shown,
                        )
                    }
                }
            }
            // A short result keeps the row pinned: without room below it the grid clamps its scroll and
            // the row drops back under the hero (Details' and the service page's filler).
            item(key = "filter_room", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height((maxHeight - HomePageTopBarHeight - HomePageFilterRowHeight - 18.dp).coerceAtLeast(1.dp)))
            }
        }
        NuvioDesktopVerticalScrollbar(gridState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
    }
    }
    }
}

internal const val FolderFilterRowKey = "filter_row"

/** A folder row's eyebrow: where the catalog comes from (TMDB, Trakt) or its type. */
private fun folderRowEyebrow(tab: FolderTab): String? = when {
    tab.source?.isTmdb == true -> "TMDB"
    tab.source?.isTrakt == true -> "Trakt"
    else -> tab.typeLabel.takeIf { it.isNotBlank() }
}

/** Home's hero tile cycling the folder's titles, with the round frosted Back in its corner. */
@Composable
internal fun FolderHero(
    items: List<MetaPreview>,
    height: Dp,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onBack: () -> Unit,
    onItemClick: (MetaPreview) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(height).clip(shape).border(1.dp, RedesignColors.hairline, shape).testTag("folder_hero")) {
        AnimatedContent(
            targetState = items.isNotEmpty(),
            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
            label = "folder_hero_ready",
        ) { ready ->
            if (ready) {
                HomeHeroMetaLoadEffect(items)
                val hero = rememberHomeHeroState(items)
                CompositionLocalProvider(LocalHomeHeroHeightCap provides height) {
                    HomeHeroSection(
                        items = hero.items,
                        viewportHeight = 640.dp,
                        sectionPadding = 26.dp,
                        showImdbRating = hero.showImdbRating,
                        showMdbListRatings = hero.showMdbListRatings,
                        onItemClick = onItemClick,
                        expanded = expanded,
                        onToggleExpanded = onToggleExpanded,
                    )
                }
            } else {
                // Loading: the tile as a soft placeholder with faint logo and text lines.
                Box(Modifier.fillMaxSize().background(RedesignColors.fill)) {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(260.dp).height(44.dp).clip(RoundedCornerShape(10.dp)).background(RedesignColors.fill))
                        Box(Modifier.width(380.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(RedesignColors.fill))
                        Box(Modifier.width(320.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(RedesignColors.fill))
                    }
                }
            }
        }
        RedesignIconButton(
            stringResource(Res.string.action_back),
            onBack,
            modifier = Modifier.padding(18.dp).testTag("folder_back"),
            vector = Icons.AutoMirrored.Rounded.ArrowBack,
            size = 44.dp,
        )
    }
}

/** Sort, Type, Genre, Year, Rating and Watch status, each only when the items can answer it. */
@Composable
internal fun PageGridFilterMenus(filters: PageGridFilters, facets: PageGridFacets, onChange: (PageGridFilters) -> Unit) {
    val any = stringResource(Res.string.page_bar_any)
    RedesignMenuButton(
        label = stringResource(Res.string.page_bar_sort),
        options = listOf(
            RedesignMenuOption(PageGridSort.CatalogOrder, stringResource(Res.string.page_bar_sort_catalog)),
            RedesignMenuOption(PageGridSort.Rating, stringResource(Res.string.page_bar_sort_rating)),
            RedesignMenuOption(PageGridSort.Newest, stringResource(Res.string.page_bar_sort_newest)),
            RedesignMenuOption(PageGridSort.AToZ, stringResource(Res.string.page_bar_sort_az)),
        ),
        selected = filters.sort,
        onSelect = { onChange(filters.copy(sort = it)) },
        active = filters.sort != PageGridSort.CatalogOrder,
        tag = "page_sort",
    )
    if (facets.mixesTypes) {
        RedesignMenuButton(
            label = stringResource(Res.string.page_bar_type),
            options = listOf(
                RedesignMenuOption<PageGridType?>(null, any),
                RedesignMenuOption(PageGridType.Movies, stringResource(Res.string.page_bar_type_movies)),
                RedesignMenuOption(PageGridType.Shows, stringResource(Res.string.page_bar_type_shows)),
            ),
            selected = filters.type,
            onSelect = { onChange(filters.copy(type = it)) },
            active = filters.type != null,
            tag = "page_type",
        )
    }
    if (facets.genres.isNotEmpty()) {
        RedesignMenuButton(
            label = stringResource(Res.string.page_bar_genre),
            options = listOf(RedesignMenuOption<String?>(null, any)) + facets.genres.map { RedesignMenuOption<String?>(it, it) },
            selected = filters.genre,
            onSelect = { onChange(filters.copy(genre = it)) },
            active = filters.genre != null,
            tag = "page_genre",
        )
    }
    if (facets.decades.isNotEmpty()) {
        RedesignMenuButton(
            label = stringResource(Res.string.page_bar_year),
            options = listOf(RedesignMenuOption<Int?>(null, any)) + facets.decades.map { RedesignMenuOption<Int?>(it, stringResource(Res.string.page_bar_decade, it)) },
            selected = filters.decade,
            onSelect = { onChange(filters.copy(decade = it)) },
            active = filters.decade != null,
            tag = "page_year",
        )
    }
    if (facets.hasRatings) {
        RedesignMenuButton(
            label = stringResource(Res.string.page_bar_rating),
            options = listOf(RedesignMenuOption<Int?>(null, any)) + listOf(8, 7, 6, 5).map { RedesignMenuOption<Int?>(it, stringResource(Res.string.page_bar_rating_min, it)) },
            selected = filters.minRating,
            onSelect = { onChange(filters.copy(minRating = it)) },
            active = filters.minRating != null,
            tag = "page_rating",
        )
    }
    RedesignMenuButton(
        label = stringResource(Res.string.page_bar_watch_status),
        options = listOf(
            RedesignMenuOption<PageGridWatchStatus?>(null, any),
            RedesignMenuOption(PageGridWatchStatus.Unwatched, stringResource(Res.string.page_bar_watch_unwatched)),
            RedesignMenuOption(PageGridWatchStatus.Watched, stringResource(Res.string.page_bar_watch_watched)),
        ),
        selected = filters.watchStatus,
        onSelect = { onChange(filters.copy(watchStatus = it)) },
        active = filters.watchStatus != null,
        tag = "page_watch_status",
    )
}

/** Home's state card: what's missing and one way forward (Home design, "Folder · empty"). */
@Composable
internal fun PageStateCard(title: String, message: String, actionLabel: String?, onAction: (() -> Unit)?) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(440.dp).clip(shape).background(RedesignColors.fill).border(1.dp, RedesignColors.hairline, shape)
            .padding(horizontal = 28.dp, vertical = 26.dp).testTag("page_state_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(RedesignColors.fillStrong), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null, tint = RedesignColors.textSecondary)
        }
        Text(title, style = redesignText(18.sp, FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        Text(
            message,
            style = redesignText(14.sp, color = RedesignColors.textSecondary, lineHeight = 21.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (actionLabel != null && onAction != null) {
            RedesignPillButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = 18.dp), primary = true)
        }
    }
}
