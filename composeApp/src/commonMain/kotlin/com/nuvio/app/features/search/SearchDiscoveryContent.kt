package com.nuvio.app.features.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import com.nuvio.app.core.ui.Chip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.nuvio.app.core.ui.nuvio
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.catalog.DUPLICATE_CATALOG_PAGE_ADVANCE_LIMIT
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbEntityRailPageResult
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.search_availability_country
import nuvio.composeapp.generated.resources.search_chip_decade
import nuvio.composeapp.generated.resources.search_chip_language
import nuvio.composeapp.generated.resources.search_chip_mood
import nuvio.composeapp.generated.resources.search_chip_new
import nuvio.composeapp.generated.resources.search_chip_studio
import nuvio.composeapp.generated.resources.search_choose_country
import nuvio.composeapp.generated.resources.search_discovery_date_window
import nuvio.composeapp.generated.resources.search_discovery_empty
import nuvio.composeapp.generated.resources.search_discovery_empty_country
import nuvio.composeapp.generated.resources.search_discovery_failed
import nuvio.composeapp.generated.resources.search_discovery_retry
import nuvio.composeapp.generated.resources.search_discovery_visibility_empty
import nuvio.composeapp.generated.resources.search_remove_filter
import nuvio.composeapp.generated.resources.search_streaming_country
import org.jetbrains.compose.resources.stringResource

internal object SearchStreamingCountry {
    val override = MutableStateFlow(SearchPreferencesStorage.loadStreamingCountry())
    private var cachedRegions: Map<String, String>? = null

    fun set(code: String?) {
        SearchPreferencesStorage.saveStreamingCountry(code)
        override.value = code
    }

    suspend fun regions(): Map<String, String> =
        cachedRegions ?: TmdbMetadataService.watchRegions().also { cachedRegions = it }

    fun effective(override: String?, deviceRegion: String?, regions: Map<String, String>): String? =
        (override ?: deviceRegion)?.uppercase()?.takeIf(regions::containsKey)
}

internal data class SearchDiscoveryUi(
    val items: List<MetaPreview> = emptyList(),
    val country: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val needsCountry: Boolean = false,
    val hasMore: Boolean = false,
    val hidResults: Boolean = false,
    val load: () -> Unit = {},
)

internal val LocalSearchDiscoverySession = compositionLocalOf<SearchDiscoverySession?> { null }

/** A tag page's "View all": one carousel's ordering, for one medium (null = movies and shows). */
internal data class SearchShelfView(val sort: SearchShelfSort, val television: Boolean?)

internal class SearchDiscoverySession {
    var selection by mutableStateOf<SearchDiscovery?>(null)
        private set
    /** Desktop tag pages show carousels; non-null opens one carousel as a paged grid. */
    var view by mutableStateOf<SearchShelfView?>(null)
    private var pageKey: Any? = null
    private var pages = SearchDiscoveryPages()
    private var shelvesKey: Any? = null
    private var shelves = SearchDiscoveryShelves()
    /** Top 10 charts for the selected provider tag; survives "View all" and back. */
    val topTen = SearchTopTenCache()
    /** A service's own rows (the Service collections slot) for the selected provider tag. */
    val collectionRows = StreamingCollectionRowsCache()
    /** The tag page hero's own metadata store, so it never prunes Home's hero entries. */
    val heroMeta = com.nuvio.app.features.home.HomeHeroMetaStore()

    fun select(value: SearchDiscovery?) {
        if (selection == value) return
        pageKey = null
        view = null
        selection = value
    }

    fun shelvesFor(key: Any): SearchDiscoveryShelves {
        if (shelvesKey != key) {
            shelvesKey = key
            shelves = SearchDiscoveryShelves()
        }
        return shelves
    }

    fun pagesFor(key: Any, television: Boolean? = null): SearchDiscoveryPages {
        if (pageKey != key) {
            pageKey = key
            pages = SearchDiscoveryPages(television)
        }
        return pages
    }
}

