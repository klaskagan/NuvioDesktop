package com.nuvio.app.features.home.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.nuvio.app.core.ui.landscapeImageRequest
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterWidth
import kotlin.math.ceil
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

internal fun artworkVisibleCardCount(widthDp: Float, paddingDp: Float, posterBaseWidth: Int, landscape: Boolean = true): Int {
    val base = desktopCatalogShelfPosterBaseWidthDp(posterBaseWidth)
    val cardWidth = if (landscape) landscapePosterWidth(base).value else base.toFloat()
    return ceil(((widthDp - paddingDp * 2).coerceAtLeast(0f) + 10f) / (cardWidth + 10f)).toInt().coerceAtLeast(1)
}

/** A visual row: a LazyColumn item or a group of individual LazyVerticalGrid cells. */
internal data class ArtworkPrefetchRow(
    val key: Any,
    val items: List<MetaPreview>,
    val itemKeys: List<Any> = listOf(key),
    val enhanceTitles: Boolean = true,
    val forceShape: PosterShape? = null,
    val alwaysShowTitle: Boolean = false,
)

@Composable
internal fun artworkFixedGridColumns(widthPx: Int, padding: androidx.compose.ui.unit.Dp, cellWidth: androidx.compose.ui.unit.Dp): Int =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        val available = (widthPx - 2 * padding.roundToPx()).coerceAtLeast(0)
        val spacing = androidx.compose.ui.unit.Dp(12f).roundToPx()
        // GridCells.FixedSize rounds each dimension before integer division.
        ((available + spacing) / (cellWidth.roundToPx() + spacing).coerceAtLeast(1)).coerceAtLeast(1)
    }

internal fun artworkGridRows(items: List<MetaPreview>, keys: List<Any>, columns: Int): List<ArtworkPrefetchRow> =
    items.indices.chunked(columns.coerceAtLeast(1)).map { indices ->
        ArtworkPrefetchRow("grid-row:${keys[indices.first()]}", indices.map(items::get), indices.map(keys::get))
    }

/** Individual grid cells share readiness with the other media in their visual row. */
@Composable
internal fun rememberGridArtworkGates(rows: List<ArtworkPrefetchRow>, identity: Any): Map<Any, LandscapeRowRevealState> {
    val settings by remember {
        com.nuvio.app.features.tmdb.TmdbSettingsRepository.ensureLoaded()
        com.nuvio.app.features.tmdb.TmdbSettingsRepository.uiState
    }.collectAsState()
    val loader = LocalLandscapeArtworkLoader.current
    val landscape = com.nuvio.app.core.ui.rememberPosterCardStyleUiState().catalogLandscapeModeEnabled
    val profile by com.nuvio.app.features.profiles.ProfileRepository.state.collectAsState()
    val gates = remember(identity, settings.language, settings.apiKey, loader, landscape, profile.activeProfile?.id) {
        mutableMapOf<Pair<Any, List<Pair<String, PosterShape>>>, LandscapeRowRevealState>()
    }
    val memberships = rows.associate { row -> row.key to row.items.map { it.stableKey() to it.posterShape } }
    val result = rows.associate { row ->
        val gateKey = row.key to memberships.getValue(row.key)
        row.key to gates.getOrPut(gateKey) {
            LandscapeRowRevealState(row.items.filter { isTitleArtworkMedia(it.type) }
                .map(MetaPreview::stableKey).toSet())
        }
    }
    SideEffect { gates.keys.retainAll(rows.map { it.key to memberships.getValue(it.key) }.toSet()) }
    return rows.flatMap { row -> row.itemKeys.map { it to result.getValue(row.key) } }.toMap()
}

@Composable
internal fun rememberListArtworkPrefetch(
    state: LazyListState,
    rows: List<ArtworkPrefetchRow>,
    identity: Any,
    enabled: Boolean = true,
    language: String? = null,
    apiKey: String? = null,
    window: MediaPrefetchWindow? = null,
): PreparedArtworkStore = rememberViewportArtworkPrefetch(state, rows, identity, enabled, language, apiKey, window = window) {
    state.layoutInfo.visibleItemsInfo.map { ArtworkViewportItem(it.key, it.offset, it.size) }
}

