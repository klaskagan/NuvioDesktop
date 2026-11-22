package com.nuvio.app.features.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.StreamingCollection
import com.nuvio.app.features.details.components.DetailPosterRailSection
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeHeroMetaLoadEffect
import com.nuvio.app.features.home.HomeHeroMetaStore
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeHeroSection
import com.nuvio.app.features.home.rememberHomeHeroState
import com.nuvio.app.features.home.stableKey
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_reorder
import nuvio.composeapp.generated.resources.action_reset
import nuvio.composeapp.generated.resources.search_catalog_fallback
import nuvio.composeapp.generated.resources.search_discovery_retry
import nuvio.composeapp.generated.resources.search_row_originals_movies
import nuvio.composeapp.generated.resources.search_row_originals_shows
import nuvio.composeapp.generated.resources.search_row_service_collections
import nuvio.composeapp.generated.resources.search_row_service_collections_hint
import nuvio.composeapp.generated.resources.search_row_top10_movies
import nuvio.composeapp.generated.resources.search_row_top10_shows
import nuvio.composeapp.generated.resources.search_rows_customize
import nuvio.composeapp.generated.resources.search_rows_customize_hint
import nuvio.composeapp.generated.resources.search_shelf_new_movies
import nuvio.composeapp.generated.resources.search_shelf_new_shows
import nuvio.composeapp.generated.resources.search_shelf_originals_movies
import nuvio.composeapp.generated.resources.search_shelf_originals_shows
import nuvio.composeapp.generated.resources.search_shelf_popular_movies
import nuvio.composeapp.generated.resources.search_shelf_popular_shows
import nuvio.composeapp.generated.resources.search_shelf_top_rated_movies
import nuvio.composeapp.generated.resources.search_shelf_top_rated_shows
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// A tag page (desktop): Home's rotating hero, then rows in the user's order. Search's provider
// tags and the streaming collection's folders both render this, so the two always match.

private fun SearchShelfKey.visibleIn(scope: SearchScope) = when (scope) {
    SearchScope.Movies -> !television
    SearchScope.Shows -> television
    else -> true
}

private fun SearchTopTenCatalog.visibleIn(scope: SearchScope) = when (scope) {
    SearchScope.Movies -> television == false
    SearchScope.Shows -> television == true
    else -> true
}

/** A carousel's title; a service's Originals carry its name ("Netflix original shows"). */
@Composable
internal fun shelfTitle(key: SearchShelfKey, selection: SearchDiscovery? = null): String = when (key.sort) {
    SearchShelfSort.Originals -> stringResource(
        if (key.television) Res.string.search_shelf_originals_shows else Res.string.search_shelf_originals_movies,
        selection?.name.orEmpty(),
    )
    SearchShelfSort.Popular -> stringResource(if (key.television) Res.string.search_shelf_popular_shows else Res.string.search_shelf_popular_movies)
    SearchShelfSort.TopRated -> stringResource(if (key.television) Res.string.search_shelf_top_rated_shows else Res.string.search_shelf_top_rated_movies)
    SearchShelfSort.New -> stringResource(if (key.television) Res.string.search_shelf_new_shows else Res.string.search_shelf_new_movies)
}

/** Taller than the results spotlight, shorter than Home's full-window hero. */
private val SearchTagHeroViewport = 640.dp

/**
 * The tag page hero rotates through the service's Top 10 (shows and movies interleaved) or, for
 * genre-style tags and services without a chart, the most popular titles. The first item also
 * drives the ambient backdrop.
 */
