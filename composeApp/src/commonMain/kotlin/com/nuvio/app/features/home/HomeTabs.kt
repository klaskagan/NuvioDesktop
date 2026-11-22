package com.nuvio.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignTabs
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.watching.domain.isSeriesLikeWatchingContentType
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_tab_for_you
import nuvio.composeapp.generated.resources.home_tab_movies
import nuvio.composeapp.generated.resources.home_tab_my_lists
import nuvio.composeapp.generated.resources.home_tab_shows
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Desktop Home's filter tabs. They narrow the rows the user already configured; nothing is refetched. */
internal enum class HomeTab(val labelRes: StringResource) {
    ForYou(Res.string.home_tab_for_you),
    Movies(Res.string.home_tab_movies),
    Shows(Res.string.home_tab_shows),
    MyLists(Res.string.home_tab_my_lists),
}

private fun String.homeMediaTab(): HomeTab? = when {
    equals("movie", ignoreCase = true) -> HomeTab.Movies
    isSeriesLikeWatchingContentType() -> HomeTab.Shows
    else -> null
}

/** Movies or Shows from the catalog's own type; a mixed or untyped catalog goes by most of its items. */
internal fun HomeCatalogSection.homeMediaTab(): HomeTab? =
    target.contentType.homeMediaTab()
        ?: items.mapNotNull { it.type.homeMediaTab() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

internal fun HomeCatalogSection.belongsTo(tab: HomeTab): Boolean = when (tab) {
    HomeTab.ForYou -> true
    HomeTab.Movies, HomeTab.Shows -> target !is CatalogTarget.Seasonal && homeMediaTab() == tab
    HomeTab.MyLists -> target is CatalogTarget.Library || target is CatalogTarget.CollectionSource
}

internal fun ContinueWatchingItem.belongsTo(tab: HomeTab): Boolean = when (tab) {
    HomeTab.ForYou -> true
    HomeTab.Movies, HomeTab.Shows -> parentMetaType.homeMediaTab() == tab
    HomeTab.MyLists -> false
}

/** The user's collection rows show on For you and My lists; the seasonal spotlight on For you only. */
internal fun HomeTab.showsCollections(): Boolean = this == HomeTab.ForYou || this == HomeTab.MyLists
internal fun HomeTab.showsSeasonalSpotlight(): Boolean = this == HomeTab.ForYou

internal const val HOME_LEAD_RANKING_SIZE = 10

/**
 * The row that leads a tab as a numbered ranking: the first addon catalog in the user's order with
 * at least ten titles, unless the user already chose a row style for it. The numbers are the
 * catalog's own order, so its title is kept.
 */
internal fun homeLeadRankingKey(
    tab: HomeTab,
    orderedSections: List<HomeCatalogSection>,
    rowStyles: Map<String, HomeRowStyle>,
): String? {
    if (tab == HomeTab.MyLists) return null
    val lead = orderedSections.firstOrNull { section ->
        section.target is CatalogTarget.Addon && section.calendarState == null && section.belongsTo(tab) &&
            section.items.size >= HOME_LEAD_RANKING_SIZE && section.items.none { it.calendarCard != null }
    } ?: return null
    val chosen = rowStyles[lead.key] ?: HomeRowStyle.Automatic
    return lead.key.takeIf { chosen == HomeRowStyle.Automatic && supportsHomeRowStyle(lead.key) }
}

/**
 * The tab strip under the hero. It pins while the rows scroll: [pinnedColor] fades in only once
 * it is actually pinned, and [blendColor] softens the hero's solid lower edge into the
 * ambient backdrop while it is not.
 */
@Composable
internal fun HomeTabsBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    pinnedColor: Color,
    blendColor: Color,
    horizontalPadding: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (blendColor.alpha > 0f) {
                    // Starts above the bar so the list's gap after the hero is covered too.
                    val lead = 32.dp.toPx()
                    val height = 220.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to blendColor,
                            lead / (lead + height) to blendColor,
                            1f to Color.Transparent,
                            startY = -lead,
                            endY = height,
                        ),
                        topLeft = Offset(0f, -lead),
                        size = Size(size.width, lead + height),
                    )
                }
            },
    ) {
        Box(
            Modifier.matchParentSize()
                .background(pinnedColor)
                .nuvioConsumePointerEvents(),
        )
        if (pinnedColor.alpha > 0.5f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
        }
        RedesignTabs(
            items = HomeTab.entries,
            selected = selected,
            label = { stringResource(it.labelRes) },
            onSelect = onSelect,
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .padding(top = topInset + 18.dp, bottom = 16.dp)
                .testTag("home_tabs"),
        )
    }
}
