package com.nuvio.app.features.home.components

import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
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
    eyebrow: String? = null,
    leadCard: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val calendar = section.calendarState
    // Quick look's ← → walks this row.
    androidx.compose.runtime.CompositionLocalProvider(LocalPosterQuickLookRow provides entries) {
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
            eyebrow = eyebrow,
            leadCard = leadCard,
            rankingLayout = if (rowStyle == HomeRowStyle.NumberedRanking) NumberedRankingLayout(maxWidth) else null,
        )
    }
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
    eyebrow: String?,
    leadCard: Boolean,
    rankingLayout: NumberedRankingLayout?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    // Desktop Home: a plain poster row opens with a wide card for its first title.
    val leadItem = entries.firstOrNull()?.takeIf {
        leadCard && rankingLayout == null && rowStyle == HomeRowStyle.Automatic &&
            !posterCardStyle.catalogLandscapeModeEnabled && it.calendarCard == null &&
            it.posterShape == PosterShape.Poster
    }
    // The shelf remembers its entries by equality, so it can hand back older but equal instances: match by key.
    val leadKey = leadItem?.stableKey()
    val leadHeight = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp).dp *
        (posterCardStyle.heightDp.toFloat() / posterCardStyle.widthDp.coerceAtLeast(1))
    val rowState = rememberLazyListState()
    val excluded = preservesOriginalLandscapeArtwork(section.title, section.target) || entries.any { it.calendarCard != null }

    val rankingFont = if (rankingLayout != null) legacyRankingFont() else null
    val ranks = remember(entries, rankingLayout != null) {
        if (rankingLayout == null) emptyMap() else entries.mapIndexed { index, item ->
            item.stableKey() to (item.catalogRank?.takeIf { it > 0 } ?: index + 1)
        }.toMap()
    }
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
        eyebrow = eyebrow,
        key = { item -> item.stableKey() },
    ) { item ->
        val watched = WatchingState.isPosterWatched(watchedKeys, item, fullyWatchedSeriesKeys)
        val openItem = onPosterClick?.let { { it(item) } }
        val openActions = onPosterLongClick?.let { { it(item) } }
        if (rankingLayout != null && rankingFont != null) {
            val key = item.stableKey()
            NumberedRankingItem(ranks.getValue(key), rankingLayout, rankingFont) {
                // The rank's lane has a fixed width, so the poster keeps the strip under Inline expand.
                HomePosterHoverPreview(
                    item = item,
                    isWatched = watched,
                    onClick = openItem,
                    onLongClick = openActions,
                    allowInlineExpand = false,
                ) { hoverModifier, hoverOverlay ->
                    NuvioPosterCard(
                        title = item.name,
                        imageUrl = item.poster,
                        fallbackImageUrl = item.rawPosterUrl,
                        modifier = Modifier.width(rankingLayout.posterWidth).then(hoverModifier),
                        fillWidth = true,
                        shape = NuvioPosterShape.Poster,
                        artworkHeight = rankingLayout.posterHeight,
                        artworkCornerRadius = rankingLayout.posterCorner,
                        artworkFocusRing = rankingLayout.focusRing,
                        showTitleBelow = false,
                        isWatched = watched,
                        onClick = openItem,
                        onLongClick = openActions,
                        artworkOverlay = hoverOverlay,
                    )
                }
            }
        } else if (leadKey != null && item.stableKey() == leadKey) {
            HomeRowLeadCard(item = item, width = leadHeight * (16f / 9f), height = leadHeight, onClick = openItem, onLongClick = openActions)
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
