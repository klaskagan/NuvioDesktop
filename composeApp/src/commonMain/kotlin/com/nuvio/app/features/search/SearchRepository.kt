package com.nuvio.app.features.search

import co.touchlab.kermit.Logger
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonExtraProperty
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.firstEnabledManifestError
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.servers.ServerMediaKind
import com.nuvio.app.features.servers.serverMessage
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.catalog.CATALOG_PAGE_SIZE
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionMediaType
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.collection.TmdbCollectionSourceType
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.buildCatalogUrl
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.catalog.mergeCatalogItems
import com.nuvio.app.features.catalog.nextCatalogPaginationState
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.features.streams.PlaybackAvailability
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

internal fun <T> canReuseRequestState(
    forceRefresh: Boolean,
    requestKey: T,
    cachedRequestKey: T?,
): Boolean = !forceRefresh && requestKey == cachedRequestKey

internal fun resolveDiscoverCatalog(
    sources: List<DiscoverCatalogOption>,
    preferredCatalogKey: String?,
    currentCatalogKey: String?,
): DiscoverCatalogOption? =
    sources.firstOrNull { it.key == preferredCatalogKey }
        ?: sources.firstOrNull { it.key == currentCatalogKey }
        ?: sources.firstOrNull()

private data class DiscoverRequestKey(
    val sources: List<DiscoverCatalogOption>,
    val hideUnreleasedContent: Boolean,
    val hasPendingAddonManifests: Boolean,
)

object SearchRepository {
    private val log = Logger.withTag("SearchRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _discoverUiState = MutableStateFlow(DiscoverUiState())
    val discoverUiState: StateFlow<DiscoverUiState> = _discoverUiState.asStateFlow()

    private val _hideUnplayable = MutableStateFlow(true)
    /** Settings → Content Discovery → "Hide unplayable results". On by default. */
    val hideUnplayable: StateFlow<Boolean> = _hideUnplayable.asStateFlow()
    private var hideUnplayableLoaded = false

    fun ensurePreferencesLoaded() {
        if (hideUnplayableLoaded) return
        hideUnplayableLoaded = true
        _hideUnplayable.value = !SearchPreferencesStorage.loadShowUnplayable()
    }

    fun onProfileChanged() {
        hideUnplayableLoaded = false
        ensurePreferencesLoaded()
    }

    fun setHideUnplayable(hide: Boolean) {
        ensurePreferencesLoaded()
        if (_hideUnplayable.value == hide) return
        _hideUnplayable.value = hide
        SearchPreferencesStorage.saveShowUnplayable(!hide)
    }

    private var activeJob: Job? = null
    private val searchGeneration = SearchGeneration()
    private var activeDiscoverJob: Job? = null
    private var lastRequestKey: String? = null
    private var discoverSources: List<DiscoverCatalogOption> = emptyList()
    private var lastDiscoverRequestKey: DiscoverRequestKey? = null

    fun search(
        query: String,
        addons: List<ManagedAddon>,
        forceRefresh: Boolean = false,
    ) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            clear()
            return
        }

        ensurePreferencesLoaded()
        val hideUnplayable = _hideUnplayable.value
        val enabledAddons = addons.enabledAddons()
        val hasPendingAddonManifests = enabledAddons.hasPendingEnabledManifests()
        val addonManifestErrorMessage = enabledAddons.firstEnabledManifestError()
        val activeAddons = enabledAddons.filter { it.manifest != null }
        val serverSearches = ServerCatalog.searchTargets()
        if (activeAddons.isEmpty() && serverSearches.isEmpty()) {
            activeJob?.cancel()
            searchGeneration.next()
            lastRequestKey = null
            _uiState.value = SearchUiState(
                isLoading = hasPendingAddonManifests,
                emptyStateReason = when {
                    hasPendingAddonManifests -> null
                    addonManifestErrorMessage != null -> SearchEmptyStateReason.RequestFailed
                    else -> SearchEmptyStateReason.NoActiveAddons
                },
                errorMessage = addonManifestErrorMessage,
            )
            return
        }

        val requests = buildSearchRequests(
            addons = activeAddons,
            query = normalizedQuery,
        )
        if (requests.isEmpty() && serverSearches.isEmpty()) {
            activeJob?.cancel()
            searchGeneration.next()
            lastRequestKey = null
            _uiState.value = SearchUiState(
                isLoading = hasPendingAddonManifests,
                emptyStateReason = if (hasPendingAddonManifests) null else SearchEmptyStateReason.NoSearchCatalogs,
            )
            return
        }