internal fun tagHeroItems(topTen: SearchTopTenUi, shelves: SearchDiscoveryShelvesUi, scope: SearchScope): List<MetaPreview> {
    fun interleave(shows: List<MetaPreview>, movies: List<MetaPreview>) = when (scope) {
        SearchScope.Movies -> movies
        SearchScope.Shows -> shows
        else -> buildList {
            for (i in 0 until maxOf(shows.size, movies.size)) {
                shows.getOrNull(i)?.let(::add)
                movies.getOrNull(i)?.let(::add)
            }
        }
    }
    val charts = topTen.sections.filter { (chart, _) -> chart.visibleIn(scope) }
    val fromCharts = if (charts.isEmpty()) emptyList() else interleave(
        charts.filter { it.first.television != false }.flatMap { it.second.items },
        charts.filter { it.first.television == false }.flatMap { it.second.items },
    )
    val pool = fromCharts.ifEmpty {
        interleave(
            shelves.rails[SearchShelfKey(SearchShelfSort.Popular, television = true)].orEmpty(),
            shelves.rails[SearchShelfKey(SearchShelfSort.Popular, television = false)].orEmpty(),
        )
    }
    return pool.distinctBy { it.stableKey() }.take(TAG_HERO_SIZE)
}

private const val TAG_HERO_SIZE = 8

/** One row of a tag page, resolved from the layout: what [streamingProviderPage] draws, in order. */
internal sealed interface TagPageRow {
    val key: String

    data class Chart(val chart: SearchTopTenCatalog, val section: HomeCatalogSection) : TagPageRow {
        override val key get() = "tag-top10:${section.key}"
    }

    data class Rail(val shelf: SearchShelfKey, val items: List<MetaPreview>) : TagPageRow {
        override val key get() = "tag-rail:${shelf.sort}:${shelf.television}"
    }

    /** One of the service's own rows, filling the Service collections slot. */
    data class Collection(val section: HomeCatalogSection, val items: List<MetaPreview>) : TagPageRow {
        override val key get() = "tag-collection:${section.key}"
    }
}

/**
 * The page's rows in the user's order. Hidden rows, rows the tag doesn't have (no chart, no
 * original films, an empty slot) and rows without titles for the active tab are skipped, so
 * they take no space and the rest keep their relative order.
 */
internal fun tagPageRows(
    layout: StreamingRowLayout,
    shelves: SearchDiscoveryShelvesUi,
    topTen: SearchTopTenUi,
    collectionRows: List<HomeCatalogSection>,
    scope: SearchScope,
): List<TagPageRow> = layout.visibleOrder.flatMap { id ->
    when (id) {
        StreamingRowId.Top10Shows, StreamingRowId.Top10Movies ->
            topTen.sections
                .filter { (chart, _) -> chart.rowId() == id && chart.visibleIn(scope) }
                .map { (chart, section) -> TagPageRow.Chart(chart, section) }
        StreamingRowId.ServiceCollections -> collectionRows.mapNotNull { section ->
            section.items.inScope(scope).takeIf { it.isNotEmpty() }?.let { TagPageRow.Collection(section, it) }
        }
        else -> shelves.rails.entries
            .filter { (shelf, items) -> shelf.rowId() == id && shelf.visibleIn(scope) && items.isNotEmpty() }
            .map { (shelf, items) -> TagPageRow.Rail(shelf, items) }
    }
}