@Composable
internal fun rememberGridArtworkPrefetch(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    rows: List<ArtworkPrefetchRow>,
    identity: Any,
    enabled: Boolean = true,
    allowPortraitFallback: Boolean = true,
    forceShape: PosterShape? = null,
    window: MediaPrefetchWindow? = null,
): PreparedArtworkStore = rememberViewportArtworkPrefetch(state, rows, identity, enabled, allowPortraitFallback = allowPortraitFallback, forceShape = forceShape, window = window) {
    state.layoutInfo.visibleItemsInfo.map { ArtworkViewportItem(it.key, it.offset.y, it.size.height) }
}

@Composable
private fun rememberViewportArtworkPrefetch(
    viewport: Any,
    rows: List<ArtworkPrefetchRow>,
    identity: Any,
    enabled: Boolean,
    requestedLanguage: String? = null,
    requestedApiKey: String? = null,
    allowPortraitFallback: Boolean = true,
    forceShape: PosterShape? = null,
    parentStore: PreparedArtworkStore? = null,
    window: MediaPrefetchWindow? = null,
    horizontal: Boolean = false,
    visibleItems: () -> List<ArtworkViewportItem>,
): PreparedArtworkStore {
    val settings by remember {
        com.nuvio.app.features.tmdb.TmdbSettingsRepository.ensureLoaded()
        com.nuvio.app.features.tmdb.TmdbSettingsRepository.uiState
    }.collectAsState()
    val profile by com.nuvio.app.features.profiles.ProfileRepository.state.collectAsState()
    val style = com.nuvio.app.core.ui.rememberPosterCardStyleUiState()
    val language = requestedLanguage ?: settings.language
    val apiKey = requestedApiKey ?: settings.apiKey
    val landscape = style.catalogLandscapeModeEnabled
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val metadataLoader = LocalLandscapeArtworkLoader.current
    val parent = rememberCoroutineScope()
    val scope = remember(identity, profile.activeProfile?.id, language, apiKey, metadataLoader, imageLoader, landscape, enabled, allowPortraitFallback, forceShape, parentStore, style.alwaysShowLandscapeClearlogo) {
        CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    var active by remember(scope) { mutableStateOf(true) }
    ScreenActivityEffect(scope) { active = it }
    val scheduler = rememberMediaPreparationScheduler()
    fun requestKey(request: MediaPreload) = MediaPreparationKey(profile.activeProfile?.id, language, apiKey, metadataLoader, imageLoader,
        listOf(request.item.id, request.item.type, request.item.poster, request.item.rawPosterUrl,
            request.item.banner, request.item.landscapePoster, request.item.logo),
        request.shape, request.enhanceTitles, request.alwaysShowTitle, allowPortraitFallback, style.alwaysShowLandscapeClearlogo)
    val allKeys = remember(rows, scope) { rows.flatMap { row -> row.items.map { item ->
        val request = MediaPreload(item, row.forceShape ?: forceShape ?: if (landscape) PosterShape.Landscape else item.posterShape, row.enhanceTitles, row.alwaysShowTitle)
        item.stableKey() to requestKey(request)
    } }.toMap() }
    val latestKeys by rememberUpdatedState(allKeys)
    val prepared = remember(scope, scheduler) { PreparedArtworkStore(parentStore) { key -> latestKeys[key]?.let(scheduler::peek) } }
    prepared.rows(rows)
    val managedKeys = remember(rows) { rows.flatMap { it.items }.map(MetaPreview::stableKey).toSet() }
    prepared.manage(managedKeys)
    val order = remember(rows) { rows.map { it.key.toString() } }
    val rowForItem = remember(rows) { rows.flatMap { row -> row.itemKeys.map { it to row.key.toString() } }.toMap() }
    val byKey = remember(rows) { rows.associateBy { it.key.toString() } }
    val prefetchWindow = window ?: rememberMediaPrefetchWindow(scope)
    fun visibleRows() = visibleItems().mapNotNull { item -> rowForItem[item.key]?.let { key -> key to item } }
    var plannedRows by remember(scope, order) { mutableStateOf(adaptivePrefetchRowKeys(order, visibleRows().map { it.first }, 2, 1)) }
    var visibleRowKeys by remember(scope, order) { mutableStateOf(emptySet<String>()) }
    val rowIndices = remember(order) { order.withIndex().associate { it.value to it.index } }
    LaunchedEffect(scope, rows, viewport, scheduler, prefetchWindow) {
        var previousVisible = emptySet<String>()
        snapshotFlow { visibleRows() to scheduler.metrics.preparationMillis }
            .distinctUntilChanged().collectLatest { (visible, preparationMillis) ->
                val first = visible.minByOrNull { rowIndices[it.first] ?: 0 }
                val position = first?.let { (key, item) ->
                    (rowIndices[key] ?: 0).toDouble() - item.offset.toDouble() / item.size.coerceAtLeast(1)
                } ?: 0.0
                val units = if (horizontal) visible.size.coerceAtLeast(1) else 1
                fun update() {
                    if (visible.isNotEmpty()) prefetchWindow.observe(position, mediaPrefetchNowMillis(), preparationMillis, units)
                    plannedRows = adaptivePrefetchRowKeys(order, visible.map { it.first }, prefetchWindow.aheadRows, prefetchWindow.direction, units)
                }
                update()
                visibleRowKeys = visible.map { it.first }.toSet()
                prepared.visibleRows = visibleRowKeys.sortedBy { rowIndices[it] ?: 0 }
                val visibleSet = visible.map { it.first }.toSet()
                (visibleSet - previousVisible).forEach { key ->
                    val media = byKey[key]?.items.orEmpty().filter { isTitleArtworkMedia(it.type) }
                    if (media.isNotEmpty()) scheduler.metrics.rowEntered(media.all { prepared[it.stableKey()]?.isLoading == false })
                }
                previousVisible = visibleSet
                delay(300)
                update() // A stationary viewport returns to the small baseline window.
            }
    }
    val priorities = remember(plannedRows, visibleRowKeys, byKey) {
        plannedRows.flatMapIndexed { index, key -> byKey[key]?.items.orEmpty().mapIndexed { card, item ->
            item.stableKey() to (if (key in visibleRowKeys) index * 1000 + card else 1_000_000 + index * 1000 + card)
        } }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.min() }
    }
    val candidates = remember(plannedRows, byKey, landscape, active, enabled, forceShape, parentStore) {
        (if (active && enabled) plannedRows else emptyList()).flatMap { key ->
            val row = byKey[key] ?: return@flatMap emptyList()
            row.items.filter { isTitleArtworkMedia(it.type) && parentStore?.manages(it.stableKey()) != true }.map { item ->
                MediaPreload(item, row.forceShape ?: forceShape ?: if (landscape) PosterShape.Landscape else item.posterShape,
                    row.enhanceTitles, row.alwaysShowTitle)
            }
        }.distinctBy { it.item.stableKey() }
    }
    val owner = remember(scope) { Any() }
    DisposableEffect(scheduler, owner) { onDispose { scheduler.remove(owner) } }
    LaunchedEffect(scheduler, owner, candidates, priorities) {
        prepared.plan(candidates.map { it.item.stableKey() })
        scheduler.update(owner, candidates.map { request ->
            val requestKey = requestKey(request)
            SharedMediaPreparationScheduler.Request(requestKey, priorities[request.item.stableKey()] ?: 1000, load = {
                val item = request.item
                var artwork: com.nuvio.app.features.tmdb.TmdbLandscapeArtwork? = null
                var logoFailed = false
                try {
                val completed = withTimeoutOrNull(12_000) {
                    val wide = request.shape == PosterShape.Landscape
                    val enhanced = wide && request.enhanceTitles && !request.alwaysShowTitle
                    val metadataStart = mediaPrefetchNowMillis()
                    artwork = if (enhanced) metadataLoader(item.id, item.type, language) else null
                    if (enhanced && artwork == null) logoFailed = true
                    scheduler.metrics.metadata(mediaPrefetchNowMillis() - metadataStart)
                    val landscapePoster = item.landscapePoster.takeUnless { request.alwaysShowTitle || (!enhanced && style.alwaysShowLandscapeClearlogo) }
                    val background = if (wide) artwork?.cleanBackdrop ?: landscapePoster ?: item.banner ?: item.poster.takeIf { allowPortraitFallback } else item.poster
                    val logo = if (artwork?.cleanBackdrop != null) artwork?.logo ?: item.logo
                        else if (wide && (!request.enhanceTitles || request.alwaysShowTitle) && landscapePoster.isNullOrBlank()) item.logo else null
                    val fallback = if (artwork?.cleanBackdrop != null) null
                        else if (wide && !landscapePoster.isNullOrBlank()) item.banner ?: item.rawPosterUrl.takeIf { allowPortraitFallback }
                        else item.rawPosterUrl.takeIf { !wide || allowPortraitFallback }
                    val imageStart = mediaPrefetchNowMillis()
                    coroutineScope {
                        val image = async {
                            background?.let {
                                val model = com.nuvio.app.core.ui.posterImageRequest(context, it, request.shape.toPosterShape(), fallback = fallback)
                                imageLoader.execute(model).also { result ->
                                    if (result is coil3.request.SuccessResult) com.nuvio.app.core.ui.cacheMediaImagePreview(model, result.image)
                                }
                            }
                        }
                        val title = async {
                            logo?.let {
                                val model = landscapeImageRequest(context, it, logo = true)
                                imageLoader.execute(model).also { result ->
                                    if (result is coil3.request.SuccessResult) com.nuvio.app.core.ui.cacheMediaImagePreview(model, result.image)
                                }
                            }
                        }
                        val imageResult = image.await()
                        logoFailed = (title.await() is coil3.request.ErrorResult) || logoFailed
                        if (imageResult is coil3.request.ErrorResult) { artwork = artwork?.copy(cleanBackdrop = null); logoFailed = true }
                    }
                    scheduler.metrics.images(mediaPrefetchNowMillis() - imageStart)
                    true
                } ?: false
                if (!completed) logoFailed = true
                } catch (cancelled: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    artwork = null; logoFailed = true
                } catch (_: Exception) { artwork = null; logoFailed = true }
                LandscapeMediaArtworkState(artwork, logoFailed = logoFailed)
            }, ready = { result -> prepared.complete(request.item.stableKey(), result) })
        })
    }
    return prepared
}


