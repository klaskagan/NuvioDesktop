package com.nuvio.app.features.library

import com.nuvio.app.core.ui.RedesignMessageTile
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.AppScreenTab
import com.nuvio.app.core.ui.LocalBackdropFocusController
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignPageLead
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.posterGridColumnCountForViewport
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.cloud.CloudLibraryFile
import com.nuvio.app.features.cloud.CloudLibraryItem
import com.nuvio.app.features.cloud.CloudLibraryItemType
import com.nuvio.app.features.cloud.CloudLibraryUiState
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.flow.Flow
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the desktop Library shows below its header; each pane crossfades into the next. */
internal enum class LibraryDesktopPane { Shelves, Grid, Cloud, Servers }

/** Everything the desktop Library draws, computed by [LibraryScreen] (which also owns mobile). */
internal class LibraryDesktopModel(
    val viewMode: LibraryViewMode,
    val showServers: Boolean,
    val uiState: LibraryUiState,
    val layoutMode: LibraryLayoutMode,
    val sortOption: LibrarySortOption,
    val displaySections: List<LibraryDisplaySection>,
    val projection: LibraryVerticalProjection,
    /** A list opened in place from the shelves ("View all" or Your lists); Esc returns to them. */
    val openedListKey: String?,
    val watchedFilter: LibraryWatchedFilter,
    val watchedKeys: Set<String>,
    val fullyWatchedSeriesKeys: Set<String>,
    val posterPattern: String,
    val cloud: CloudLibraryUiState,
    val cloudProviderId: String?,
    val cloudKind: CloudMediaKind?,
    val cloudQuery: String,
    val cloudItemKey: String?,
    val serverShelves: List<LibraryServerShelf>?,
    val networkCondition: NetworkCondition,
    val offline: Boolean,
) {
    val pane: LibraryDesktopPane
        get() = when (viewMode) {
            LibraryViewMode.Cloud -> LibraryDesktopPane.Cloud
            LibraryViewMode.Servers -> LibraryDesktopPane.Servers
            LibraryViewMode.Saved ->
                if (layoutMode == LibraryLayoutMode.VERTICAL || openedListKey != null) LibraryDesktopPane.Grid
                else LibraryDesktopPane.Shelves
        }
}

internal class LibraryDesktopActions(
    val selectViewMode: (LibraryViewMode) -> Unit,
    val toggleLayout: () -> Unit,
    val openList: (LibrarySection) -> Unit,
    val closeList: () -> Unit,
    val selectSection: (String) -> Unit,
    val selectType: (String?) -> Unit,
    val selectGenre: (String?) -> Unit,
    val selectYear: (String?) -> Unit,
    val selectWatched: (LibraryWatchedFilter) -> Unit,
    val selectSort: (LibrarySortOption) -> Unit,
    val retry: () -> Unit,
    val onPosterClick: ((LibraryItem) -> Unit)?,
    val onPosterLongClick: ((LibraryItem, LibrarySection) -> Unit)?,
    val onDisintegrated: (String) -> Unit,
    val onDownloadsClick: (() -> Unit)?,
    val cloudSelectProvider: (String?) -> Unit,
    val cloudSelectKind: (CloudMediaKind?) -> Unit,
    val cloudQueryChange: (String) -> Unit,
    val cloudSelectItem: (CloudLibraryItem) -> Unit,
    val cloudPlayFile: (CloudLibraryItem, CloudLibraryFile) -> Unit,
    val cloudCloseItem: () -> Unit,
    val cloudRefresh: () -> Unit,
    val onConnectCloudClick: (() -> Unit)?,
    val onPlayClick: ((LibraryItem) -> Unit)? = null,
    val onConnectTrackingClick: (() -> Unit)? = null,
    val onBrowseHomeClick: (() -> Unit)? = null,
    val onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    val onPreviewClick: ((MetaPreview) -> Unit)?,
    val onPreviewLongClick: ((MetaPreview) -> Unit)?,
    val serversRetry: () -> Unit,
)

/**
 * The redesigned desktop Library: it sits on the shell's ambient backdrop with Calendar's header
 * (kicker, title, subtitle; source pills and icon buttons on the right) and swaps panes below it:
 * shelves with a board, a filtered grid, Cloud files with a side panel, or media server shelves.
 * The header is the same component in every pane and state, so no control moves.
 */
