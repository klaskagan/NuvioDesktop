package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.isDesktop
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    allowPortraitFallback: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        HomeCatalogRowSectionContent(
            section = section, entries = entries, watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys, modifier = Modifier.fillMaxWidth(),
            sectionPadding = sectionPadding ?: homeSectionHorizontalPaddingForWidth(maxWidth.value),
            onViewAllClick = onViewAllClick, onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick, allowPortraitFallback = allowPortraitFallback,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    allowPortraitFallback: Boolean,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val rowState = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = .25f, behindFraction = .25f) })
    val excluded = preservesOriginalLandscapeArtwork(section.title, section.target)
    val prepared = rememberShelfArtworkPrefetch(rowState, entries, section.key, enhanceTitles = !excluded, allowPortraitFallback = allowPortraitFallback)
    val gate = remember(section.key, entries, prepared, posterCardStyle.catalogLandscapeModeEnabled) { LandscapeRowRevealState() }
    val visibleIndices by remember(rowState) { derivedStateOf { rowState.layoutInfo.visibleItemsInfo.map { it.index } } }
    val cohort = visibleIndices.mapNotNull { entries.getOrNull(it) }.filter {
        isTitleArtworkMedia(it.type)
    }
    val cohortReady = visibleIndices.isNotEmpty() && cohort.all { gate.ready[it.stableKey()] == true }
    SideEffect { if (!gate.revealed && cohortReady) gate.revealed = true }

    CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared, LocalLandscapeRowReveal provides gate) {
    NuvioShelfSection(
        title = section.title,
        state = rowState,
        entries = entries,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = onViewAllClick,
        onTitleClick = onViewAllClick?.takeIf { isDesktop },
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        key = { item -> item.stableKey() },
    ) { item ->
        HomePosterCard(
            item = item,
            allowPortraitFallback = allowPortraitFallback,
            enhanceLandscapeTitles = !excluded,
            useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
            isWatched = WatchingState.isPosterWatched(
                watchedKeys = watchedKeys,
                item = item,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            ),
            onClick = onPosterClick?.let { { it(item) } },
            onLongClick = onPosterLongClick?.let { { it(item) } },
        )
    }
    }
}
