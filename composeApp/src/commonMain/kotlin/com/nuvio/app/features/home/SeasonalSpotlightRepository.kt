package com.nuvio.app.features.home

import com.nuvio.app.core.auth.userId
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.collection.seasonalIdentity
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.seasonal_spotlight_label
import org.jetbrains.compose.resources.getString

/** A bounded in-memory cache is shared by Home and Explore, without blocking other home rows. */
object SeasonalSpotlightRepository {
    private data class Key(val theme: String, val language: String, val apiKey: String, val page: Int)
    private val cache = SeasonalPageCache<Key>(load = ::loadPage)

    private val sessionMutex = Mutex()
    private val sourceSlots = kotlinx.coroutines.sync.Semaphore(4)
    private val sessionSeed = kotlin.random.Random.nextLong()
    private val sessionOrders = linkedMapOf<String, SeasonalSessionOrder>()
    private data class SourceKey(val profile: Int, val account: String?, val addons: Int, val source: CollectionSource, val language: String, val apiKey: String)
    private val sourceCache = SeasonalPageCache<SourceKey>(capacity = 128, load = { key ->
        com.nuvio.app.features.collection.resolveSeasonalSource(key.source)
    })

    fun sections(theme: SeasonalTheme, folder: com.nuvio.app.features.collection.CollectionFolder): kotlinx.coroutines.flow.Flow<HomeCatalogSection> = kotlinx.coroutines.flow.channelFlow {
        val profile = com.nuvio.app.features.profiles.ProfileRepository.activeProfileId
        val settings = TmdbSettingsRepository.snapshot()
        val account = com.nuvio.app.core.auth.AuthRepository.state.value.userId
        val addons = com.nuvio.app.features.addons.AddonRepository.uiState.value.addons.hashCode()
        val identity = "$account:$profile:${theme.id}:${folder.hashCode()}:${settings.language}:${settings.apiKey}"
        val order = sessionMutex.withLock { synchronizedOrder(identity) }
        val mutex = Mutex()
        fun section(items: List<MetaPreview>) = HomeCatalogSection(
            key = "seasonal_${theme.id}", title = folder.title, subtitle = getSeasonDescription(theme),
            addonName = "", target = CatalogTarget.Seasonal(theme.id), items = items, hasMore = true)
        mutex.withLock { send(section(order.items)) }
        val jobs = folder.resolvedSources.map { source ->
            launch {
                try {
                    val page = sourceSlots.withPermit {
                        sourceCache.get(SourceKey(profile, account, addons, source, settings.language, TmdbSettingsRepository.effectiveApiKey()))
                    }
                    val sample = page.items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
                        .shuffled(kotlin.random.Random(sessionSeed xor source.hashCode().toLong())).take(8)
                    mutex.withLock { send(section(order.append(sample))) }
                } catch (error: CancellationException) { throw error } catch (_: Exception) {
                    // A failed source does not erase the successful same-session shelf.
                }
            }
        }
        jobs.forEach { it.join() }
        mutex.withLock { send(section(order.items).copy(hasMore = false)) }
    }

    private fun synchronizedOrder(identity: String): SeasonalSessionOrder = sessionOrders.getOrPut(identity) {
        if (sessionOrders.size >= 24) sessionOrders.remove(sessionOrders.keys.first())
        SeasonalSessionOrder(sessionSeed)
    }

    private fun getSeasonDescription(theme: SeasonalTheme): String = kotlinx.coroutines.runBlocking { getString(theme.description) }

    suspend fun section(theme: SeasonalTheme): HomeCatalogSection {
        val folder = com.nuvio.app.features.collection.SeasonalCollection.activeFolder(
            com.nuvio.app.features.collection.CollectionRepository.getCollection(com.nuvio.app.features.collection.SeasonalCollection.ID), theme)
        return HomeCatalogSection("seasonal_${theme.id}", folder?.title ?: getString(theme.title), getString(theme.description), "",
            CatalogTarget.Seasonal(theme.id), page(theme.id).items, hasMore = true)
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

    private val keyLocks = mutableMapOf<K, Mutex>()

    suspend fun get(key: K): CatalogPage {
        val guard = mutex.withLock { keyLocks.getOrPut(key) { Mutex() } }
        return guard.withLock {
            val previous = mutex.withLock { entries[key] }
            if (previous != null && now() - previous.fetchedAt in 0L until ttlMs) return@withLock previous.page
            try {
                val result = load(key)
                if (result.items.isNotEmpty()) mutex.withLock {
                    entries[key] = Cached(result, now())
                    while (entries.size > capacity) entries.remove(entries.keys.first())
                    if (keyLocks.size > capacity * 2) keyLocks.keys.filter { it !in entries && keyLocks[it]?.isLocked == false }
                        .take(keyLocks.size - capacity).forEach { keyLocks.remove(it) }
                }
                result.takeIf { it.items.isNotEmpty() } ?: previous?.page ?: result
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { previous?.page ?: throw error }
        }
    }
}

/** Append-only within a launch: late sources and refreshes cannot move already visible cards. */
internal class SeasonalSessionOrder(private val seed: Long) {
    var items: List<MetaPreview> = emptyList()
        private set
    fun append(incoming: List<MetaPreview>): List<MetaPreview> {
        val seen = items.mapTo(mutableSetOf()) { it.seasonalIdentity() }
        items = items + incoming.distinctBy { it.seasonalIdentity() }.filter { it.seasonalIdentity() !in seen }
            .shuffled(kotlin.random.Random(seed))
        return items
    }
}
