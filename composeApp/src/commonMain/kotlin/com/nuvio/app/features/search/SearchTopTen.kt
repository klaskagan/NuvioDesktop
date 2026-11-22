package com.nuvio.app.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.HomeCatalogSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/*
 * FlixPatrol "Top 10" charts are published by catalog addons (e.g. AIOMetadata) under ids like
 * `flixpatrol.netflix.lt.series`: service slug (see [StreamingProviderSpec.slug]), country, kind.
 */

/** One installed addon catalog, reduced to what Top 10 matching needs. */
internal data class SearchAddonCatalog(
    val manifestUrl: String,
    val addonName: String,
    val type: String,
    val id: String,
    val name: String,
)

/** A matched chart. [television] is null for a combined movies-and-shows chart (`all`). */
internal data class SearchTopTenCatalog(val catalog: SearchAddonCatalog, val television: Boolean?)

/**
 * Charts for a provider tag, shows first, then movies, then combined; one per kind. Each kind
 * prefers the chart for [region] (the streaming country), then [STREAMING_FALLBACK_REGION], then
 * whichever chart an installed addon lists first. Empty for non-provider tags or without a chart.
 */
internal fun topTenCatalogsFor(
    selection: SearchDiscovery,
    catalogs: List<SearchAddonCatalog>,
    region: String? = null,
): List<SearchTopTenCatalog> {
    if (selection.kind != DiscoveryKind.Provider) return emptyList()
    val slug = streamingProviderSpec(selection.name)?.slug ?: return emptyList()
    val prefix = "flixpatrol.$slug."
    val preferredRegions = listOfNotNull(region?.lowercase(), STREAMING_FALLBACK_REGION.lowercase())
    fun SearchAddonCatalog.regionRank(): Int =
        preferredRegions.indexOf(id.removePrefix(prefix).substringBeforeLast('.', "").lowercase())
            .let { if (it < 0) preferredRegions.size else it }
    return catalogs
        .filter { it.id.startsWith(prefix) }
        .mapNotNull { catalog ->
            when (catalog.id.substringAfterLast('.')) {
                "series" -> SearchTopTenCatalog(catalog, television = true)
                "movie" -> SearchTopTenCatalog(catalog, television = false)
                "all" -> SearchTopTenCatalog(catalog, television = null)
                else -> null
            }
        }
        .groupBy { it.television }
        .map { (_, charts) -> charts.minBy { it.catalog.regionRank() } }
        .sortedBy { when (it.television) { true -> 0; false -> 1; null -> 2 } }
}

internal fun List<ManagedAddon>.searchAddonCatalogs(): List<SearchAddonCatalog> =
    enabledAddons().flatMap { addon ->
        val manifest = addon.manifest ?: return@flatMap emptyList()
        manifest.catalogs.map { catalog ->
            SearchAddonCatalog(manifest.transportUrl, addon.displayTitle, catalog.type, catalog.id, catalog.name)
        }
    }

internal data class SearchTopTenUi(
    val sections: List<Pair<SearchTopTenCatalog, HomeCatalogSection>> = emptyList(),
    val loading: Boolean = false,
)

internal class SearchTopTenCache {
    var key: Any? = null
    var state by mutableStateOf(SearchTopTenUi())
}

/**
 * Loads the matched charts in parallel. A chart that fails or comes back empty is dropped; the
 * rows are rankings, so items are kept in the addon's order without hide-unplayable filtering
 * (removing entries would renumber the chart).
 */
@Composable
internal fun rememberSearchTopTen(
    selection: SearchDiscovery?,
    addons: List<ManagedAddon>,
    active: Boolean,
): SearchTopTenUi {
    val session = LocalSearchDiscoverySession.current ?: remember { SearchDiscoverySession() }
    val cache = session.topTen
    val countryOverride by SearchStreamingCountry.override.collectAsStateWithLifecycle()
    val region = countryOverride ?: SearchPreferencesStorage.deviceRegion()
    val charts = remember(selection, addons, region) {
        selection?.let { topTenCatalogsFor(it, addons.searchAddonCatalogs(), region) }.orEmpty()
    }
    val key = charts.map { it.catalog }
    LaunchedEffect(key, active) {
        if (!active || cache.key == key) return@LaunchedEffect
        if (charts.isEmpty()) {
            cache.key = key
            cache.state = SearchTopTenUi()
            return@LaunchedEffect
        }
        cache.state = SearchTopTenUi(loading = true)
        val sections = coroutineScope {
            charts.map { chart ->
                async {
                    val page = try {
                        fetchCatalogPage(
                            manifestUrl = chart.catalog.manifestUrl,
                            type = chart.catalog.type,
                            catalogId = chart.catalog.id,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    val items = page?.items.orEmpty().take(TOP_TEN_SIZE)
                    if (items.isEmpty()) null else chart to HomeCatalogSection(
                        key = "search-top10:${chart.catalog.manifestUrl}:${chart.catalog.type}:${chart.catalog.id}",
                        title = chart.catalog.name,
                        subtitle = chart.catalog.addonName,
                        addonName = chart.catalog.addonName,
                        target = CatalogTarget.Addon(
                            manifestUrl = chart.catalog.manifestUrl,
                            contentType = chart.catalog.type,
                            catalogId = chart.catalog.id,
                            genre = null,
                            supportsPagination = false,
                        ),
                        items = items,
                        canExpand = false,
                    )
                }
            }.mapNotNull { it.await() }
        }
        cache.key = key
        cache.state = SearchTopTenUi(sections = sections)
    }
    return if (charts.isEmpty()) SearchTopTenUi() else cache.state
}

private const val TOP_TEN_SIZE = 10
