package com.nuvio.app.features.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.core.ui.BackdropSurface
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import com.nuvio.app.core.ui.LocalBackdropFocusController
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.NavLayer
import com.nuvio.app.core.ui.NavSide
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.core.ui.navTransitionLayer
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.StreamingCollection
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.LoadMoreOnScrollEffect
import com.nuvio.app.features.watched.WatchedRepository
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * One discovery session per service, kept for the app's lifetime so returning from a title (or
 * reopening the folder) shows the loaded page instead of reloading it. Separate from Search's
 * session: the two pages keep their own tab, "View all" and hero state.
 */
internal object StreamingProviderSessions {
    private val sessions = mutableMapOf<String, SearchDiscoverySession>()

    fun sessionFor(selection: SearchDiscovery): SearchDiscoverySession =
        sessions.getOrPut(selection.name) { SearchDiscoverySession().also { it.select(selection) } }

    fun clear() = sessions.clear()
}

/**
 * A streaming service's page, opened from its folder in the streaming collection. It draws the
 * same rows as Search's provider tag ([streamingProviderPage]) under the folder's logo.
 */
@Composable
internal fun StreamingProviderScreen(
    collectionId: String,
    folderId: String,
    title: String,
    onBack: () -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection = remember(collectionId, folderId) { StreamingCollection.provider(collectionId, folderId) } ?: return
    val session = remember(selection) { StreamingProviderSessions.sessionFor(selection) }
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()
    val folder = collections.firstOrNull { it.id == collectionId }?.folders?.firstOrNull { it.id == folderId }
    val active = LocalScreenActive.current

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
    }
    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val homeSettings by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val hideUnplayable by remember {
        SearchRepository.ensurePreferencesLoaded()
        SearchRepository.hideUnplayable
    }.collectAsStateWithLifecycle()
    val rowLayout by remember {
        StreamingRowLayoutRepository.ensureLoaded()
        StreamingRowLayoutRepository.layout
    }.collectAsStateWithLifecycle()

    var scope by rememberSaveable(selection.name) { mutableStateOf(SearchScope.All) }
    val listState = rememberLazyListState()
    val shelfView = session.view
    val goBack = { if (session.view != null) session.view = null else onBack() }
    PlatformBackHandler(enabled = shelfView != null, onBack = goBack)

    CompositionLocalProvider(LocalSearchDiscoverySession provides session) {
        val shelves = rememberSearchDiscoveryShelves(
            selection = selection,
            active = active,
            hideUnplayable = hideUnplayable,
            hideUnreleased = homeSettings.hideUnreleasedContent,
        )
        val topTen = rememberSearchTopTen(selection = selection, addons = addonsUiState.addons, active = active)
        val collectionRows = rememberStreamingCollectionRows(
            selection = selection,
            region = shelves.region,
            addons = addonsUiState.addons,
            active = active,
        )
        val discoveryUi = rememberSearchDiscovery(
            selection = selection.takeIf { shelfView != null },
            active = active,
            hideUnplayable = hideUnplayable,
            hideUnreleased = homeSettings.hideUnreleasedContent,
            view = shelfView,
            regionOverride = shelves.region,
        )
        LoadMoreOnScrollEffect(
            state = listState,
            canLoadMore = discoveryUi.hasMore && !discoveryUi.failed,
            isLoading = discoveryUi.loading,
            enabled = shelfView != null,
            onLoadMore = discoveryUi.load,
        )

        // Ambient backdrop: the folder's own artwork until the hero's first title is known.
        val backdropMode = LocalShellBackdropMode.current
        val backdrop = remember(backdropMode == ShellBackdropMode.Solid) {
            if (backdropMode != ShellBackdropMode.Solid) BackdropFocusController() else null
        }
        val heroArt = tagHeroItems(topTen, shelves, scope).firstOrNull()?.let { it.banner ?: it.poster }
        val backdropArt = heroArt ?: folder?.heroBackdropUrl
        LaunchedEffect(backdrop, backdropArt) { backdrop?.base = backdropArt }

        val headerScrolled by remember(listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        val headerColor by animateColorAsState(
            targetValue = when {
                backdrop == null -> MaterialTheme.colorScheme.background
                headerScrolled -> BackdropSurface.stickyHeader
                else -> Color.Transparent
            },
            animationSpec = tween(220),
            label = "streaming_sticky_header",
        )
        val hazeState = rememberHazeState()

        Box(
            modifier.fillMaxSize()
                .then(if (backdrop == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                .testTag("streaming_provider_${selection.name}"),
        ) {
            if (backdrop != null) {
                Box(Modifier.fillMaxSize().navTransitionLayer(NavLayer.Background, NavSide.Above)) {
                    ShellBackdrop(backdrop, showArtwork = backdropMode == ShellBackdropMode.Ambient)
                }
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().navTransitionLayer(NavLayer.Content, NavSide.Above)
                    .padding(start = LocalBackdropContentStartInset.current),
            ) {
                // Same column as Search: at most ~1240dp of content, never closer than 40dp to the edges.
                val gutter = maxOf(40.dp, (maxWidth - 1240.dp) / 2)
                val contentWidth = maxWidth - gutter * 2
                val posterCardStyle = rememberPosterCardStyleUiState()
                val columns = remember(contentWidth, posterCardStyle.widthDp) {
                    searchGridColumns(contentWidth, posterCardStyle.widthDp, landscape = false)
                }
                CompositionLocalProvider(LocalBackdropFocusController provides backdrop) {
                RedesignTypography(enabled = true) {
                NuvioScreen(
                    horizontalPadding = 0.dp,
                    topPadding = 0.dp,
                    listState = listState,
                    modifier = Modifier.fillMaxSize(),
                    drawBackground = backdrop == null,
                ) {
                    val pinned = headerColor.alpha > 0.05f
                    // Rows are blur sources for the pinned header and fade/slide whenever the layout changes.
                    val itemModifier: LazyItemScope.() -> Modifier = {
                        Modifier.animateItem(
                            fadeInSpec = tween(220),
                            placementSpec = tween(260, easing = FastOutSlowInEasing),
                            fadeOutSpec = tween(160),
                        ).hazeSource(hazeState)
                    }
                    stickyHeader(key = "streaming-header") {
                        Box(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.matchParentSize()
                                    .then(if (pinned) Modifier.hazeEffect(hazeState) { blurRadius = 24.dp } else Modifier)
                                    .background(headerColor)
                                    .nuvioConsumePointerEvents(),
                            )
                            if (headerColor.alpha > 0.5f) {
                                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 24.dp, bottom = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NuvioBackButton(
                                        onClick = goBack,
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        buttonSize = 48.dp,
                                        iconSize = 24.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    var logoFailed by remember(folder?.titleLogoUrl) { mutableStateOf(false) }
                                    val logo = folder?.titleLogoUrl?.takeIf { it.isNotBlank() && !logoFailed }
                                    if (logo != null) {
                                        NuvioAsyncImage(
                                            logo, title,
                                            Modifier.height(52.dp).widthIn(max = 260.dp),
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.CenterStart,
                                            onError = { logoFailed = true },
                                        )
                                    } else {
                                        Text(
                                            title.ifBlank { selection.name },
                                            style = redesignText(28.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (shelfView == null) {
                                    TagPageControls(scope = scope, onScopeChange = { scope = it }, fallbackCountry = shelves.fallbackCountry)
                                }
                            }
                        }
                    }
                    if (shelfView == null) {
                        streamingProviderPage(
                            selection = selection,
                            shelves = shelves,
                            topTen = topTen,
                            collectionRows = collectionRows,
                            layout = rowLayout,
                            heroMeta = session.heroMeta,
                            scope = scope,
                            gutter = gutter,
                            itemModifier = itemModifier,
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                            onViewAll = {
                                session.view = it
                                listState.requestScrollToItem(0)
                            },
                            onCollectionRowViewAll = onCatalogClick,
                        )
                    } else {
                        searchDesktopDiscoveryResults(
                            selection = selection,
                            view = shelfView,
                            onBack = {
                                session.view = null
                                listState.requestScrollToItem(0)
                            },
                            state = discoveryUi,
                            columns = columns,
                            gutter = gutter,
                            itemModifier = itemModifier,
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                        )
                    }
                }
                }
                }
            }
        }
    }
}