@Composable
internal fun LibraryDesktopScreen(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    shelvesState: LazyListState,
    topChromePadding: Dp?,
    scrollToTopRequests: Flow<Unit>,
    modifier: Modifier = Modifier,
) {
    val pane = model.pane
    val gridState = rememberLazyListState()
    val cloudState = rememberLazyListState()
    val serverState = rememberLazyListState()
    val paneState = when (pane) {
        LibraryDesktopPane.Shelves -> shelvesState
        LibraryDesktopPane.Grid -> gridState
        LibraryDesktopPane.Cloud -> cloudState
        LibraryDesktopPane.Servers -> serverState
    }

    // One stable title steers the backdrop per pane (never hover): the latest save, the grid's
    // first title, a server's first title; Cloud has no artwork and falls back to the theme mesh.
    val backdrop = LocalBackdropFocusController.current
    val lead = remember(model.uiState.sections) { librarySpotlight(model.uiState.sections) }
    val backdropArt = when (pane) {
        LibraryDesktopPane.Shelves -> lead?.item?.backdropArt()
        LibraryDesktopPane.Grid -> model.projection.entries.firstOrNull()?.item?.backdropArt()
        LibraryDesktopPane.Cloud -> null
        LibraryDesktopPane.Servers -> model.serverShelves.orEmpty()
            .firstNotNullOfOrNull { it.section?.items?.firstOrNull() }
            ?.let { it.banner ?: it.landscapePoster ?: it.poster }
    }
    ScreenActivityEffect(backdrop, backdropArt) { active ->
        if (active) backdrop?.base = backdropArt
    }

    // The merged top bar turns solid once content has moved under it.
    val navBarScrollState = LocalNuvioNavBarScrollState.current
    LaunchedEffect(paneState, navBarScrollState) {
        snapshotFlow { paneState.firstVisibleItemIndex * 80f + paneState.firstVisibleItemScrollOffset }
            .collect { navBarScrollState?.updateScrollOffset(AppScreenTab.Library, it) }
    }
    ScreenActivityEffect(scrollToTopRequests, paneState) { active ->
        if (!active) return@ScreenActivityEffect
        scrollToTopRequests.collect { paneState.animateScrollToItem(0) }
    }

    // Esc (caught at the window, before Back) closes the in-page layer first: the Cloud file panel,
    // then a list opened from the shelves.
    val screenActive = LocalScreenActive.current
    val escapeTarget: (() -> Unit)? = when {
        pane == LibraryDesktopPane.Cloud && model.cloudItemKey != null -> actions.cloudCloseItem
        pane == LibraryDesktopPane.Grid && model.openedListKey != null -> actions.closeList
        else -> null
    }
    PlatformBackHandler(enabled = screenActive && escapeTarget != null, onBack = { escapeTarget?.invoke() })
    if (screenActive && escapeTarget != null) {
        DisposableEffect(escapeTarget) {
            val handler: () -> Boolean = { escapeTarget(); true }
            AppKeyboardShortcuts.overlayEscape = handler
            onDispose { if (AppKeyboardShortcuts.overlayEscape === handler) AppKeyboardShortcuts.overlayEscape = null }
        }
    }

    RedesignTypography(enabled = true) {
        BoxWithConstraints(
            modifier.fillMaxSize()
                .then(if (backdrop == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                .testTag("library-desktop"),
        ) {
            val gutter = desktopPageHorizontalPaddingForWidth(maxWidth.value)
            val contentWidth = maxWidth - gutter * 2
            val posterStyle = rememberPosterCardStyleUiState()
            val columns = remember(contentWidth, maxHeight, posterStyle.widthDp) {
                posterGridColumnCountForViewport(contentWidth, maxHeight, posterStyle.widthDp)
            }
            Column(Modifier.fillMaxSize().padding(top = ((topChromePadding ?: 16.dp) - 4.dp).coerceAtLeast(8.dp))) {
                LibraryDesktopHeader(model, actions, lead, Modifier.padding(horizontal = gutter))
                Spacer(Modifier.height(14.dp))
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = {
                        (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 40 }) togetherWith
                            (fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 60 })
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "library_pane",
                ) { shown ->
                    CompositionLocalProvider(LocalNuvioShelfHeaderStyle provides NuvioShelfHeaderStyle.Editorial) {
                        when (shown) {
                            LibraryDesktopPane.Shelves -> LibraryShelvesPane(model, actions, lead, shelvesState, gutter)
                            LibraryDesktopPane.Grid -> LibraryGridPane(model, actions, gridState, gutter, columns)
                            LibraryDesktopPane.Cloud -> LibraryCloudPane(model, actions, cloudState, gutter, contentWidth)
                            LibraryDesktopPane.Servers -> LibraryServersPane(model, actions, serverState, gutter)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryDesktopHeader(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    lead: LibrarySpotlight?,
    modifier: Modifier,
) {
    val pane = model.pane
    Row(
        modifier.fillMaxWidth().testTag("library-header"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RedesignPageLead(
            title = stringResource(Res.string.library_title),
            kicker = libraryKicker(model),
            subtitle = librarySubtitle(model),
            modifier = Modifier.weight(1f).padding(bottom = 2.dp),
        )
        Row(
            Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val modes = buildList {
                add(LibraryViewMode.Saved)
                add(LibraryViewMode.Cloud)
                if (model.showServers) add(LibraryViewMode.Servers)
            }
            RedesignPillGroup(
                options = modes,
                selected = model.viewMode,
                label = { mode ->
                    stringResource(
                        when (mode) {
                            LibraryViewMode.Saved -> Res.string.library_source_saved
                            LibraryViewMode.Cloud -> Res.string.library_source_cloud
                            LibraryViewMode.Servers -> Res.string.library_source_servers
                        },
                    )
                },
                onSelect = actions.selectViewMode,
                optionTag = { "library-source-${it.name}" },
            )
            Spacer(Modifier.width(2.dp))
            // Three reserved slots, so switching source never moves a control.
            when (pane) {
                LibraryDesktopPane.Shelves, LibraryDesktopPane.Grid -> {
                    val showGrid = model.layoutMode == LibraryLayoutMode.HORIZONTAL
                    RedesignIconButton(
                        contentDescription = stringResource(
                            if (showGrid) Res.string.library_layout_show_vertical else Res.string.library_layout_show_horizontal,
                        ),
                        onClick = actions.toggleLayout,
                        modifier = Modifier.testTag("library-layout-toggle"),
                    ) { tint ->
                        AnimatedContent(showGrid, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "library_layout_icon") { grid ->
                            Icon(if (grid) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                LibraryDesktopPane.Cloud -> RedesignIconButton(
                    contentDescription = stringResource(Res.string.cloud_library_refresh),
                    onClick = actions.cloudRefresh,
                    vector = Icons.Rounded.Refresh,
                    modifier = Modifier.testTag("library-cloud-refresh"),
                )
                LibraryDesktopPane.Servers -> RedesignIconButton(contentDescription = "", onClick = null)
            }
            // Reserved only where the source can manage lists, so other sources show no gap.
            if (libraryListManagementAvailable()) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (model.viewMode == LibraryViewMode.Saved) LibraryListManagementButton(frosted = true)
                }
            }
            actions.onDownloadsClick?.let { LibraryDownloadsButton(onClick = it, frosted = true) }
        }
    }
}

@Composable
private fun libraryKicker(model: LibraryDesktopModel): String {
    val ui = model.uiState
    return when (model.pane) {
        LibraryDesktopPane.Shelves, LibraryDesktopPane.Grid -> {
            val source = librarySourceLabel(ui.sourceMode)
            if (!ui.isLoaded || ui.sections.isEmpty()) return source
            if (model.pane == LibraryDesktopPane.Grid) {
                val section = model.projection.availableSections.firstOrNull { it.type == model.projection.selectedSectionKey }
                val count = model.projection.entries.size
                listOfNotNull(
                    source,
                    section?.let { localizedLibrarySectionTitle(it.type, it.displayTitle) },
                    pluralStringResource(Res.plurals.library_title_count, count, count),
                ).joinToString(" · ")
            } else {
                val count = ui.items.size.takeIf { it > 0 } ?: ui.sections.sumOf { it.items.size }
                "$source · ${pluralStringResource(Res.plurals.library_title_count, count, count)}"
            }
        }
        LibraryDesktopPane.Cloud -> {
            val label = stringResource(Res.string.library_source_cloud)
            val count = model.cloud.items.size
            val providers = model.cloud.providers.size
            if (!model.cloud.isLoaded || !model.cloud.hasConnectedProvider) label
            else listOf(
                label,
                pluralStringResource(Res.plurals.library_cloud_item_count, count, count),
                pluralStringResource(Res.plurals.library_provider_count, providers, providers),
            ).joinToString(" · ")
        }
        LibraryDesktopPane.Servers -> {
            val label = stringResource(Res.string.library_source_servers)
            val count = model.serverShelves?.size ?: 0
            if (count == 0) label else "$label · ${pluralStringResource(Res.plurals.library_server_library_count, count, count)}"
        }
    }
}

@Composable
private fun librarySubtitle(model: LibraryDesktopModel): String? = when (model.pane) {
    LibraryDesktopPane.Shelves -> model.uiState.sections.takeIf { model.uiState.isLoaded && it.isNotEmpty() }?.let { sections ->
        val names = sections.map { localizedLibrarySectionTitle(it.type, it.displayTitle) }
        if (names.size <= 3) names.joinToString(", ")
        else stringResource(Res.string.library_lists_and_more, names.take(2).joinToString(", "), names.size - 2)
    }
    LibraryDesktopPane.Grid -> model.projection.takeIf { model.uiState.isLoaded && model.uiState.sections.isNotEmpty() }?.let { projection ->
        val total = projection.allTypesCount
        val shown = projection.entries.size
        if (shown == total) null else stringResource(Res.string.library_grid_showing, shown, total)
    }
    LibraryDesktopPane.Cloud -> model.cloud.providers.takeIf { it.isNotEmpty() }?.let { providers ->
        val names = providers.map { it.providerName }
        val joined = if (names.size == 1) names.single()
        else stringResource(Res.string.library_names_and, names.dropLast(1).joinToString(", "), names.last())
        listOfNotNull(joined, model.cloud.refreshedAtEpochMs?.let { refreshedAgo(it) }).joinToString(" · ")
    }
    LibraryDesktopPane.Servers -> model.serverShelves.orEmpty().map { it.ref.connection.name }.distinct()
        .takeIf { it.isNotEmpty() }?.joinToString(", ")
}

@Composable
private fun librarySourceLabel(mode: LibrarySourceMode): String = stringResource(
    when (mode) {
        LibrarySourceMode.LOCAL -> Res.string.library_source_label_local
        LibrarySourceMode.TRAKT -> Res.string.library_source_label_trakt
        LibrarySourceMode.SIMKL -> Res.string.library_source_label_simkl
        LibrarySourceMode.MDBLIST -> Res.string.library_source_label_mdblist
    },
)

/** The most recently saved title and the list it is in: the board's lead tile and the backdrop. */
internal class LibrarySpotlight(val item: LibraryItem, val section: LibrarySection)

internal fun librarySpotlight(sections: List<LibrarySection>): LibrarySpotlight? {
    var best: LibrarySpotlight? = null
    sections.forEach { section ->
        section.items.forEach { item ->
            if (best == null || item.savedAtEpochMs > best!!.item.savedAtEpochMs) best = LibrarySpotlight(item, section)
        }
    }
    return best
}

internal fun LibraryItem.backdropArt(): String? =
    banner?.takeIf { it.isNotBlank() } ?: landscapePoster?.takeIf { it.isNotBlank() } ?: poster

/** Saved, Cloud and Servers share this: rows fade and slide into place instead of jumping. */
internal fun LazyItemScope.libraryDesktopItemModifier(): Modifier =
    Modifier.animateItem(
        fadeInSpec = tween(220),
        placementSpec = tween(260, easing = FastOutSlowInEasing),
        fadeOutSpec = tween(140),
    )

/** First-load error for the saved lists: the shared offline card, or a frosted message with Retry. */
@Composable
internal fun LibraryLoadProblem(model: LibraryDesktopModel, actions: LibraryDesktopActions, gutter: Dp) {
    if (model.offline) {
        NuvioNetworkOfflineCard(
            condition = model.networkCondition,
            modifier = Modifier.padding(horizontal = gutter),
            onRetry = actions.retry,
        )
    } else {
        RedesignMessageTile(tag = "library-message", 
            title = stringResource(
                when (model.uiState.sourceMode) {
                    LibrarySourceMode.LOCAL -> Res.string.library_load_failed
                    LibrarySourceMode.TRAKT -> Res.string.library_trakt_load_failed
                    LibrarySourceMode.SIMKL -> Res.string.library_simkl_load_failed
                    LibrarySourceMode.MDBLIST -> Res.string.library_mdblist_load_failed
                },
            ),
            message = model.uiState.errorMessage.orEmpty(),
            actionLabel = stringResource(Res.string.action_retry),
            onAction = actions.retry,
            modifier = Modifier.padding(horizontal = gutter),
        )
    }
}

@Composable
internal fun LibraryEmptyLibrary(model: LibraryDesktopModel, actions: LibraryDesktopActions, gutter: Dp) {
    val mode = model.uiState.sourceMode
    RedesignMessageTile(tag = "library-message", 
        title = stringResource(
            when (mode) {
                LibrarySourceMode.LOCAL -> Res.string.library_empty_title
                LibrarySourceMode.TRAKT -> Res.string.library_trakt_empty_title
                LibrarySourceMode.SIMKL -> Res.string.library_simkl_empty_title
                LibrarySourceMode.MDBLIST -> Res.string.library_mdblist_empty_title
            },
        ),
        message = stringResource(
            when (mode) {
                LibrarySourceMode.LOCAL -> Res.string.library_empty_message
                LibrarySourceMode.TRAKT -> Res.string.library_trakt_empty_message
                LibrarySourceMode.SIMKL -> Res.string.library_simkl_empty_message
                LibrarySourceMode.MDBLIST -> Res.string.library_mdblist_empty_message
            },
        ),
        modifier = Modifier.padding(horizontal = gutter),
        bookmark = true,
        actionLabel = stringResource(Res.string.library_empty_browse),
        onAction = actions.onBrowseHomeClick,
        // Saved on this device only: offer to bring lists from a tracking account.
        secondaryLabel = stringResource(Res.string.library_empty_connect).takeIf { mode == LibrarySourceMode.LOCAL },
        onSecondary = actions.onConnectTrackingClick.takeIf { mode == LibrarySourceMode.LOCAL },
    )
}

/** The Cloud pane: filters, then the file list with the selected item's files beside it. */
@Composable
private fun LibraryCloudPane(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    state: LazyListState,
    gutter: Dp,
    contentWidth: Dp,
) {
    val view = rememberCloudView(model)
    val wide = contentWidth >= 760.dp
    Column(Modifier.fillMaxSize()) {
        if (view.showToolbar) {
            LibraryCloudToolbar(model, actions, view, Modifier.padding(horizontal = gutter))
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter)) {
            val panelItem = view.selectedItem
            if (panelItem != null && !wide) {
                LibraryCloudFilePanel(panelItem, actions, Modifier.fillMaxSize().padding(bottom = 24.dp))
            } else {
                LazyColumn(
                    state = state,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("library-cloud-list"),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    libraryCloudItems(model, actions, view)
                }
                AnimatedVisibility(
                    visible = panelItem != null,
                    enter = fadeIn(tween(220)) + expandHorizontally(tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                    exit = fadeOut(tween(160)) + shrinkHorizontally(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
                ) {
                    // Keep drawing the last item while the panel slides out.
                    val last = remember { mutableStateOf<CloudLibraryItem?>(null) }
                    SideEffect { if (panelItem != null) last.value = panelItem }
                    val item = panelItem ?: last.value
                    if (item != null) {
                        LibraryCloudFilePanel(item, actions, Modifier.padding(start = 16.dp, bottom = 24.dp).width(352.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryServersPane(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    state: LazyListState,
    gutter: Dp,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("library-servers"),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        libraryDesktopServerContent(
            shelves = model.serverShelves,
            gutter = gutter,
            watchedKeys = model.watchedKeys,
            fullyWatchedSeriesKeys = model.fullyWatchedSeriesKeys,
            onCatalogClick = actions.onCatalogClick,
            onPosterClick = actions.onPreviewClick,
            onPosterLongClick = actions.onPreviewLongClick,
            onRetry = actions.serversRetry,
        )
    }
}

/** Back control at the start of the grid's controls while a list is open from the shelves. */
@Composable
internal fun LibraryCloseListButton(onClick: () -> Unit) {
    RedesignIconButton(
        contentDescription = stringResource(Res.string.action_back),
        onClick = onClick,
        vector = Icons.AutoMirrored.Rounded.ArrowBack,
        modifier = Modifier.testTag("library-list-back"),
    )
}
