package com.nuvio.app.features.collection

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.trakt.TraktPublicListSourceResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal fun CollectionSource.mediaSources(): List<CollectionSource> =
    if (mediaType.equals("BOTH", true)) listOf(copy(mediaType = "movie"), copy(mediaType = "tv")) else listOf(this)

internal suspend fun resolveSeasonalSource(source: CollectionSource, page: Int = 1): CatalogPage {
    val result = when {
    source.isTmdb -> TmdbCollectionSourceResolver.resolve(source, page)
    source.isTrakt -> TraktPublicListSourceResolver.resolve(source, page,
        requireAppliedSort = source.sortBy in setOf("percentage", "title", "released"))
    else -> {
        val catalog = source.addonCatalogSource() ?: error("Invalid catalog source")
        val resolved = AddonRepository.uiState.value.addons.findCollectionCatalog(catalog) ?: error("Catalog source unavailable")
        fetchCatalogPage(resolved.addon.manifestUrl, catalog.type, catalog.catalogId,
            genre = catalog.genre, skip = page.takeIf { it > 1 })
    }
    }
    val slots = Semaphore(4)
    val items = coroutineScope { result.items.map { item -> async {
        if (item.canonicalMediaKey != null || !item.id.startsWith("tt") ||
            com.nuvio.app.features.tmdb.TmdbSettingsRepository.effectiveApiKey().isBlank()) item
        else slots.withPermit {
            try {
                val tmdb = com.nuvio.app.features.tmdb.TmdbService.ensureTmdbId(item.id, item.type)
                if (tmdb == null) item else item.copy(canonicalMediaKey = "${item.type}:tmdb:${tmdb.removePrefix("tmdb:")}")
            } catch (error: CancellationException) { throw error } catch (_: Exception) { item }
        }
    } }.awaitAll() }
    return result.copy(items = items)
}

internal fun com.nuvio.app.features.home.MetaPreview.seasonalIdentity(): String = canonicalMediaKey ?: "${type}:${id}"