        val requestKey = buildString {
            append(normalizedQuery.lowercase())
            append('|')
            append(HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent)
            append('|')
            append(hasPendingAddonManifests)
            append('|')
            append(hideUnplayable)
            append('|')
            append(
                requests.joinToString(separator = "|") { request ->
                    "${request.addon.manifestUrl}:${request.type}:${request.catalogId}"
                },
            )
            serverSearches.forEach { ref ->
                append("|server:${ref.connection.id}:${ref.kind.contentType}:")
                append(ref.connection.selectedLibraries(ref.kind).joinToString(",") { it.id })
            }
        }
        val loaders: List<suspend () -> HomeCatalogSection> =
            requests.map { request -> suspend { request.toSection(forceRefresh = forceRefresh) } } +
                serverSearches.map { ref ->
                    suspend {
                        ServerCatalog.searchSection(ref, normalizedQuery).also { section ->
                            require(section.items.isNotEmpty()) {
                                getString(Res.string.search_error_no_results_for_catalog, section.title)
                            }
                        }
                    }
                }
        if (canReuseRequestState(forceRefresh, requestKey, lastRequestKey)) return
        lastRequestKey = requestKey

        activeJob?.cancel()
        val generation = searchGeneration.next()
        _uiState.value = SearchUiState(isLoading = true)

        fun publish(state: SearchUiState) {
            if (searchGeneration.isCurrent(generation)) _uiState.value = state
        }