/** Home uses the same scheduler as grids, with only the visible horizontal slice planned. */
@Composable
internal fun rememberHomeArtworkPrefetch(
    listState: LazyListState,
    rows: List<Pair<String, HomeCatalogSection?>>,
    visibleCardCount: Int,
    landscape: Boolean,
    profileKey: String,
    language: String,
    apiKey: String,
): PreparedArtworkStore {
    val planned = remember(rows, visibleCardCount) { rows.map { (key, section) ->
        ArtworkPrefetchRow(key, if (section == null || preservesOriginalLandscapeArtwork(section.title, section.target) || section.target is com.nuvio.app.features.catalog.CatalogTarget.Seasonal) emptyList() else section.items.take(visibleCardCount + 1),
            enhanceTitles = section != null && !preservesOriginalLandscapeArtwork(section.title, section.target) && section.target !is com.nuvio.app.features.catalog.CatalogTarget.Seasonal,
            forceShape = if (section?.target is com.nuvio.app.features.catalog.CatalogTarget.Seasonal) PosterShape.Landscape else null,
            alwaysShowTitle = section?.target is com.nuvio.app.features.catalog.CatalogTarget.Seasonal)
    } }
    return rememberListArtworkPrefetch(listState, planned, profileKey, enabled = true, language = language, apiKey = apiKey)
}

