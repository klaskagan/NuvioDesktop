package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.seasonal_spotlight_label
import org.jetbrains.compose.resources.getString

/** A bounded in-memory cache is shared by Home and Explore, without blocking other home rows. */
object SeasonalSpotlightRepository {
    private data class Key(val theme: String, val language: String, val apiKey: String, val page: Int)
    private val cache = SeasonalPageCache<Key>(load = ::loadPage)

    suspend fun section(theme: SeasonalTheme): HomeCatalogSection {
        val result = page(theme.id)
        return HomeCatalogSection(
            key = "seasonal_${theme.id}",
            title = getString(theme.title),
            subtitle = getString(theme.description),
            addonName = getString(Res.string.seasonal_spotlight_label),
            target = CatalogTarget.Seasonal(theme.id),
            items = result.items,
            hasMore = result.nextSkip != null,
        )
    }

    suspend fun page(themeId: String, page: Int = 1): CatalogPage {
        require(page > 0)
        val settings = TmdbSettingsRepository.snapshot()
        val key = Key(themeId, settings.language, TmdbSettingsRepository.effectiveApiKey(), page)
        return cache.get(key)
    }

    private suspend fun loadPage(key: Key): CatalogPage {
        val theme = SeasonalTheme.fromId(key.theme)
        val today = CurrentDateProvider.todayIsoDate()
        val page = key.page
        val keywordIds = theme.keywords.mapNotNull { keyword ->
            TmdbCollectionSourceResolver.searchKeywords(keyword).entries
                .firstOrNull { it.value.equals(keyword, ignoreCase = true) }?.key
        }
        // Never turn a failed keyword lookup into a generic popular-movies collection.
        check(theme.keywords.isEmpty() || keywordIds.isNotEmpty()) { "Seasonal keywords unavailable" }
        val discovered = TmdbCollectionSourceResolver.resolve(
            source = CollectionSource(
                provider = "tmdb",
                tmdbSourceType = "DISCOVER",
                mediaType = "movie",
                sortBy = "popularity.desc",
                filters = theme.filters(keywordIds, today),
            ),
            page = page,
        )
        val seeds = if (page == 1) TmdbCollectionSourceResolver.resolveMovieSeeds(theme.seedMovieIds) else emptyList()
        val result = discovered.copy(
            items = (seeds + discovered.items).distinctBy(MetaPreview::stableKey)
                .filterReleasedItems(today),
        )
        val enriched = coroutineScope {
            result.items.map { movie ->
                async {
                    try {
                        val artwork = TmdbMetadataService.fetchLandscapeArtwork(movie.id, movie.type, key.language)
                        movie.copy(banner = artwork?.backdrop ?: movie.banner, logo = artwork?.logo ?: movie.logo)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        movie // The card will use its real backdrop and a readable title fallback.
                    }
                }
            }.awaitAll()
        }
        return result.copy(items = enriched)
    }
}

/** Failed refreshes keep a successful same-theme page; cancellation always propagates. */
internal class SeasonalPageCache<K>(
    private val now: () -> Long = WatchProgressClock::nowEpochMs,
    private val ttlMs: Long = 6 * 60 * 60 * 1000L,
    private val capacity: Int = 24,
    private val load: suspend (K) -> CatalogPage,
) {
    private data class Cached(val page: CatalogPage, val fetchedAt: Long)
    private val mutex = Mutex()
    private val entries = linkedMapOf<K, Cached>()

    suspend fun get(key: K): CatalogPage = mutex.withLock {
        val previous = entries[key]
        if (previous != null && now() - previous.fetchedAt in 0L until ttlMs) return@withLock previous.page
        try {
            val result = load(key)
            if (result.items.isNotEmpty()) {
                entries[key] = Cached(result, now())
                while (entries.size > capacity) entries.remove(entries.keys.first())
            }
            result.takeIf { it.items.isNotEmpty() } ?: previous?.page ?: result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            previous?.page ?: throw error
        }
    }
}