internal class SearchDiscoveryPages(television: Boolean? = null) {
    var state by mutableStateOf(SearchDiscoveryUi(loading = true))
    var moviePage: Int? = 1.takeIf { television != true }
    var tvPage: Int? = 1.takeIf { television != false }
    var request by mutableStateOf(0)
    var completedRequest = -1
    var consecutiveUnchangedPages = 0
}

@Composable
internal fun rememberSearchDiscovery(
    selection: SearchDiscovery?,
    active: Boolean,
    hideUnplayable: Boolean,
    hideUnreleased: Boolean,
    view: SearchShelfView? = null,
    /** The region the tag page's carousels used (e.g. the fallback), so the grid shows the same catalog. */
    regionOverride: String? = null,
    loadPage: suspend (Boolean, Int, SearchDiscovery, String?, LocalDate) -> TmdbEntityRailPageResult? =
        { television, page, chip, country, today -> loadSearchDiscoveryMedium(television, page, chip, country, today, view?.sort) },
    filterResults: suspend (List<MetaPreview>) -> List<MetaPreview> = SearchRepository::filterDiscoveryResults,
): SearchDiscoveryUi {
    val countryOverride by SearchStreamingCountry.override.collectAsStateWithLifecycle()
    val deviceRegion = SearchPreferencesStorage.deviceRegion()
    val day = discoveryToday()
    val localSession = LocalSearchDiscoverySession.current
    val session = localSession ?: remember { SearchDiscoverySession() }
    val pageKey = listOf(selection, countryOverride, deviceRegion, day.takeIf { selection?.kind == DiscoveryKind.Recent }, hideUnplayable, hideUnreleased, view, regionOverride)
    val pages = remember(session, pageKey) { session.pagesFor(pageKey, view?.television) }
    val request = pages.request
    LaunchedEffect(selection, active, countryOverride, deviceRegion, day, hideUnplayable, hideUnreleased, request, regionOverride) {
        if (selection == null || !active || pages.completedRequest == request) return@LaunchedEffect
        if (pages.moviePage == null && pages.tvPage == null) return@LaunchedEffect
        pages.state = pages.state.copy(loading = true, failed = false)
        try {
            val region = when (val resolved = resolveDiscoveryRegion(selection, countryOverride, deviceRegion, regionOverride)) {
                DiscoveryRegion.Missing -> {
                    pages.completedRequest = request
                    pages.state = SearchDiscoveryUi(needsCountry = true)
                    return@LaunchedEffect
                }
                is DiscoveryRegion.Resolved -> {
                    if (resolved.name != null) pages.state = pages.state.copy(country = resolved.name)
                    resolved.code
                }
            }
            val (movie, tv) = coroutineScope {
                val movies = async { pages.moviePage?.let { loadPage(false, it, selection, region, day) } }
                val shows = async { pages.tvPage?.let { loadPage(true, it, selection, region, day) } }
                movies.await() to shows.await()
            }
            val incoming = movie?.items.orEmpty() + tv?.items.orEmpty()
            val next = filterResults(incoming)
            pages.moviePage = pages.moviePage?.plus(1)?.takeIf { movie?.hasMore == true }
            pages.tvPage = pages.tvPage?.plus(1)?.takeIf { tv?.hasMore == true }
            val items = (pages.state.items + next).distinctBy(MetaPreview::stableKey)
            pages.consecutiveUnchangedPages =
                if (items.size > pages.state.items.size) 0 else pages.consecutiveUnchangedPages + 1
            if (pages.consecutiveUnchangedPages >= DUPLICATE_CATALOG_PAGE_ADVANCE_LIMIT) {
                pages.moviePage = null
                pages.tvPage = null
            }
            pages.state = pages.state.copy(
                items = when {
                    view?.sort == SearchShelfSort.TopRated -> items.sortedByDescending { it.sortRating ?: -1.0 }
                    view?.sort == SearchShelfSort.New ||
                        (view == null && selection.kind == DiscoveryKind.Recent) -> items.sortedByDescending { it.rawReleaseDate }
                    else -> items.sortedByDescending { it.popularity ?: -1.0 }
                },
                loading = false,
                hasMore = pages.moviePage != null || pages.tvPage != null,
                hidResults = pages.state.hidResults || next.size < incoming.size,
            )
            pages.completedRequest = request
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            pages.completedRequest = request
            pages.state = pages.state.copy(loading = false, failed = true)
        }
    }
    if (selection == null) return SearchDiscoveryUi()
    return pages.state.copy(load = { if (!pages.state.loading && pages.request == pages.completedRequest) pages.request++ })
}