internal fun LazyListScope.streamingProviderPage(
    selection: SearchDiscovery,
    shelves: SearchDiscoveryShelvesUi,
    topTen: SearchTopTenUi,
    collectionRows: List<HomeCatalogSection>,
    layout: StreamingRowLayout,
    heroMeta: HomeHeroMetaStore,
    scope: SearchScope,
    gutter: Dp,
    itemModifier: LazyItemScope.() -> Modifier,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onViewAll: (SearchShelfView) -> Unit,
    onCollectionRowViewAll: ((HomeCatalogSection) -> Unit)? = null,
) {
    val heroItems = tagHeroItems(topTen, shelves, scope)
    if (heroItems.isNotEmpty()) {
        item(key = "tag-hero") {
            // Home's rotating hero (auto-advance, progress dots, logo, ratings row), as a card in the column.
            HomeHeroMetaLoadEffect(heroItems, heroMeta)
            val hero = rememberHomeHeroState(heroItems, heroMeta)
            Box(itemModifier().padding(horizontal = gutter).padding(top = 22.dp).clip(RoundedCornerShape(18.dp))) {
                HomeHeroSection(
                    items = hero.items,
                    viewportHeight = SearchTagHeroViewport,
                    sectionPadding = 28.dp,
                    showImdbRating = hero.showImdbRating,
                    showMdbListRatings = hero.showMdbListRatings,
                    onItemClick = onPosterClick,
                )
            }
        }
    } else if (shelves.loading || topTen.loading) {
        item(key = "tag-skeleton-spotlight") {
            Box(
                itemModifier().padding(horizontal = gutter).padding(top = 22.dp).fillMaxWidth().height(300.dp)
                    .clip(RoundedCornerShape(18.dp)).background(RedesignColors.fill),
            )
        }
    }
    tagPageRows(layout, shelves, topTen, collectionRows, scope).forEach { row ->
        item(key = row.key) {
            when (row) {
                // What's hot on the service right now, in the app's numbered ranking style.
                is TagPageRow.Chart -> HomeCatalogRowSection(
                    section = row.section,
                    modifier = itemModifier().padding(top = 14.dp),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    sectionPadding = gutter,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    rowStyle = HomeRowStyle.NumberedRanking,
                )
                is TagPageRow.Rail -> DetailPosterRailSection(
                    title = shelfTitle(row.shelf, selection),
                    items = row.items,
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    headerHorizontalPadding = gutter,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    onViewAllClick = { onViewAll(SearchShelfView(row.shelf.sort, row.shelf.television)) },
                    viewAllPillSize = NuvioViewAllPillSize.Compact,
                    modifier = itemModifier().padding(top = 14.dp),
                )
                is TagPageRow.Collection -> DetailPosterRailSection(
                    title = row.section.title,
                    items = row.items,
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    headerHorizontalPadding = gutter,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    onViewAllClick = onCollectionRowViewAll?.takeIf { row.section.hasMore }?.let { open -> { open(row.section) } },
                    viewAllPillSize = NuvioViewAllPillSize.Compact,
                    modifier = itemModifier().padding(top = 14.dp),
                )
            }
        }
    }
    if (shelves.loading && shelves.isEmpty) {
        items(3, key = { "tag-skeleton-row:$it" }) {
            com.nuvio.app.features.home.components.HomeSkeletonRow(
                modifier = itemModifier().padding(top = 12.dp),
                horizontalPadding = gutter,
            )
        }
    }
    if (!shelves.loading && (shelves.failed || shelves.needsCountry || shelves.isEmpty)) {
        item(key = "tag-status") {
            val status = shelves.asStatus()
            Column(
                itemModifier().fillMaxWidth().padding(horizontal = gutter).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(discoveryStatusMessage(status), style = redesignText(15.sp, color = RedesignColors.textSecondary), textAlign = TextAlign.Center)
                if (shelves.failed) {
                    Spacer(Modifier.height(14.dp))
                    SearchPillButton(stringResource(Res.string.search_discovery_retry), primary = true, onClick = shelves.retry)
                }
            }
        }
    }
}

// ---------- Service collections slot ----------

internal class StreamingCollectionRowsCache {
    var key: Any? = null
    var state by mutableStateOf<List<HomeCatalogSection>>(emptyList())
}

/**
 * A service's own rows: the sources of its folder in the streaming collection, queried for the
 * region the page's carousels used. Empty for other tags, and until the carousels know the region.
 * It only observes collections; Home loads (and seeds) them at launch.
 */
