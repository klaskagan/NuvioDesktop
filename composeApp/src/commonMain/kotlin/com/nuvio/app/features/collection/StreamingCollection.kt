package com.nuvio.app.features.collection

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.search.SearchDiscovery
import com.nuvio.app.features.search.StreamingCatalogRegion
import com.nuvio.app.features.search.StreamingProviderSpec
import com.nuvio.app.features.search.searchProviderDiscovery
import com.nuvio.app.features.search.streamingProviderSpecs
import com.nuvio.app.features.trakt.TraktPublicListSourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The streaming-services collection whose folders open the same service page as Search's provider
 * tags. It is seeded once as a copy of the community streaming collection's artwork (that
 * collection is left untouched) and is identified by stable ids, so no stored field marks it.
 *
 * A folder's own sources are the service's extra rows (Marvel, Pixar…): they fill the page's
 * "Service collections" slot and stay editable in the collection editor like any other source.
 */
object StreamingCollection {
    const val ID = "nuvio-streaming"
    const val VERSION = 1

    internal fun folderId(spec: StreamingProviderSpec) = "streaming_${spec.slug}"

    /** The provider tag a folder opens; null for other collections and for folders the user added. */
    internal fun provider(collectionId: String, folderId: String): SearchDiscovery? {
        if (collectionId != ID) return null
        val spec = streamingProviderSpecs.firstOrNull { folderId(it) == folderId } ?: return null
        return searchProviderDiscovery(spec.name)
    }

    internal fun folderFor(collections: List<Collection>, provider: String): CollectionFolder? {
        val spec = streamingProviderSpecs.firstOrNull { it.name == provider } ?: return null
        return collections.firstOrNull { it.id == ID }?.folders?.firstOrNull { it.id == folderId(spec) }
    }

    /** The community collection the artwork is copied from. */
    internal fun source(collections: List<Collection>): Collection? =
        collections.firstOrNull { it.id != ID && it.isCommunityStreamingCollection() }

    /**
     * One folder per service the source collection has, in its order, with that folder's artwork.
     * Folders that aren't a streaming service (Trakt, IMDB Top 250) are left out. Null when there
     * is nothing to copy from yet, so seeding can wait for the first sync.
     */
    internal fun seedFrom(collections: List<Collection>): Collection? {
        val source = source(collections) ?: return null
        val seen = mutableSetOf<String>()
        val folders = source.folders.mapNotNull { folder ->
            val provider = searchProviderDiscovery(folder.title) ?: return@mapNotNull null
            val spec = streamingProviderSpecs.firstOrNull { it.name == provider.name } ?: return@mapNotNull null
            if (!seen.add(spec.name)) return@mapNotNull null
            folder.copy(
                id = folderId(spec),
                sources = spec.brandRows.map { row ->
                    CollectionSource(
                        provider = "tmdb", tmdbSourceType = "DISCOVER", title = row.title,
                        mediaType = "BOTH", sortBy = "popularity.desc",
                        // No region: the page fills in the streaming country (see withStreamingRegion).
                        filters = TmdbCollectionFilters(withCompanies = row.companies, withWatchProviders = provider.value),
                    )
                },
                catalogSources = emptyList(),
                seasonalThemeId = null,
            )
        }
        if (folders.isEmpty()) return null
        return Collection(
            id = ID, title = source.title, backdropImageUrl = source.backdropImageUrl,
            pinToTop = source.pinToTop, viewMode = source.viewMode, showAllTab = false, folders = folders,
        )
    }

    /** A service folder's source as the page queries it: blank watch regions follow the page's region. */
    internal suspend fun regionalSource(collectionId: String, folderId: String, source: CollectionSource): CollectionSource {
        val provider = provider(collectionId, folderId) ?: return source
        return source.withStreamingRegion(StreamingCatalogRegion.forProvider(provider.name))
    }

    /**
     * Loads the first page of each of a service folder's sources as a row. A source that fails or
     * comes back empty is dropped. Rows open as a collection source, so "View all" pages the same query.
     */
    internal suspend fun loadRows(
        folder: CollectionFolder,
        region: String,
        addons: List<ManagedAddon>,
        filterResults: suspend (List<MetaPreview>) -> List<MetaPreview>,
    ): List<HomeCatalogSection> = coroutineScope {
        folder.resolvedSources.map { source ->
            async {
                val catalogSource = source.addonCatalogSource()
                val resolvedCatalog = catalogSource?.let(addons::findCollectionCatalog)
                val page = try {
                    when {
                        source.isTmdb -> TmdbCollectionSourceResolver.resolve(source.withStreamingRegion(region))
                        source.isTrakt -> TraktPublicListSourceResolver.resolve(source)
                        catalogSource != null && resolvedCatalog != null -> fetchCatalogPage(
                            manifestUrl = resolvedCatalog.addon.manifestUrl,
                            type = catalogSource.type,
                            catalogId = catalogSource.catalogId,
                            genre = catalogSource.genre,
                        )
                        else -> null
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                val items = filterResults(page?.items.orEmpty()).distinctBy(MetaPreview::stableKey)
                if (items.isEmpty()) return@async null
                val sourceKey = source.catalogRouteKey()
                HomeCatalogSection(
                    key = collectionShelfKey(ID, folder.id, sourceKey),
                    title = source.title?.takeIf { it.isNotBlank() } ?: resolvedCatalog?.catalog?.name ?: folder.title,
                    subtitle = folder.title,
                    addonName = "",
                    target = CatalogTarget.CollectionSource(
                        collectionId = ID,
                        folderId = folder.id,
                        sourceKey = sourceKey,
                        contentType = catalogSource?.type
                            ?: if (TmdbCollectionMediaType.fromString(source.mediaType) == TmdbCollectionMediaType.TV) "series" else "movie",
                        supportsPagination = page?.nextSkip != null,
                    ),
                    items = items,
                    hasMore = page?.nextSkip != null,
                )
            }
        }.mapNotNull { it.await() }
    }
}

/** A TMDB source filtered by watch provider but saved without a region takes the page's region. */
internal fun CollectionSource.withStreamingRegion(region: String): CollectionSource {
    val sourceFilters = filters ?: return this
    if (!isTmdb || sourceFilters.withWatchProviders.isNullOrBlank() || !sourceFilters.watchRegion.isNullOrBlank()) return this
    return copy(filters = sourceFilters.copy(watchRegion = region))
}