        activeJob = scope.launch {
            val timings = SearchTimings(normalizedQuery)
            val availability = PlaybackAvailability.current()
            val canPlay = { type: String, id: String -> availability.canPlay(type = type, videoId = id, parentMetaId = id) }
            // Start the TMDB→IMDb lookups the playability check will need as soon as items arrive,
            // so the final check mostly reads TmdbService's cache instead of waiting on the network.
            val warmLookups = mutableListOf<Job>()
            fun warmPlayability(items: List<MetaPreview>) {
                if (!hideUnplayable) return
                items.forEach { item ->
                    val tmdbId = searchResultTmdbLookupId(item, canPlay) ?: return@forEach
                    warmLookups += launch { lookupImdbForPlayability(tmdbId, item.type) }
                }
            }
            val resultChannel = Channel<IndexedSearchResult>(Channel.UNLIMITED)
            val jobs = loaders.mapIndexed { index, load ->
                launch {
                    runCatching { load() }
                        .fold(
                            onSuccess = { section ->
                                resultChannel.trySend(
                                    IndexedSearchResult(
                                        index = index,
                                        section = section,
                                    ),
                                )
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                resultChannel.trySend(
                                    IndexedSearchResult(
                                        index = index,
                                        error = error,
                                    ),
                                )
                            },
                        )
                }
            }
            val peopleJob = launch {
                val people = try {
                    filterSearchPeople(TmdbMetadataService.searchPeople(normalizedQuery), normalizedQuery)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.w(error) { "People search failed" }
                    emptyList()
                }
                timings.mark("people")
                resultChannel.trySend(IndexedSearchResult(index = PEOPLE_RESULT_INDEX, people = people))
                // The top matching person's titles join the Movies & TV grid. Shares the person page's cache.
                val featured = people.firstOrNull() ?: return@launch
                val filmography = try {
                    TmdbMetadataService.fetchPersonDetail(featured.tmdbId, preferCrewCredits = featured.isDirector)
                        ?.let { filterSearchFilmography(it, isDirector = featured.isDirector) }
                        .orEmpty()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.w(error) { "Filmography for search person failed" }
                    emptyList()
                }
                timings.mark("filmography")
                if (filmography.isNotEmpty()) {
                    resultChannel.trySend(IndexedSearchResult(index = FILMOGRAPHY_RESULT_INDEX, filmography = filmography))
                }
            }
            val franchiseJob = launch {
                val franchise = try {
                    searchFranchiseFilms(normalizedQuery)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.w(error) { "Franchise search failed" }
                    emptyList()
                }
                timings.mark("franchise")
                if (franchise.isNotEmpty()) {
                    resultChannel.trySend(IndexedSearchResult(index = FRANCHISE_RESULT_INDEX, filmography = franchise))
                }
            }
            val closeChannelJob = launch {
                (jobs + peopleJob + franchiseJob).joinAll()
                resultChannel.close()
            }
            // A slow addon must not hold every result back: after this, show what is ready and
            // append late providers' new titles at the end.
            val firstPublishJob = launch {
                delay(SEARCH_FIRST_PUBLISH_MS)
                resultChannel.trySend(IndexedSearchResult(index = FIRST_PUBLISH_RESULT_INDEX))
            }
            var shownResults: List<MetaPreview>? = null
            val results = arrayOfNulls<IndexedSearchResult>(loaders.size)
            var people = emptyList<SearchPerson>()
            var filmography = emptyList<MetaPreview>()
            var franchise = emptyList<MetaPreview>()

            suspend fun resolve(isFinished: Boolean): SearchUiState {
                val completedResults = results.filterNotNull()
                val sections = results.orderedSections()
                val exact = sections.flatMap { it.items }
                    .filter { searchRelevanceTier(it.name, normalizedQuery) == SearchRelevanceTier.Exact }
                    .distinctBy { it.type to it.id }
                val metadata = if (exact.size > 1 && exact.any { it.popularity == null }) {
                    kotlinx.coroutines.withTimeoutOrNull(750) {
                        exact.map { it.type == "series" }.distinct().flatMap { television ->
                            try {
                                TmdbMetadataService.searchDiscoveryPage(television, 1, emptyMap(), normalizedQuery).items
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.orEmpty()
                } else emptyList()
                return resolveSearchUiState(
                    query = normalizedQuery,
                    sections = sections.map { section ->
                        section.copy(items = section.items.map { item ->
                            if (item.popularity != null ||
                                searchRelevanceTier(item.name, normalizedQuery) != SearchRelevanceTier.Exact
                            ) item else {
                                val keys = searchDedupKeys(item)
                                val matches = metadata.filter { candidate ->
                                    searchDedupKeys(candidate).any(keys::contains)
                                }
                                val match = matches.singleOrNull { candidate ->
                                    item.id == candidate.id || candidate.id in item.alternateIds
                                } ?: matches.singleOrNull()
                                item.copy(popularity = match?.popularity, voteCount = item.voteCount ?: match?.voteCount)
                            }
                        })
                    },
                    people = people,
                    filmography = filmography,
                    franchise = franchise,
                    completedProviders = completedResults.size,
                    failedProviders = completedResults.count { it.error != null },
                    firstFailure = completedResults.firstNotNullOfOrNull { result ->
                        result.error?.let { it.serverMessage() ?: it.message }
                    },
                    isFinished = isFinished,
                    hasPendingAddonManifests = hasPendingAddonManifests,
                )
            }

            try {
                for (result in resultChannel) {
                    if (result.index == PEOPLE_RESULT_INDEX) {
                        people = result.people.orEmpty()
                    } else if (result.index == FILMOGRAPHY_RESULT_INDEX) {
                        filmography = result.filmography.orEmpty()
                        warmPlayability(filmography)
                    } else if (result.index == FRANCHISE_RESULT_INDEX) {
                        franchise = result.filmography.orEmpty()
                        warmPlayability(franchise)
                    } else if (result.index == FIRST_PUBLISH_RESULT_INDEX) {
                        val partial = resolve(isFinished = false)
                            .let { if (hideUnplayable) it.withPlayableResultsOnly(canPlay) else it }
                        if (partial.hasContent) {
                            publish(partial)
                            shownResults = partial.results
                            timings.mark("firstShown")
                        }
                    } else {
                        results[result.index] = result
                        result.section?.let { warmPlayability(it.items) }
                        if (results.all { it != null }) timings.mark("addons")
                    }
                }
            } finally {
                closeChannelJob.cancel()
                firstPublishJob.cancel()
                resultChannel.close()
            }

            // Published once everything finished; if an early grid is already on screen, its cards
            // keep their order and only new titles are appended, so nothing moves under the user.
            val finished = resolve(isFinished = true)
            timings.mark("merged")
            val filtered = if (hideUnplayable) finished.withPlayableResultsOnly(canPlay) else finished
            val published = shownResults?.let { shown ->
                filtered.copy(results = if (franchise.isEmpty()) publishLateSearchResults(normalizedQuery, shown, filtered.results)
                    else appendNewSearchResults(shown, filtered.results), emptyStateReason = null)
            } ?: filtered
            timings.mark("playability")
            publish(published)
            log.i { timings.summary(lookups = warmLookups.size, shown = published.results.size, hidden = finished.results.size - published.results.size) }
        }
    }

    /** Films of the TMDB collection the query names exactly ("james bond" → James Bond Collection). */
    private suspend fun searchFranchiseFilms(query: String): List<MetaPreview> {
        val collection = TmdbCollectionSourceResolver.searchCollections(query)
            .firstOrNull { result -> result.name?.let { isSearchFranchiseMatch(it, query) } == true }
            ?: return emptyList()
        val page = TmdbCollectionSourceResolver.resolve(
            CollectionSource(
                provider = "tmdb",
                tmdbSourceType = TmdbCollectionSourceType.COLLECTION.name,
                title = collection.name,
                tmdbId = collection.id,
                mediaType = TmdbCollectionMediaType.MOVIE.name,
            ),
        )
        return page.withUnreleasedFilter().items.filter { !it.poster.isNullOrBlank() }
    }

    internal suspend fun filterDiscoveryResults(items: List<MetaPreview>): List<MetaPreview> {
        val visible = com.nuvio.app.features.catalog.CatalogPage(items, items.size, null).withUnreleasedFilter().items
        if (!_hideUnplayable.value) return visible
        val availability = PlaybackAvailability.current()
        return SearchUiState(results = visible).withPlayableResultsOnly { type, id ->
            availability.canPlay(type = type, videoId = id, parentMetaId = id)
        }.results
    }

    private suspend fun SearchUiState.withPlayableResultsOnly(canPlay: (String, String) -> Boolean): SearchUiState {
        if (results.isEmpty()) return this
        val playable = filterPlayableSearchResults(
            results = results,
            canPlay = canPlay,
            resolveTmdbImdbId = ::lookupImdbForPlayability,
        )
        return withFilteredResults(playable)
    }

    /** Cached by TmdbService (misses for 60 s), and concurrent calls for one id share a request. */
    private suspend fun lookupImdbForPlayability(tmdbId: Int, type: String): String? =
        try {
            withTimeoutOrNull(PLAYABILITY_LOOKUP_TIMEOUT_MS) { TmdbService.tmdbToImdb(tmdbId = tmdbId, mediaType = type) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "IMDb lookup for search result tmdb:$tmdbId failed" }
            null
        }

    fun clear() {
        activeJob?.cancel()
        searchGeneration.next()
        lastRequestKey = null
        _uiState.value = SearchUiState()
    }

    fun reset() {
        activeJob?.cancel()
        searchGeneration.next()
        activeDiscoverJob?.cancel()
        lastRequestKey = null
        discoverSources = emptyList()
        lastDiscoverRequestKey = null
        _uiState.value = SearchUiState()
        _discoverUiState.value = DiscoverUiState()
    }

    fun refreshDiscover(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean = false,
    ) {
        val enabledAddons = addons.enabledAddons()
        val hasPendingAddonManifests = enabledAddons.hasPendingEnabledManifests()
        val addonManifestErrorMessage = enabledAddons.firstEnabledManifestError()
        val activeAddons = enabledAddons.filter { it.manifest != null }
        val serverLibraries = ServerCatalog.titleLibraries()
        val go3Sources = com.nuvio.app.features.go3.Go3Catalog.sources()
        if (activeAddons.isEmpty() && serverLibraries.isEmpty() && go3Sources.isEmpty()) {
            activeDiscoverJob?.cancel()
            discoverSources = emptyList()
            lastDiscoverRequestKey = null
            log.d { "Discover refresh aborted: no active addons" }
            _discoverUiState.value = DiscoverUiState(
                isLoading = hasPendingAddonManifests,
                emptyStateReason = when {
                    hasPendingAddonManifests -> null
                    addonManifestErrorMessage != null -> DiscoverEmptyStateReason.RequestFailed
                    else -> DiscoverEmptyStateReason.NoActiveAddons
                },
                errorMessage = addonManifestErrorMessage,
            )
            return
        }

        val sources = buildDiscoverSources(activeAddons) + go3Sources + serverLibraries.filter { it.library.kind != ServerMediaKind.MIXED }.map { ref ->
            DiscoverCatalogOption(
                key = ref.target.let { "server:${it.connectionId}:${it.libraryId}" },
                addonName = ServerRepository.sourceLabel(ref.connection),
                manifestUrl = "",
                type = ref.library.kind.contentType,
                catalogId = ref.library.id,
                catalogName = ref.title,
                supportsPagination = true,
                server = ref.target,
            )
        }
        val current = _discoverUiState.value
        val hideUnreleasedContent = HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent
        val requestKey = DiscoverRequestKey(
            sources = sources,
            hideUnreleasedContent = hideUnreleasedContent,
            hasPendingAddonManifests = hasPendingAddonManifests,
        )
        if (canReuseRequestState(forceRefresh, requestKey, lastDiscoverRequestKey)) {
            log.d {
                "Reusing discover state type=${current.selectedType} catalog=${current.selectedCatalogKey} " +
                    "genre=${current.selectedGenre ?: "<all>"} items=${current.items.size} nextSkip=${current.nextSkip}"
            }
            return
        }

        discoverSources = sources
        lastDiscoverRequestKey = requestKey
        if (sources.isEmpty()) {
            activeDiscoverJob?.cancel()
            log.d { "Discover refresh found no compatible discover catalogs" }
            _discoverUiState.value = DiscoverUiState(
                isLoading = hasPendingAddonManifests,
                emptyStateReason = if (hasPendingAddonManifests) null else DiscoverEmptyStateReason.NoDiscoverCatalogs,
            )
            return
        }

        val preferredCatalogKey = DiscoverSelectionStorage.loadCatalogKey()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val selectedCatalog = requireNotNull(
            resolveDiscoverCatalog(
                sources = sources,
                preferredCatalogKey = preferredCatalogKey,
                currentCatalogKey = current.selectedCatalogKey,
            ),
        )
        val typeOptions = sources.map { it.type }.distinct()
        val selectedType = selectedCatalog.type
        val catalogOptions = sources.filter { it.type == selectedType }
        val selectedGenre = selectedCatalog.resolveGenreSelection(current.selectedGenre)

        _discoverUiState.value = DiscoverUiState(
            typeOptions = typeOptions,
            selectedType = selectedType,
            catalogOptions = catalogOptions,
            selectedCatalogKey = selectedCatalog.key,
            selectedGenre = selectedGenre,
            items = emptyList(),
            isLoading = false,
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )

        log.d {
            "Discover refresh prepared type=$selectedType catalog=${selectedCatalog.key} " +
                "genre=${selectedGenre ?: "<all>"} sources=${sources.size}"
        }

        loadDiscoverFeed(
            reset = true,
            forceRefresh = forceRefresh,
        )
    }

    fun selectDiscoverType(type: String) {
        val current = _discoverUiState.value
        if (current.selectedType == type) return

        val catalogOptions = discoverSources.filter { it.type == type }
        val selectedCatalog = catalogOptions.firstOrNull() ?: run {
            _discoverUiState.value = current.copy(
                selectedType = type,
                catalogOptions = emptyList(),
                selectedCatalogKey = null,
                selectedGenre = null,
                items = emptyList(),
                isLoading = false,
                nextSkip = null,
                emptyStateReason = DiscoverEmptyStateReason.NoDiscoverCatalogs,
                errorMessage = null,
            )
            return
        }

        _discoverUiState.value = current.copy(
            selectedType = type,
            catalogOptions = catalogOptions,
            selectedCatalogKey = selectedCatalog.key,
            selectedGenre = selectedCatalog.resolveGenreSelection(null),
            items = emptyList(),
            isLoading = false,
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )
        DiscoverSelectionStorage.saveCatalogKey(selectedCatalog.key)
        loadDiscoverFeed(
            reset = true,
            forceRefresh = false,
        )
    }

    fun selectDiscoverCatalog(catalogKey: String) {
        val current = _discoverUiState.value
        if (current.selectedCatalogKey == catalogKey) return

        val selectedCatalog = current.catalogOptions.firstOrNull { it.key == catalogKey } ?: return
        _discoverUiState.value = current.copy(
            selectedCatalogKey = selectedCatalog.key,
            selectedGenre = selectedCatalog.resolveGenreSelection(null),
            items = emptyList(),
            isLoading = false,
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )
        DiscoverSelectionStorage.saveCatalogKey(selectedCatalog.key)
        loadDiscoverFeed(
            reset = true,
            forceRefresh = false,
        )
    }

    fun selectDiscoverGenre(genre: String?) {
        val current = _discoverUiState.value
        val selectedCatalog = current.selectedCatalog ?: return
        val normalizedGenre = if(selectedCatalog.key.startsWith("go3:")) genre?.takeIf {
            it in com.nuvio.app.features.go3.Go3Catalog.filters(selectedCatalog.catalogId)
        } else selectedCatalog.resolveGenreSelection(genre)
        if (current.selectedGenre == normalizedGenre) return
        if (selectedCatalog.key.startsWith("go3:")) {
            val cached = com.nuvio.app.features.go3.Go3Catalog.cachedPage(selectedCatalog.catalogId, normalizedGenre)
            if (cached != null) {
                activeDiscoverJob?.cancel()
                _discoverUiState.value=current.copy(selectedGenre=normalizedGenre,items=cached,isLoading=false,nextSkip=null,
                    emptyStateReason=if(cached.isEmpty()) DiscoverEmptyStateReason.NoResults else null,errorMessage=null)
                return
            }
        }

        _discoverUiState.value = current.copy(
            selectedGenre = normalizedGenre,
            items = emptyList(),
            isLoading = false,
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )
        loadDiscoverFeed(
            reset = true,
            forceRefresh = false,
        )
    }

    fun refreshGo3IfStale() {
        val state=_discoverUiState.value
        val selected=state.selectedCatalog ?: return
        if(selected.key.startsWith("go3:") && !state.isLoading && com.nuvio.app.features.go3.Go3Catalog.isStale(selected.catalogId))
            loadDiscoverFeed(reset=true,forceRefresh=true)
    }

    fun loadMoreDiscover() {
        val current = _discoverUiState.value
        if (current.isLoading || current.nextSkip == null) return
        loadDiscoverFeed(
            reset = false,
            forceRefresh = false,
        )
    }

    private fun buildSearchRequests(
        addons: List<ManagedAddon>,
        query: String,
    ): List<SearchCatalogRequest> =
        addons.mapNotNull { addon ->
            val manifest = addon.manifest ?: return@mapNotNull null
            addon to manifest
        }.flatMap { (addon, manifest) ->
            manifest.catalogs
                .filter { catalog -> catalog.supportsSearch() }
                .map { catalog ->
                    SearchCatalogRequest(
                        addon = addon,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        type = catalog.type,
                        query = query,
                        supportsPagination = catalog.supportsPagination(),
                    )
                }
        }

    private fun buildDiscoverSources(addons: List<ManagedAddon>): List<DiscoverCatalogOption> =
        addons.mapNotNull { addon ->
            val manifest = addon.manifest ?: return@mapNotNull null
            addon to manifest
        }.flatMap { (addon, manifest) ->
            manifest.catalogs
                .filter { catalog -> catalog.supportsDiscover() }
                .map { catalog ->
                    val genreExtra = catalog.genreExtra()
                    DiscoverCatalogOption(
                        key = "${manifest.id}:${catalog.type}:${catalog.id}",
                        addonName = addon.displayTitle,
                        manifestUrl = addon.manifestUrl,
                        type = catalog.type,
                        catalogId = catalog.id,
                        catalogName = catalog.name,
                        genreOptions = genreExtra?.options.orEmpty(),
                        genreRequired = genreExtra?.isRequired == true,
                        supportsPagination = catalog.supportsPagination(),
                    )
                }
        }

    private suspend fun SearchCatalogRequest.toSection(forceRefresh: Boolean): HomeCatalogSection {
        val manifest = requireNotNull(addon.manifest)
        val page = fetchCatalogPage(
            manifestUrl = manifest.transportUrl,
            type = type,
            catalogId = catalogId,
            search = query,
            forceRefresh = forceRefresh,
        ).withUnreleasedFilter()
        val posterPattern = com.nuvio.app.core.poster.CustomPosterUrlRepository.let {
            it.ensureLoaded()
            it.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.SEARCH)
        }
        val items = page.items.withCustomPosterUrls(posterPattern)
        require(items.isNotEmpty()) {
            getString(Res.string.search_error_no_results_for_catalog, catalogName)
        }

        return HomeCatalogSection(
            key = "${manifest.id}:search:$type:$catalogId:${query.lowercase()}",
            title = getString(Res.string.discover_catalog_context, catalogName, type.displayLabel()),
            subtitle = addon.displayTitle,
            addonName = addon.displayTitle,
            target = CatalogTarget.Addon(
                manifestUrl = manifest.transportUrl,
                contentType = type,
                catalogId = catalogId,
                supportsPagination = supportsPagination,
            ),
            items = items,
            availableItemCount = page.rawItemCount,
            hasMore = supportsPagination && page.nextSkip != null,
        )
    }

    private fun loadDiscoverFeed(
        reset: Boolean,
        forceRefresh: Boolean,
    ) {
        activeDiscoverJob?.cancel()
        val current = _discoverUiState.value
        val selectedCatalog = current.selectedCatalog ?: return
        val requestedSkip = if (reset) 0 else current.nextSkip ?: return
        val requestUrl = if (selectedCatalog.key.startsWith("go3:") || selectedCatalog.server != null) {
            selectedCatalog.key
        } else {
            buildCatalogUrl(
                manifestUrl = selectedCatalog.manifestUrl,
                type = selectedCatalog.type,
                catalogId = selectedCatalog.catalogId,
                genre = current.selectedGenre,
                search = null,
                skip = requestedSkip.takeIf { it > 0 },
            )
        }

        log.d {
            "Discover request reset=$reset addon=${selectedCatalog.addonName} type=${selectedCatalog.type} " +
                "catalogId=${selectedCatalog.catalogId} catalogKey=${selectedCatalog.key} " +
                "genre=${current.selectedGenre ?: "<all>"} skip=$requestedSkip url=$requestUrl"
        }

        _discoverUiState.value = current.copy(
            isLoading = true,
            items = if (reset && !selectedCatalog.key.startsWith("go3:")) emptyList() else current.items,
            nextSkip = if (reset) null else current.nextSkip,
            consecutiveDuplicatePages = if (reset) 0 else current.consecutiveDuplicatePages,
            emptyStateReason = null,
            errorMessage = null,
        )

        activeDiscoverJob = scope.launch {
            runCatching {
                val server = selectedCatalog.server
                if (selectedCatalog.key.startsWith("go3:")) {
                    val items = com.nuvio.app.features.go3.Go3Catalog.page(selectedCatalog.catalogId, current.selectedGenre, forceRefresh)
                    CatalogPage(items = items, rawItemCount = items.size, nextSkip = null)
                } else if (server != null) {
                    ServerCatalog.page(server, skip = requestedSkip)
                } else {
                    fetchCatalogPage(
                        manifestUrl = selectedCatalog.manifestUrl,
                        type = selectedCatalog.type,
                        catalogId = selectedCatalog.catalogId,
                        genre = current.selectedGenre,
                        skip = requestedSkip.takeIf { it > 0 },
                        forceRefresh = forceRefresh,
                    )
                }.withUnreleasedFilter()
            }.fold(
                onSuccess = { page ->
                    val latest = _discoverUiState.value
                    if (latest.selectedCatalogKey != selectedCatalog.key || latest.selectedGenre != current.selectedGenre) {
                        return@fold
                    }
                    val effectiveGenre = if(selectedCatalog.key.startsWith("go3:")) latest.selectedGenre?.takeIf {
                        it in com.nuvio.app.features.go3.Go3Catalog.filters(selectedCatalog.catalogId)
                    } else latest.selectedGenre
                    val visiblePage = if(selectedCatalog.key.startsWith("go3:")) page.copy(items =
                        com.nuvio.app.features.go3.Go3Catalog.cachedPage(selectedCatalog.catalogId,effectiveGenre) ?: page.items) else page
                    val mergedItems = if (reset) {
                        visiblePage.items
                    } else {
                        mergeCatalogItems(latest.items, visiblePage.items)
                    }.let { items ->
                        val pattern = com.nuvio.app.core.poster.CustomPosterUrlRepository.let { repo ->
                            repo.ensureLoaded()
                            repo.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.SEARCH)
                        }
                        items.withCustomPosterUrls(pattern)
                    }
                    val supportsPagination = !selectedCatalog.key.startsWith("go3:") && (selectedCatalog.supportsPagination || page.rawItemCount >= CATALOG_PAGE_SIZE)
                    val loadedNewItems = reset || mergedItems.size > latest.items.size
                    val paginationState = nextCatalogPaginationState(
                        supportsPagination = supportsPagination,
                        requestedSkip = requestedSkip,
                        page = page,
                        loadedNewItems = loadedNewItems,
                        consecutiveDuplicatePages = if (reset) 0 else latest.consecutiveDuplicatePages,
                    )
                    log.d {
                        "Discover response catalogKey=${selectedCatalog.key} returned=${page.items.size} " +
                            "merged=${mergedItems.size} rawItemCount=${page.rawItemCount} nextSkip=${page.nextSkip} " +
                            "sample=${page.items.previewNames()}"
                    }
                    _discoverUiState.value = latest.copy(
                        items = mergedItems,
                        selectedGenre = effectiveGenre,
                        isLoading = false,
                        nextSkip = paginationState.nextSkip,
                        consecutiveDuplicatePages = paginationState.consecutiveDuplicatePages,
                        emptyStateReason = if (mergedItems.isEmpty()) DiscoverEmptyStateReason.NoResults else null,
                        errorMessage = null,
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) {
                        log.d {
                            "Discover request cancelled catalogKey=${selectedCatalog.key} addon=${selectedCatalog.addonName} " +
                                "type=${selectedCatalog.type} catalogId=${selectedCatalog.catalogId} " +
                                "genre=${current.selectedGenre ?: "<all>"} skip=$requestedSkip"
                        }
                        return@fold
                    }

                    val latest = _discoverUiState.value
                    if (latest.selectedCatalogKey != selectedCatalog.key || latest.selectedGenre != current.selectedGenre) {
                        return@fold
                    }
                    log.e(error) {
                        "Discover request failed catalogKey=${selectedCatalog.key} addon=${selectedCatalog.addonName} " +
                            "type=${selectedCatalog.type} catalogId=${selectedCatalog.catalogId} " +
                            "genre=${current.selectedGenre ?: "<all>"} skip=$requestedSkip url=$requestUrl"
                    }
                    _discoverUiState.value = latest.copy(
                        items = if (reset) emptyList() else latest.items,
                        isLoading = false,
                        nextSkip = null,
                        emptyStateReason = DiscoverEmptyStateReason.RequestFailed,
                        errorMessage = error.message ?: getString(Res.string.discover_empty_load_failed_message),
                    )
                },
            )
        }
    }
}