@Composable
internal fun rememberStreamingCollectionRows(
    selection: SearchDiscovery?,
    region: String?,
    addons: List<ManagedAddon>,
    active: Boolean,
    filterResults: suspend (List<MetaPreview>) -> List<MetaPreview> = SearchRepository::filterDiscoveryResults,
    loadRows: suspend (CollectionFolder, String, List<ManagedAddon>, suspend (List<MetaPreview>) -> List<MetaPreview>) -> List<HomeCatalogSection> =
        StreamingCollection::loadRows,
): List<HomeCatalogSection> {
    val session = LocalSearchDiscoverySession.current ?: remember { SearchDiscoverySession() }
    val cache = session.collectionRows
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()
    val folder = selection?.takeIf { it.kind == DiscoveryKind.Provider }?.let { StreamingCollection.folderFor(collections, it.name) }
    val sources = folder?.resolvedSources.orEmpty()
    val key = listOf(folder?.id, sources, region)
    LaunchedEffect(key, active, addons) {
        if (!active || cache.key == key) return@LaunchedEffect
        if (folder == null || sources.isEmpty()) {
            cache.key = key
            cache.state = emptyList()
            return@LaunchedEffect
        }
        if (region == null) return@LaunchedEffect
        val rows = loadRows(folder, region, addons, filterResults)
        cache.key = key
        cache.state = rows
    }
    return if (cache.key == key) cache.state else emptyList()
}

// ---------- Header controls: tabs, fallback note, Customize rows ----------

