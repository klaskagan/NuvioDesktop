package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.features.trakt.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()
    val calendar = section.calendarState
    Column(modifier = modifier.fillMaxWidth()) {
    if (calendar != null && (calendar.problem != null || entries.isEmpty())) {
        if (calendar.loading && entries.isEmpty()) HomeSkeletonRow()
        else HomeEmptyStateCard(
            title = section.title,
            modifier = Modifier.padding(horizontal = sectionPadding ?: 24.dp),
            message = stringResource(when (calendar.problem) {
                CalendarProblem.DISCONNECTED -> Res.string.calendar_disconnected
                CalendarProblem.AUTHENTICATION -> Res.string.calendar_auth_expired
                CalendarProblem.NETWORK -> if (calendar.partial) Res.string.calendar_partial else Res.string.calendar_error
                null -> Res.string.calendar_empty
            }),
            actionLabel = if (calendar.problem == CalendarProblem.NETWORK) stringResource(Res.string.action_retry) else null,
            onActionClick = if (calendar.problem == CalendarProblem.NETWORK) ({ scope.launch { TraktCalendarRepository.load(force = true) }; Unit }) else null,
        )
    }
    if (entries.isNotEmpty()) BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        HomeCatalogRowSectionContent(
            section = section, entries = entries, watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys, modifier = Modifier.fillMaxWidth(),
            sectionPadding = sectionPadding ?: homeSectionHorizontalPaddingForWidth(maxWidth.value),
            onViewAllClick = onViewAllClick, onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick, allowPortraitFallback = allowPortraitFallback,
        )
    }
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
    val excluded = preservesOriginalLandscapeArtwork(section.title, section.target) || entries.any { it.calendarCard != null }
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