/** Monotonic search id; a response may only publish while its id is still the current one. */
internal class SearchGeneration {
    @Volatile
    private var current: Long = 0L

    fun next(): Long = (current + 1).also { current = it }

    fun isCurrent(generation: Long): Boolean = generation == current
}

private const val PLAYABILITY_LOOKUP_TIMEOUT_MS = 2_000L

/** Milliseconds from search start to each phase, logged once per search to tune response time. */
private class SearchTimings(private val query: String) {
    private val start = kotlin.time.TimeSource.Monotonic.markNow()
    private val lock = SynchronizedObject()
    private val marks = linkedMapOf<String, Long>()

    // Marked from the people job and the result collector, which run on different threads.
    fun mark(phase: String) = synchronized(lock) {
        if (phase !in marks) marks[phase] = start.elapsedNow().inWholeMilliseconds
    }

    fun summary(lookups: Int, shown: Int, hidden: Int): String = synchronized(lock) {
        "Search timings \"$query\": " + marks.entries.joinToString { "${it.key}=${it.value}ms" } +
            ", tmdbLookups=$lookups, shown=$shown, hiddenUnplayable=$hidden"
    }
}
private const val PEOPLE_RESULT_INDEX = -1
private const val FILMOGRAPHY_RESULT_INDEX = -2
private const val FRANCHISE_RESULT_INDEX = -3
private const val FIRST_PUBLISH_RESULT_INDEX = -4
private const val SEARCH_FIRST_PUBLISH_MS = 2_000L