/** A tag page's header row: All / Movies / Shows, the fallback-catalog note, and Customize rows. */
@Composable
internal fun TagPageControls(
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    fallbackCountry: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SearchScopeTabs(selected = scope, counts = null, onSelect = onScopeChange)
        Spacer(Modifier.weight(1f))
        // Shown beside the tabs, so it never moves the rows below when it appears.
        AnimatedVisibility(fallbackCountry != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            var shown by remember { mutableStateOf(fallbackCountry) }
            if (fallbackCountry != null) shown = fallbackCountry
            Text(
                stringResource(Res.string.search_catalog_fallback, shown.orEmpty()),
                style = redesignText(12.sp, color = RedesignColors.textMuted),
                maxLines = 1,
                modifier = Modifier.padding(end = 14.dp, bottom = 15.dp).testTag("tag_fallback_catalog"),
            )
        }
        TagRowsCustomizeButton(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun TagRowsCustomizeButton(modifier: Modifier = Modifier) {
    val layout by remember {
        StreamingRowLayoutRepository.ensureLoaded()
        StreamingRowLayoutRepository.layout
    }.collectAsStateWithLifecycle()
    // The transition state keeps the popup composed while the panel fades out.
    val panel = remember { MutableTransitionState(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val open = panel.targetState
    val shape = RoundedCornerShape(999.dp)
    val fill by animateColorAsState(
        when {
            open -> RedesignColors.fillStrong
            hovered -> Color.White.copy(alpha = 0.10f)
            else -> RedesignColors.fill
        },
        tween(140),
        label = "customize_rows_fill",
    )
    val label = stringResource(Res.string.search_rows_customize)
    Box(modifier) {
        Row(
            Modifier.clip(shape).background(fill)
                .border(1.dp, if (open) RedesignColors.hairlineFocused.copy(alpha = 0.35f) else RedesignColors.hairline, shape)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { panel.targetState = !panel.targetState }
                .padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)
                .testTag("tag_rows_customize"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = redesignText(13.sp, FontWeight.Medium), maxLines = 1)
        }
        if (panel.currentState || panel.targetState) {
            val dismiss = { panel.targetState = false }
            // Esc closes the panel instead of going Back; it stays claimed until the panel is gone.
            DisposableEffect(Unit) {
                val handler: () -> Boolean = { dismiss(); true }
                AppKeyboardShortcuts.overlayEscape = handler
                onDispose { if (AppKeyboardShortcuts.overlayEscape === handler) AppKeyboardShortcuts.overlayEscape = null }
            }
            val offsetY = with(LocalDensity.current) { 42.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, offsetY),
                onDismissRequest = dismiss,
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = panel,
                    enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = TransformOrigin(1f, 0f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = TransformOrigin(1f, 0f)),
                ) {
                    TagRowsPanel(layout)
                }
            }
        }
    }
}

@Composable
private fun StreamingRowId.label(): String = stringResource(
    when (this) {
        StreamingRowId.Top10Shows -> Res.string.search_row_top10_shows
        StreamingRowId.Top10Movies -> Res.string.search_row_top10_movies
        StreamingRowId.OriginalsShows -> Res.string.search_row_originals_shows
        StreamingRowId.OriginalsMovies -> Res.string.search_row_originals_movies
        StreamingRowId.ServiceCollections -> Res.string.search_row_service_collections
        StreamingRowId.PopularShows -> Res.string.search_shelf_popular_shows
        StreamingRowId.PopularMovies -> Res.string.search_shelf_popular_movies
        StreamingRowId.TopRatedShows -> Res.string.search_shelf_top_rated_shows
        StreamingRowId.TopRatedMovies -> Res.string.search_shelf_top_rated_movies
        StreamingRowId.NewShows -> Res.string.search_shelf_new_shows
        StreamingRowId.NewMovies -> Res.string.search_shelf_new_movies
    },
)

/** Drag to reorder, switch to hide; every change applies to the page behind it straight away. */
@Composable
private fun TagRowsPanel(layout: StreamingRowLayout) {
    val shape = RoundedCornerShape(14.dp)
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        StreamingRowLayoutRepository.moveByIndex(from.index, to.index)
    }
    Column(
        Modifier.width(340.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Color(0xFF17181C))
            .border(1.dp, RedesignColors.hairline, shape)
            .padding(vertical = 12.dp)
            .testTag("tag_rows_panel"),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.search_rows_customize), style = redesignText(15.sp, FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            SearchTextAction(stringResource(Res.string.action_reset), onClick = StreamingRowLayoutRepository::reset)
        }
        Text(
            stringResource(Res.string.search_rows_customize_hint),
            style = redesignText(12.sp, color = RedesignColors.textMuted, lineHeight = 16.sp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = listState) {
            itemsIndexed(layout.order, key = { _, id -> id.name }) { _, id ->
                ReorderableItem(reorderState, key = id.name) { dragging ->
                    val visible = id !in layout.hidden
                    val rowFill by animateColorAsState(if (dragging) RedesignColors.fillStrong else Color.Transparent, tween(140), label = "row_drag_fill")
                    val lift by animateDpAsState(if (dragging) 6.dp else 0.dp, tween(140), label = "row_drag_lift")
                    val name = id.label()
                    val reorder = stringResource(Res.string.action_reorder)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                            .shadow(lift, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(rowFill)
                            .padding(start = 4.dp, end = 8.dp)
                            .testTag("tag_row_${id.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp).draggableHandle().semantics { contentDescription = "$reorder: $name" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = null, tint = RedesignColors.textMuted, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(
                                name,
                                style = redesignText(14.sp, FontWeight.Medium, if (visible) RedesignColors.textPrimary else RedesignColors.textMuted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (id == StreamingRowId.ServiceCollections) {
                                Text(
                                    stringResource(Res.string.search_row_service_collections_hint),
                                    style = redesignText(12.sp, color = RedesignColors.textMuted, lineHeight = 15.sp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Switch(
                            checked = visible,
                            onCheckedChange = { StreamingRowLayoutRepository.setHidden(id, !it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF0E0F12),
                                checkedTrackColor = RedesignColors.textPrimary,
                                uncheckedThumbColor = RedesignColors.textMuted,
                                uncheckedTrackColor = RedesignColors.fill,
                                uncheckedBorderColor = RedesignColors.hairline,
                            ),
                            // Material's switch is sized for touch; the panel is a dense pointer list.
                            modifier = Modifier.height(36.dp).scale(0.72f).semantics { contentDescription = name },
                        )
                    }
                }
            }
        }
    }
}