private sealed interface DiscoveryRegion {
    data object Missing : DiscoveryRegion
    data class Resolved(val code: String?, val name: String?) : DiscoveryRegion
}

/** Provider tags need a streaming country; other tags resolve to no region. */
private suspend fun resolveDiscoveryRegion(
    selection: SearchDiscovery,
    override: String?,
    deviceRegion: String?,
    pageRegion: String? = null,
): DiscoveryRegion {
    if (selection.kind != DiscoveryKind.Provider) return DiscoveryRegion.Resolved(null, null)
    val regions = SearchStreamingCountry.regions()
    val code = pageRegion?.takeIf(regions::containsKey)
        ?: SearchStreamingCountry.effective(override, deviceRegion, regions)
        ?: return DiscoveryRegion.Missing
    return DiscoveryRegion.Resolved(code, regions[code])
}

/** One carousel on a desktop tag page. */
internal data class SearchShelfKey(val sort: SearchShelfSort, val television: Boolean)

/** The carousels every tag has: each ordering shows shows first, then movies. */
internal val SEARCH_SHELF_KEYS = listOf(SearchShelfSort.Popular, SearchShelfSort.TopRated, SearchShelfSort.New).flatMap { sort ->
    listOf(SearchShelfKey(sort, television = true), SearchShelfKey(sort, television = false))
}

/** A tag's carousels: a streaming service adds its Originals (films only where the spec names a studio). */
internal fun shelfKeysFor(selection: SearchDiscovery?): List<SearchShelfKey> =
    listOf(true, false)
        .filter { selection?.originalsFilters(it) != null }
        .map { SearchShelfKey(SearchShelfSort.Originals, it) } + SEARCH_SHELF_KEYS

/** Rails that depend on the streaming country; a service's Originals are the same everywhere. */
private val SearchShelfKey.usesRegion: Boolean get() = sort != SearchShelfSort.Originals

