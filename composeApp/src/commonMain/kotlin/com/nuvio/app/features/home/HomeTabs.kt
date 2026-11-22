package com.nuvio.app.features.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.DesktopNavigationTabs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Which Home tab is showing. It lives outside Home because the desktop top bar shows these tabs on
 * every screen: picking one from Search or a detail page opens Home on it.
 */
internal object HomeTabSelection {
    var tab by mutableStateOf(HomeTab.ForYou)
}

/** Home's tabs as the top bar's leading entries; none is selected while another screen is showing. */
@Composable
internal fun rememberHomeNavigationTabs(homeSelected: Boolean, onOpenHome: () -> Unit): DesktopNavigationTabs {
    val labels = HomeTab.entries.map { stringResource(it.labelRes) }
    val selected = HomeTabSelection.tab
    val openHome by rememberUpdatedState(onOpenHome)
    return remember(labels, selected, homeSelected) {
        DesktopNavigationTabs(
            labels = labels,
            selectedIndex = selected.ordinal.takeIf { homeSelected },
            onSelect = { index ->
                HomeTabSelection.tab = HomeTab.entries[index]
                openHome()
            },
        )
    }
}

/** Desktop Home's filter tabs. They narrow the rows the user already configured; nothing is refetched. */
internal enum class HomeTab(val labelRes: StringResource) {
    ForYou(Res.string.home_tab_for_you),
    Movies(Res.string.home_tab_movies),
    Shows(Res.string.home_tab_shows),
    MyLists(Res.string.home_tab_my_lists),
}

internal fun String.homeMediaTab(): HomeTab? = when {
    equals("movie", ignoreCase = true) -> HomeTab.Movies
    isSeriesLikeWatchingContentType() -> HomeTab.Shows
    else -> null
}

/** How a tab's own hero catalogs are keyed in stored settings; null for tabs that share For you's hero. */
internal val HomeTab.heroSourceId: String?
    get() = when (this) {
        HomeTab.Movies -> "movies"
        HomeTab.Shows -> "shows"
        HomeTab.ForYou, HomeTab.MyLists -> null
    }

/** A Home catalog as the hero source rules see it, in the user's Home order. */
internal data class HomeHeroSourceCandidate(
    val key: String,
    val type: String,
    val enabled: Boolean,
    val forYouSource: Boolean,
    val supportsHero: Boolean = true,
)

/**
 * The catalogs that feed the Movies or Shows hero. The user's own picks for the tab win; with
 * none, the For you hero catalogs of that type are used; with none of those either, the tab's
 * first visible catalog. So a tab's hero always matches the tab without any setup.
 */
internal fun resolveTabHeroSourceKeys(
    tab: HomeTab,
    orderedCatalogs: List<HomeHeroSourceCandidate>,
    chosen: Collection<String>,
): List<String> {
    val eligible = orderedCatalogs.filter { it.supportsHero && it.type.homeMediaTab() == tab }
    val picked = eligible.filter { it.key in chosen }
    if (picked.isNotEmpty()) return picked.map { it.key }
    val shared = eligible.filter { it.forYouSource }
    if (shared.isNotEmpty()) return shared.map { it.key }
    return listOfNotNull(eligible.firstOrNull { it.enabled }?.key)
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

/**
 * Home's own tab strip, for the sidebar layout (the top-bar layout shows the tabs in the bar
 * itself). It stays pinned while the page scrolls; [pinnedColor] fades in only once content is
 * actually passing beneath it.
 */
@Composable
internal fun HomeTabsBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    pinnedColor: Color,
    horizontalPadding: Dp,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
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