private data class ArtworkViewportItem(val key: Any, val offset: Int, val size: Int)
private data class MediaPreparationKey(
    val profileId: String?, val language: String, val apiKey: String, val loader: Any, val imageLoader: Any,
    val artworkInputs: List<String?>, val shape: PosterShape, val enhanceTitles: Boolean, val alwaysShowTitle: Boolean,
    val allowPortraitFallback: Boolean, val alwaysShowClearlogo: Boolean,
)
private data class MediaPreload(val item: MetaPreview, val shape: PosterShape, val enhanceTitles: Boolean, val alwaysShowTitle: Boolean)
private fun PosterShape.toPosterShape(): com.nuvio.app.core.ui.NuvioPosterShape = when (this) {
    PosterShape.Poster -> com.nuvio.app.core.ui.NuvioPosterShape.Poster
    PosterShape.Landscape -> com.nuvio.app.core.ui.NuvioPosterShape.Landscape
    PosterShape.Square -> com.nuvio.app.core.ui.NuvioPosterShape.Square
}

/** Every standalone media shelf gets horizontal lookahead; enclosing pages retain vertical lookahead. */
@Composable
internal fun rememberShelfArtworkPrefetch(
    state: LazyListState,
    items: List<MetaPreview>,
    identity: Any,
    enhanceTitles: Boolean = true,
    forceShape: PosterShape? = null,
    allowPortraitFallback: Boolean = true,
    alwaysShowLandscapeTitle: Boolean = false,
): PreparedArtworkStore {
    // Original-artwork shelves must not share result slots with enhanced cards of the same title.
    val parent = LocalPreparedLandscapeArtwork.current.takeIf { enhanceTitles && !alwaysShowLandscapeTitle }
    val rows = remember(items, enhanceTitles, forceShape, alwaysShowLandscapeTitle) { items.distinctBy(MetaPreview::stableKey).map {
        ArtworkPrefetchRow(it.stableKey(), listOf(it), enhanceTitles = enhanceTitles, forceShape = forceShape, alwaysShowTitle = alwaysShowLandscapeTitle)
    } }
    val controlledByParent = parent != null && items.any { parent.manages(it.stableKey()) }
    val approaching = !controlledByParent || items.any { parent?.isPlanned(it.stableKey()) == true }
    val prepared = rememberViewportArtworkPrefetch(state, rows, identity, approaching, allowPortraitFallback = allowPortraitFallback, parentStore = parent, horizontal = true) {
        state.layoutInfo.visibleItemsInfo.map { ArtworkViewportItem(it.key, it.offset, it.size) }
    }
    val visibleKeys by remember(state, items) { derivedStateOf {
        state.layoutInfo.visibleItemsInfo.mapNotNull { items.getOrNull(it.index)?.stableKey() }
    } }
    SideEffect { prepared.visibleCohort(visibleKeys) }
    return prepared
}