internal data class SearchDiscoveryShelvesUi(
    val rails: Map<SearchShelfKey, List<MetaPreview>> = emptyMap(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val needsCountry: Boolean = false,
    val hidResults: Boolean = false,
    val retry: () -> Unit = {},
    /** The region the region-filtered rails were loaded for; null for tags without one. */
    val region: String? = null,
    /** Set when the streaming country had no catalog for the service and the fallback region is shown. */
    val fallbackCountry: String? = null,
) {
    fun asStatus() = SearchDiscoveryUi(failed = failed, needsCountry = needsCountry, hidResults = hidResults, load = retry)
    val isEmpty: Boolean get() = rails.values.all { it.isEmpty() }
}

internal class SearchDiscoveryShelves {
    var state by mutableStateOf(SearchDiscoveryShelvesUi(loading = true))
    var request by mutableStateOf(0)
    var completedRequest = -1
}

/**
 * Loads page 1 of every carousel for a tag in parallel. A rail that fails or has no TMDB query
 * (e.g. a movie-only genre on TV) comes back empty and is hidden; only all rails failing is an error.
 * A service with nothing in the streaming country (TMDB has no Lithuanian catalog for some) is
 * reloaded once for [STREAMING_FALLBACK_REGION]. Cached per selection in the session so returning
 * from "View all" doesn't reload.
 */
@Composable
internal fun rememberSearchDiscoveryShelves(
    selection: SearchDiscovery?,
    active: Boolean,
    hideUnplayable: Boolean,
    hideUnreleased: Boolean,
    loadRail: suspend (SearchShelfKey, SearchDiscovery, String?, LocalDate) -> TmdbEntityRailPageResult? =
        { key, chip, country, today -> loadSearchDiscoveryMedium(key.television, 1, chip, country, today, key.sort) },
    filterResults: suspend (List<MetaPreview>) -> List<MetaPreview> = SearchRepository::filterDiscoveryResults,
): SearchDiscoveryShelvesUi {
    val countryOverride by SearchStreamingCountry.override.collectAsStateWithLifecycle()
    val deviceRegion = SearchPreferencesStorage.deviceRegion()
    val day = discoveryToday()
    val session = LocalSearchDiscoverySession.current ?: remember { SearchDiscoverySession() }
    val key = listOf(selection, countryOverride, deviceRegion, day, hideUnplayable, hideUnreleased)
    val shelves = remember(session, key) { session.shelvesFor(key) }
    val request = shelves.request
    LaunchedEffect(selection, active, countryOverride, deviceRegion, day, hideUnplayable, hideUnreleased, request) {
        if (selection == null || !active || shelves.completedRequest == request) return@LaunchedEffect
        shelves.state = shelves.state.copy(loading = true, failed = false)
        // The region list comes from TMDB too: offline, the page shows its retry state instead of throwing.
        val discoveryRegion = try {
            resolveDiscoveryRegion(selection, countryOverride, deviceRegion)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            shelves.completedRequest = request
            shelves.state = SearchDiscoveryShelvesUi(failed = true)
            return@LaunchedEffect
        }
        val resolved = when (discoveryRegion) {
            DiscoveryRegion.Missing -> {
                shelves.completedRequest = request
                shelves.state = SearchDiscoveryShelvesUi(needsCountry = true)
                return@LaunchedEffect
            }
            is DiscoveryRegion.Resolved -> discoveryRegion
        }
        val loaded = loadTagShelves(selection, resolved.code, day, loadRail, filterResults)
        if (selection.kind == DiscoveryKind.Provider && loaded.region != null) StreamingCatalogRegion.record(selection.name, loaded.region)
        shelves.completedRequest = request
        shelves.state = SearchDiscoveryShelvesUi(
            rails = loaded.rails,
            failed = loaded.failed,
            hidResults = loaded.hidResults,
            region = loaded.region,
            fallbackCountry = if (!loaded.usedFallback) null else {
                runCatching { SearchStreamingCountry.regions()[STREAMING_FALLBACK_REGION] }.getOrNull() ?: STREAMING_FALLBACK_REGION
            },
        )
    }
    if (selection == null) return SearchDiscoveryShelvesUi()
    return shelves.state.copy(retry = { if (!shelves.state.loading) shelves.request++ })
}

private class ShelfLoad(val key: SearchShelfKey, val items: List<MetaPreview>, val incoming: Int, val failed: Boolean = false)

internal class TagShelvesLoad(
    val rails: Map<SearchShelfKey, List<MetaPreview>>,
    val failed: Boolean,
    val hidResults: Boolean,
    val region: String?,
    val usedFallback: Boolean,
)

/**
 * Page 1 of every carousel of a tag, in parallel. When a service has no titles at all in
 * [region] (the region-filtered rails all loaded and came back empty), those rails are loaded
 * once more for [STREAMING_FALLBACK_REGION]; a failed request never triggers the fallback.
 */
internal suspend fun loadTagShelves(
    selection: SearchDiscovery,
    region: String?,
    day: LocalDate,
    loadRail: suspend (SearchShelfKey, SearchDiscovery, String?, LocalDate) -> TmdbEntityRailPageResult?,
    filterResults: suspend (List<MetaPreview>) -> List<MetaPreview>,
): TagShelvesLoad {
    suspend fun load(keys: List<SearchShelfKey>, railRegion: String?) = coroutineScope {
        keys.map { shelf ->
            async {
                val page = try {
                    loadRail(shelf, selection, railRegion, day)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    return@async ShelfLoad(shelf, emptyList(), incoming = 0, failed = true)
                }
                val incoming = page?.items.orEmpty()
                ShelfLoad(shelf, filterResults(incoming).distinctBy(MetaPreview::stableKey), incoming.size)
            }
        }.map { it.await() }
    }
    var usedRegion = region
    var loads = load(shelfKeysFor(selection), region)
    val regional = loads.filter { it.key.usesRegion }
    val emptyRegion = selection.kind == DiscoveryKind.Provider && region != STREAMING_FALLBACK_REGION &&
        regional.none { it.failed } && regional.all { it.incoming == 0 }
    if (emptyRegion) {
        val fallback = load(regional.map { it.key }, STREAMING_FALLBACK_REGION)
        if (fallback.any { it.incoming > 0 }) {
            loads = loads.filterNot { it.key.usesRegion } + fallback
            usedRegion = STREAMING_FALLBACK_REGION
        }
    }
    return TagShelvesLoad(
        rails = loads.associate { it.key to it.items },
        failed = loads.all { it.failed },
        hidResults = loads.any { it.items.size < it.incoming },
        region = usedRegion,
        usedFallback = usedRegion != region,
    )
}

@Composable
internal fun SearchDiscoveryChips(
    suggestions: List<SearchDiscovery>,
    selected: SearchDiscovery?,
    country: String?,
    onSelect: (SearchDiscovery?) -> Unit,
    onStreamingCountrySettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow {
            items(suggestions) { suggestion ->
                val label = discoveryChipLabel(suggestion)
                Chip(
                    label = label,
                    selected = selected == suggestion,
                    onClick = { onSelect(suggestion.takeUnless { selected == it }) },
                    trailingIcon = if (selected == suggestion) {
                        { Icon(Icons.Rounded.Close, stringResource(Res.string.search_remove_filter)) }
                    } else null,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        if (selected?.kind == DiscoveryKind.Provider) {
            TextButton(onClick = onStreamingCountrySettings) {
                Text(
                    if (country == null) stringResource(Res.string.search_streaming_country)
                    else stringResource(Res.string.search_availability_country, country),
                )
            }
        }
        if (selected?.kind == DiscoveryKind.Recent) {
            val today = discoveryToday()
            Text(
                stringResource(Res.string.search_discovery_date_window, recentDiscoveryStart(today).toString(), today.toString()),
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
    }
}

@Composable
internal fun discoveryChipLabel(suggestion: SearchDiscovery): String = when (suggestion.kind) {
    DiscoveryKind.Provider, DiscoveryKind.Genre, DiscoveryKind.Subgenre -> suggestion.name
    DiscoveryKind.Mood -> stringResource(Res.string.search_chip_mood, suggestion.name)
    DiscoveryKind.Decade -> stringResource(Res.string.search_chip_decade, suggestion.name)
    DiscoveryKind.Language -> stringResource(Res.string.search_chip_language, suggestion.name)
    DiscoveryKind.Studio -> stringResource(Res.string.search_chip_studio, suggestion.name)
    DiscoveryKind.Recent -> stringResource(Res.string.search_chip_new)
}

internal fun SearchDiscoveryUi.showsStatus(): Boolean =
    failed || needsCountry || (!loading && !hasMore && items.isEmpty())

@Composable
internal fun discoveryStatusMessage(state: SearchDiscoveryUi): String = when {
    state.failed -> stringResource(Res.string.search_discovery_failed)
    state.needsCountry -> stringResource(Res.string.search_choose_country)
    state.hidResults -> stringResource(Res.string.search_discovery_visibility_empty)
    state.country != null -> stringResource(Res.string.search_discovery_empty_country, state.country)
    else -> stringResource(Res.string.search_discovery_empty)
}

internal fun LazyListScope.searchDiscoveryContent(
    state: SearchDiscoveryUi,
    columns: Int,
    landscape: Boolean,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    searchResultsContent(
        SearchUiState(results = state.items, isLoading = state.loading), columns, landscape,
        watchedKeys, fullyWatchedSeriesKeys, onPosterClick, onPosterLongClick, null,
    )
    if (state.showsStatus()) {
        item(key = "search-discovery-status") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val message = discoveryStatusMessage(state)
                Text(message, color = MaterialTheme.nuvio.colors.textMuted)
                if (state.failed) {
                    TextButton(onClick = state.load) {
                        Text(stringResource(Res.string.search_discovery_retry))
                    }
                }
            }
        }
    }
}
