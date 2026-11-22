package com.nuvio.app.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberUpdatedState
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import nuvio.composeapp.generated.resources.search_clear_history
import nuvio.composeapp.generated.resources.search_clear_history_message
import nuvio.composeapp.generated.resources.search_cancel
import nuvio.composeapp.generated.resources.search_clear_all
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.LocalPosterCardStyleOverride
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.firstEnabledManifestError
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.buildAddonCatalogRefreshSignature
import com.nuvio.app.features.home.components.PosterGridSkeletonRow
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.core.ui.posterGridColumnCountForCatalogWidth
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import com.nuvio.app.isDesktop
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.home_empty_no_sources_title
import nuvio.composeapp.generated.resources.search_empty_no_sources_message
import nuvio.composeapp.generated.resources.compose_search_clear
import nuvio.composeapp.generated.resources.compose_search_discover_title
import nuvio.composeapp.generated.resources.compose_search_empty_failed_message
import nuvio.composeapp.generated.resources.compose_search_empty_failed_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_title
import nuvio.composeapp.generated.resources.compose_search_placeholder
import nuvio.composeapp.generated.resources.compose_search_recent_searches
import nuvio.composeapp.generated.resources.compose_search_remove_recent_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    topChromePadding: Dp? = null,
    listState: LazyListState = rememberLazyListState(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onPersonClick: ((SearchPerson) -> Unit)? = null,
    searchFocusRequestCount: Int = 0,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onStreamingCountrySettings: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isRecentSearchFocused by remember { mutableStateOf(false) }

    ScreenActivityEffect(listState) { screenActive ->
        if (!screenActive) {
            isSearchFocused = false
            isRecentSearchFocused = false
            focusRequester.freeFocus()
            listState.stopScroll()
        }
    }

    ScreenActivityEffect(searchFocusRequestCount) { screenActive ->
        if (screenActive && searchFocusRequestCount > 0) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
        SearchHistoryRepository.ensureLoaded()
    }

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val uiState by SearchRepository.uiState.collectAsStateWithLifecycle()
    val discoverUiState by SearchRepository.discoverUiState.collectAsStateWithLifecycle()
    val homeCatalogSettingsUiState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val recentSearches by SearchHistoryRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val discoverySession = LocalSearchDiscoverySession.current ?: remember { SearchDiscoverySession() }
    val discovery = discoverySession.selection
    fun updateQuery(value: String) {
        if (query != value) discoverySession.select(null)
        query = value
    }
    var showClearHistory by remember { mutableStateOf(false) }
    val dismissClearHistory: () -> Unit = {
        showClearHistory = false
        focusRequester.requestFocus()
    }
    val screenActive = LocalScreenActive.current
    val focusManager = LocalFocusManager.current
    val escapeHandler by rememberUpdatedState<() -> Boolean>({
        when {
            showClearHistory -> {
                dismissClearHistory()
                true
            }
            !isSearchFocused -> false
            query.isNotEmpty() -> {
                updateQuery("")
                true
            }
            else -> {
                focusRequester.freeFocus()
                focusManager.clearFocus()
                true
            }
        }
    })
    if (isDesktop && screenActive) {
        DisposableEffect(Unit) {
            val handler: () -> Boolean = { escapeHandler() }
            AppKeyboardShortcuts.searchEscape = handler
            onDispose {
                if (AppKeyboardShortcuts.searchEscape === handler) AppKeyboardShortcuts.searchEscape = null
            }
        }
    }
    if (showClearHistory && screenActive) {
        DialogSurface(
            onDismissRequest = dismissClearHistory,
            title = stringResource(Res.string.search_clear_history),
            message = stringResource(Res.string.search_clear_history_message),
        ) {
            DialogButtons {
                DialogButton(stringResource(Res.string.search_cancel), dismissClearHistory)
                DialogButton(
                    text = stringResource(Res.string.search_clear_all),
                    style = DialogButtonStyle.Destructive,
                    onClick = {
                        SearchHistoryRepository.clearHistory()
                        dismissClearHistory()
                    },
                )
            }
        }
    }


    var lastRequestedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var searchLandscape by remember { mutableStateOf(SearchPreferencesStorage.loadLandscapeEnabled()) }
    var observedOfflineState by remember { mutableStateOf(false) }
    val discoverInFocus by remember(query, listState) {
        derivedStateOf {
            query.isBlank() && listState.firstVisibleItemIndex > 0
        }
    }

    ScreenActivityEffect(scrollToTopRequests) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        scrollToTopRequests.collect {
            listState.animateScrollToItem(0)
        }
    }

    ScreenActivityEffect(discoverUiState.selectedCatalogKey) { screenActive ->
        if (!screenActive || discoverUiState.selectedCatalogKey?.startsWith("go3:") != true) return@ScreenActivityEffect
        SearchRepository.refreshGo3IfStale()
        while (true) {
            delay(120_000)
            SearchRepository.refreshGo3IfStale()
        }
    }

    val addonRefreshKey = remember(addonsUiState.addons) {
        buildAddonCatalogRefreshSignature(addonsUiState.addons)
    }
    val addonManifestsLoading = addonsUiState.addons.hasPendingEnabledManifests()

    ScreenActivityEffect(addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        SearchRepository.refreshDiscover(addonsUiState.addons)
    }

    val hideUnplayable by remember {
        SearchRepository.ensurePreferencesLoaded()
        SearchRepository.hideUnplayable
    }.collectAsStateWithLifecycle()

    val discoveryUi = rememberSearchDiscovery(
        selection = discovery,
        active = screenActive,
        hideUnplayable = hideUnplayable,
        hideUnreleased = homeCatalogSettingsUiState.hideUnreleasedContent,
    )

    ScreenActivityEffect(query, addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent, hideUnplayable) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            lastRequestedQuery = null
            SearchRepository.clear()
        } else {
            delay(350)
            lastRequestedQuery = normalizedQuery
            SearchRepository.search(
                query = normalizedQuery,
                addons = addonsUiState.addons,
            )
        }
    }


    ScreenActivityEffect(query, discovery, lastRequestedQuery, uiState.isLoading, uiState.hasContent) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return@ScreenActivityEffect
        if (discovery != null || lastRequestedQuery != normalizedQuery) return@ScreenActivityEffect
        if (uiState.isLoading || !uiState.hasContent) return@ScreenActivityEffect
        SearchHistoryRepository.recordSearch(normalizedQuery)
    }

    ScreenActivityEffect(networkStatusUiState.condition, query, addonRefreshKey) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@ScreenActivityEffect
                observedOfflineState = false

                val normalizedQuery = query.trim()
                if (normalizedQuery.isBlank()) {
                    SearchRepository.refreshDiscover(
                        addons = addonsUiState.addons,
                        forceRefresh = true,
                    )
                } else {
                    SearchRepository.search(
                        query = normalizedQuery,
                        addons = addonsUiState.addons,
                        forceRefresh = true,
                    )
                }
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val posterCardStyle = rememberPosterCardStyleUiState()
        val searchStyle = remember(posterCardStyle, searchLandscape) {
            posterCardStyle.copy(
                catalogLandscapeModeEnabled = searchLandscape,
                cornerRadiusDp = if (searchLandscape) posterCardStyle.cornerRadiusDp.coerceAtMost(8) else posterCardStyle.cornerRadiusDp,
            )
        }
        val searchColumns = remember(maxWidth, posterCardStyle.widthDp, searchLandscape, isDesktop) {
            searchGridColumns(maxWidth, posterCardStyle.widthDp, searchLandscape)
        }
        val discoverColumns = remember(maxWidth, maxHeight, posterCardStyle.widthDp, isDesktop) {
            if (isDesktop) {
                posterGridColumnCountForCatalogWidth(
                    screenWidth = maxWidth,
                    basePosterWidthDp = posterCardStyle.widthDp,
                )
            } else {
                posterGridColumnCountForWidth(maxWidth)
            }
        }
        val homeSectionPadding = remember(maxWidth) {
            homeSectionHorizontalPaddingForWidth(maxWidth.value)
        }
        val headerTitle = when {
            query.isNotBlank() -> stringResource(Res.string.compose_nav_search)
            discoverInFocus -> stringResource(Res.string.compose_search_discover_title)
            else -> stringResource(Res.string.compose_nav_search)
        }

        CompositionLocalProvider(LocalPosterCardStyleOverride provides searchStyle.takeIf { query.isNotBlank() }) {
        val visibleSearchItems = if (discovery == null) uiState.results else discoveryUi.items
        PreserveSearchResultScrollAnchor(listState, query, searchColumns, visibleSearchItems)
        com.nuvio.app.features.home.components.LoadMoreOnScrollEffect(
            state = listState,
            canLoadMore = if (discovery != null) discoveryUi.hasMore && !discoveryUi.failed else discoverUiState.canLoadMore,
            isLoading = if (discovery != null) discoveryUi.loading else discoverUiState.isLoading,
            enabled = query.isBlank() || discovery != null,
            onLoadMore = if (discovery != null) discoveryUi.load else SearchRepository::loadMoreDiscover,
        )
        NuvioScreen(
            horizontalPadding = 0.dp,
            topPadding = if (topChromePadding != null) 0.dp else null,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        stickyHeader {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nuvioConsumePointerEvents(),
                )
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NuvioScreenHeader(
                        title = headerTitle,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        topPadding = topChromePadding,
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NuvioInputField(
                            value = query,
                            onValueChange = ::updateQuery,
                            placeholder = stringResource(Res.string.compose_search_placeholder),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            shape = RoundedCornerShape(NuvioTokens.Radius.xl),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            trailingContent = if (query.isNotBlank()) {
                                {
                                    IconButton(onClick = { updateQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(Res.string.compose_search_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                        if (query.isNotBlank()) {
                            SearchPosterOrientationButton(
                                landscape = searchLandscape,
                                onToggle = {
                                    searchLandscape = !searchLandscape
                                    SearchPreferencesStorage.saveLandscapeEnabled(searchLandscape)
                                },
                            )
                        }
                    }
                    val suggestions = searchDiscoverySuggestions(query)
                    if (suggestions.isNotEmpty()) {
                        SearchDiscoveryChips(
                            suggestions = suggestions,
                            selected = discovery,
                            country = discoveryUi.country,
                            onSelect = {
                                discoverySession.select(it)
                                listState.requestScrollToItem(0)
                            },
                            onStreamingCountrySettings = onStreamingCountrySettings,
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        if (discovery != null) {
            searchDiscoveryContent(
                discoveryUi, searchColumns, searchLandscape, watchedUiState.watchedKeys,
                fullyWatchedSeriesKeys, onPosterClick, onPosterLongClick,
            )
        } else if (query.isBlank()) {
            if ((isSearchFocused || isRecentSearchFocused || showClearHistory) && recentSearches.isNotEmpty()) {
                item(key = "recent_searches") {
                    SearchRecentSection(
                        recentSearches = recentSearches,
                        onSearchPress = { recentQuery ->
                            updateQuery(recentQuery)
                            focusRequester.requestFocus()
                        },
                        onRemoveSearch = { recentQuery ->
                            SearchHistoryRepository.removeSearch(recentQuery)
                            focusRequester.requestFocus()
                        },
                        onClearAll = { showClearHistory = true },
                        modifier = Modifier.onFocusChanged { isRecentSearchFocused = it.hasFocus }.focusGroup(),
                    )
                }
            }
                discoverContent(
                    state = discoverUiState,
                    isSourceLoading = addonManifestsLoading,
                    columns = discoverColumns,
                    networkCondition = networkStatusUiState.condition,
                    onTypeSelected = SearchRepository::selectDiscoverType,
                    onCatalogSelected = SearchRepository::selectDiscoverCatalog,
                    onGenreSelected = SearchRepository::selectDiscoverGenre,
                    onRetry = {
                        NetworkStatusRepository.requestRefresh(force = true)
                        if (addonsUiState.addons.firstEnabledManifestError() != null) {
                            AddonRepository.refreshAll()
                        } else {
                            SearchRepository.refreshDiscover(
                                addons = addonsUiState.addons,
                                forceRefresh = true,
                            )
                        }
                    },
                    watchedKeys = watchedUiState.watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            } else {
                val normalizedQuery = query.trim()
                val isWaitingForSearch = normalizedQuery.isNotBlank() && lastRequestedQuery != normalizedQuery
                when {
                    isWaitingForSearch ||
                        ((uiState.isLoading || addonManifestsLoading) && !uiState.hasContent) -> {
                        items(2) {
                            PosterGridSkeletonRow(
                                columns = searchColumns,
                                aspectRatio = if (searchLandscape) 16f / 9f else 0.68f,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    !uiState.hasContent -> {
                        item {
                            SearchEmptyStateCard(
                                reason = uiState.emptyStateReason,
                                errorMessage = uiState.errorMessage,
                                networkCondition = networkStatusUiState.condition,
                                onRetry = {
                                    if (normalizedQuery.isNotBlank()) {
                                        NetworkStatusRepository.requestRefresh(force = true)
                                        if (addonsUiState.addons.firstEnabledManifestError() != null) {
                                            AddonRepository.refreshAll()
                                        } else {
                                            SearchRepository.search(
                                                query = normalizedQuery,
                                                addons = addonsUiState.addons,
                                                forceRefresh = true,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = homeSectionPadding),
                            )
                        }
                    }

                    else -> {
                        searchResultsContent(
                            state = uiState,
                            columns = searchColumns,
                            landscape = searchLandscape,
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                            onPersonClick = onPersonClick,
                        )
                    }
                }
            }
        }
    }
    }
}

private class SearchScrollAnchor(var results: List<MetaPreview> = emptyList())

@Composable
private fun PreserveSearchResultScrollAnchor(
    listState: LazyListState,
    query: String,
    columns: Int,
    results: List<MetaPreview>,
) {
    val previous = remember(query, columns) { SearchScrollAnchor() }
    SideEffect {
        if (previous.results.isNotEmpty() && results.isNotEmpty() && previous.results !== results) {
            val layout = listState.layoutInfo.visibleItemsInfo
            val first = layout.firstOrNull { it.index == listState.firstVisibleItemIndex }
            val key = first?.key as? String
            val row = key?.takeIf { it.startsWith("search-row:") }?.substringAfter(':')?.toIntOrNull()
            val item = row?.let { previous.results.getOrNull(it * columns) }
            if (first != null && row != null && item != null) {
                val newIndex = results.indexOfFirst { it.type == item.type && it.id == item.id }
                if (newIndex >= 0 && newIndex / columns != row) {
                    val target = first.index + newIndex / columns - row
                    listState.requestScrollToItem(target.coerceAtLeast(0), listState.firstVisibleItemScrollOffset)
                }
            }
        }
        previous.results = results
    }
}

@Composable
private fun SearchEmptyStateCard(
    reason: SearchEmptyStateReason?,
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (
        reason == SearchEmptyStateReason.RequestFailed &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            modifier = modifier,
            onRetry = onRetry,
        )
        return
    }

    val title: String
    val message: String

    when (reason) {
        SearchEmptyStateReason.NoActiveAddons -> {
            title = stringResource(Res.string.home_empty_no_sources_title)
            message = stringResource(Res.string.search_empty_no_sources_message)
        }

        SearchEmptyStateReason.NoSearchCatalogs -> {
            title = stringResource(Res.string.compose_search_empty_no_search_catalogs_title)
            message = stringResource(Res.string.compose_search_empty_no_search_catalogs_message)
        }

        SearchEmptyStateReason.RequestFailed -> {
            title = stringResource(Res.string.compose_search_empty_failed_title)
            message = errorMessage ?: stringResource(Res.string.compose_search_empty_failed_message)
        }

        SearchEmptyStateReason.NoResults, null -> {
            title = stringResource(Res.string.compose_search_empty_no_results_title)
            message = stringResource(Res.string.compose_search_empty_no_results_message)
        }
    }

    HomeEmptyStateCard(
        modifier = modifier,
        title = title,
        message = message,
        actionLabel = if (reason == SearchEmptyStateReason.RequestFailed) {
            stringResource(Res.string.action_retry)
        } else {
            null
        },
        onActionClick = if (reason == SearchEmptyStateReason.RequestFailed) onRetry else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchRecentSection(
    recentSearches: List<String>,
    onSearchPress: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.compose_search_recent_searches),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            val clearLabel = stringResource(Res.string.search_clear_history)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(clearLabel) } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Rounded.Close, clearLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        recentSearches.forEach { recentQuery ->
            SearchRecentRow(
                query = recentQuery,
                onSearchPress = { onSearchPress(recentQuery) },
                onRemovePress = { onRemoveSearch(recentQuery) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
internal fun SearchRecentRow(
    query: String,
    onSearchPress: () -> Unit,
    onRemovePress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSearchPress)
            .padding(vertical = 2.dp)
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = query,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemovePress) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.compose_search_remove_recent_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
