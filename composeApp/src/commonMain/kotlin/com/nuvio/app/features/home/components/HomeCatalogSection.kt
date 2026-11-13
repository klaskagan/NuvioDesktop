package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Column
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import androidx.compose.foundation.layout.width
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
import com.nuvio.app.features.home.HomeRowStyle
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
    rowStyle: HomeRowStyle = HomeRowStyle.Automatic,
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
            rowStyle = rowStyle,
            rankingLayout = if (rowStyle == HomeRowStyle.NumberedRanking) NumberedRankingLayout(maxWidth) else null,
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
    rowStyle: HomeRowStyle,
    rankingLayout: NumberedRankingLayout?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val rowState = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = .25f, behindFraction = .25f) })
    val excluded = preservesOriginalLandscapeArtwork(section.title, section.target) || entries.any { it.calendarCard != null }
    val artworkItems = remember(entries, rowStyle) { entries.map(rowStyle::artworkItem) }
    val prepared = rememberShelfArtworkPrefetch(
        rowState, artworkItems, section.key to rowStyle, enhanceTitles = !excluded && rowStyle.enhancesLandscapeArtwork,
        allowPortraitFallback = allowPortraitFallback,
        forceShape = rowStyle.posterShape,
        alwaysShowLandscapeTitle = rowStyle == HomeRowStyle.BannerWithTitle,
    )
    val gate = remember(section.key, entries, prepared, posterCardStyle.catalogLandscapeModeEnabled) { LandscapeRowRevealState() }
    val visibleIndices by remember(rowState) { derivedStateOf { rowState.layoutInfo.visibleItemsInfo.map { it.index } } }
    val cohort = visibleIndices.mapNotNull { entries.getOrNull(it) }.filter {
        isTitleArtworkMedia(it.type)
    }
    val cohortReady = visibleIndices.isNotEmpty() && cohort.all { gate.ready[it.stableKey()] == true }
    SideEffect { if (!gate.revealed && cohortReady) gate.revealed = true }

    val rankingFont = if (rankingLayout != null) legacyRankingFont() else null
    val ranks = remember(entries, rankingLayout != null) {
        if (rankingLayout == null) emptyMap() else entries.mapIndexed { index, item ->
            item.stableKey() to (item.catalogRank?.takeIf { it > 0 } ?: index + 1)
        }.toMap()
    }
    CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared, LocalLandscapeRowReveal provides gate) {
    NuvioShelfSection(
        title = section.title,
        state = rowState,
        entries = entries,
        modifier = if (rankingLayout == null) modifier else modifier.padding(bottom = rankingLayout.bottomGap),
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        itemSpacing = rankingLayout?.gap ?: 10.dp,
        trailingContent = if (rankingLayout != null && rankingFont != null && onViewAllClick != null) {
            { NumberedRankingViewAll(rankingLayout, rankingFont, onViewAllClick) }
        } else null,
        onViewAllClick = onViewAllClick,
        onTitleClick = onViewAllClick?.takeIf { isDesktop },
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        key = { item -> item.stableKey() },
    ) { item ->
        val watched = WatchingState.isPosterWatched(watchedKeys, item, fullyWatchedSeriesKeys)
        val openItem = onPosterClick?.let { { it(item) } }
        val openActions = onPosterLongClick?.let { { it(item) } }
        if (rankingLayout != null && rankingFont != null) {
            val key = item.stableKey()
            NumberedRankingItem(ranks.getValue(key), rankingLayout, rankingFont) {
                NuvioPosterCard(
                    title = item.name,
                    imageUrl = item.poster,
                    fallbackImageUrl = item.rawPosterUrl,
                    modifier = Modifier.width(rankingLayout.posterWidth),
                    fillWidth = true,
                    shape = NuvioPosterShape.Poster,
                    artworkHeight = rankingLayout.posterHeight,
                    artworkCornerRadius = rankingLayout.posterCorner,
                    artworkFocusRing = rankingLayout.focusRing,
                    showTitleBelow = false,
                    revealArtworkTogether = true,
                    mediaIdentity = key,
                    showArtwork = gate.revealed && prepared.mayReveal(key),
                    onArtworkReadyChanged = { gate.report(key, it) },
                    isWatched = watched,
                    onClick = openItem,
                    onLongClick = openActions,
                )
            }
        } else HomePosterCard(
            item = rowStyle.artworkItem(item),
            allowPortraitFallback = allowPortraitFallback,
            enhanceLandscapeTitles = !excluded,
            rowStyle = rowStyle,
            useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
            isWatched = watched,
            onClick = openItem,
            onLongClick = openActions,
        )
    }
    }
}