private data class IndexedSearchResult(
    val index: Int,
    val section: HomeCatalogSection? = null,
    val error: Throwable? = null,
    val people: List<SearchPerson>? = null,
    val filmography: List<MetaPreview>? = null,
)

private fun Array<IndexedSearchResult?>.orderedSections(): List<HomeCatalogSection> =
    mapNotNull { result -> result?.section }

private fun CatalogPage.withUnreleasedFilter(): CatalogPage {
    if (!HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) return this
    val filteredItems = items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
    return if (filteredItems.size == items.size) this else copy(items = filteredItems)
}

private data class SearchCatalogRequest(
    val addon: ManagedAddon,
    val catalogId: String,
    val catalogName: String,
    val type: String,
    val query: String,
    val supportsPagination: Boolean,
)

private fun AddonCatalog.supportsSearch(): Boolean =
    extra.any { property -> property.name == "search" } &&
        extra.none { property -> property.isRequired && property.name != "search" }

private fun AddonCatalog.supportsDiscover(): Boolean {
    if (extra.any { property -> property.name == "search" && property.isRequired }) {
        return false
    }

    return extra.none { property ->
        when (property.name) {
            "genre" -> property.isRequired && property.options.isEmpty()
            "skip" -> false
            "search" -> false
            else -> property.isRequired
        }
    }
}

private fun AddonCatalog.genreExtra(): AddonExtraProperty? =
    extra.firstOrNull { property -> property.name == "genre" }

private fun DiscoverCatalogOption.resolveGenreSelection(requestedGenre: String?): String? =
    when {
        genreOptions.isEmpty() -> null
        requestedGenre != null && genreOptions.contains(requestedGenre) -> requestedGenre
        genreRequired -> genreOptions.firstOrNull()
        else -> null
    }

private fun List<MetaPreview>.previewNames(limit: Int = 5): String {
    if (isEmpty()) return "[]"
    return take(limit).joinToString(prefix = "[", postfix = if (size > limit) ", ...]" else "]") { item ->
        item.name
    }
}

private fun String.displayLabel(): String =
    localizedMediaTypeLabel(this)

private fun String.typeSortKey(): String =
    when (lowercase()) {
        "movie" -> "0_movie"
        "series" -> "1_series"
        "anime" -> "2_anime"
        else -> "9_$this"
    }