@Composable
internal fun rememberMediaShelfGate(state: LazyListState, items: List<MetaPreview>, identity: Any): LandscapeRowRevealState {
    val prepared = LocalPreparedLandscapeArtwork.current
    val gate = remember(identity, items.map { it.stableKey() }, prepared) { LandscapeRowRevealState() }
    val indices by remember(state) { derivedStateOf { state.layoutInfo.visibleItemsInfo.map { it.index } } }
    val cohort = indices.mapNotNull(items::getOrNull).filter { isTitleArtworkMedia(it.type) }
    val ready = indices.isNotEmpty() && cohort.all { gate.ready[it.stableKey()] == true }
    SideEffect { if (ready) gate.revealed = true }
    return gate
}

/** A non-scrolling visual row still uses the same bounded fetch/decoded-image pipeline. */
@Composable
internal fun rememberFixedRowArtworkPrefetch(items: List<MetaPreview>): PreparedArtworkStore {
    val parent = LocalPreparedLandscapeArtwork.current
    val rows = remember(items) { listOf(ArtworkPrefetchRow("media-row", items)) }
    return rememberViewportArtworkPrefetch("media-row", rows, items.map(MetaPreview::stableKey), true, parentStore = parent) { listOf(ArtworkViewportItem("media-row", 0, 1)) }
}
