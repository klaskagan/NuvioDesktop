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

internal class SearchDiscoverySession {
    var selection by mutableStateOf<SearchDiscovery?>(null)
        private set
    private var pageKey: Any? = null
    private var pages = SearchDiscoveryPages()

    fun select(value: SearchDiscovery?) {
        if (selection == value) return
        pageKey = null
        selection = value
    }

    fun pagesFor(key: Any): SearchDiscoveryPages {
        if (pageKey != key) {
            pageKey = key
            pages = SearchDiscoveryPages()
        }
        return pages
    }
}

internal class SearchDiscoveryPages {
    var state by mutableStateOf(SearchDiscoveryUi(loading = true))
    var moviePage: Int? = 1
    var tvPage: Int? = 1
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
    loadPage: suspend (Boolean, Int, SearchDiscovery, String?, LocalDate) -> TmdbEntityRailPageResult? =
        ::loadSearchDiscoveryMedium,
    filterResults: suspend (List<MetaPreview>) -> List<MetaPreview> = SearchRepository::filterDiscoveryResults,
): SearchDiscoveryUi {
    val countryOverride by SearchStreamingCountry.override.collectAsStateWithLifecycle()
    val deviceRegion = SearchPreferencesStorage.deviceRegion()
    val day = discoveryToday()
    val localSession = LocalSearchDiscoverySession.current
    val session = localSession ?: remember { SearchDiscoverySession() }
    val pageKey = listOf(selection, countryOverride, deviceRegion, day.takeIf { selection?.kind == DiscoveryKind.Recent }, hideUnplayable, hideUnreleased)
    val pages = remember(session, pageKey) { session.pagesFor(pageKey) }
    val request = pages.request
    LaunchedEffect(selection, active, countryOverride, deviceRegion, day, hideUnplayable, hideUnreleased, request) {
        if (selection == null || !active || pages.completedRequest == request) return@LaunchedEffect
        if (pages.moviePage == null && pages.tvPage == null) return@LaunchedEffect
        pages.state = pages.state.copy(loading = true, failed = false)
        try {
            val region = if (selection.kind == DiscoveryKind.Provider) {
                val regions = SearchStreamingCountry.regions()
                val code = SearchStreamingCountry.effective(countryOverride, deviceRegion, regions)
                if (code == null) {
                    pages.completedRequest = request
                    pages.state = SearchDiscoveryUi(needsCountry = true)
                    return@LaunchedEffect
                }
                pages.state = pages.state.copy(country = regions[code])
                code
            } else null
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
                items = if (selection.kind == DiscoveryKind.Recent) items.sortedByDescending { it.rawReleaseDate }
                else items.sortedByDescending { it.popularity ?: -1.0 },
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
