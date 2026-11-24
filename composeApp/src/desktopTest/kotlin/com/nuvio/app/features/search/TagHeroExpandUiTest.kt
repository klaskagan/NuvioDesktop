package com.nuvio.app.features.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeHeroMetaStore
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.homeBoardHeroHeight
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The service/tag page hero's corner control expands the card like Home's board tile. It used
 * to fall back to the hero's old window-fullscreen toggle.
 */
@OptIn(ExperimentalTestApi::class)
class TagHeroExpandUiTest {
    @Test
    fun cornerControlExpandsTheCardAndEscCollapsesIt() = runDesktopComposeUiTest(width = 1400, height = 1000) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val netflix = searchDiscoverySuggestions("Netflix").first { it.name == "Netflix" }
        val chart = SearchTopTenCatalog(
            SearchAddonCatalog("https://addon.example/manifest.json", "Addon", "series", "flixpatrol.netflix.gb.x", "Top 10"),
            true,
        )
        val section = HomeCatalogSection(
            key = "top", title = "Top 10", subtitle = "", addonName = "",
            target = CatalogTarget.Addon("https://addon.example/manifest.json", "series", "top"),
            items = listOf(MetaPreview("tmdb:hit", "series", "Hit Show"), MetaPreview("tmdb:next", "series", "Next Show")),
        )
        val heroMeta = HomeHeroMetaStore()
        // AppEnvironment applies the app's UI scale, so expected sizes use its density.
        var appDensity: androidx.compose.ui.unit.Density? = null
        var windowHeightDp = 0.dp
        // The hero's carousel counts down forever, so the test drives the clock itself.
        mainClock.autoAdvance = false
        try {
            setContent {
                AppEnvironment {
                    val local = androidx.compose.ui.platform.LocalDensity.current
                    appDensity = local
                    windowHeightDp = with(local) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        streamingProviderPage(
                            selection = netflix,
                            shelves = SearchDiscoveryShelvesUi(),
                            topTen = SearchTopTenUi(sections = listOf(chart to section)),
                            collectionRows = emptyList(),
                            layout = StreamingRowLayout(),
                            heroMeta = heroMeta,
                            scope = SearchScope.All,
                            gutter = 24.dp,
                            // Like the real pages: rows animate their placement.
                            itemModifier = { Modifier.animateItem(placementSpec = tagRowPlacementSpec()) },
                            watchedKeys = emptySet(),
                            fullyWatchedSeriesKeys = emptySet(),
                            onPosterClick = null,
                            onPosterLongClick = null,
                            onViewAll = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            onNodeWithTag("tag-hero").assertExists()
            val collapsed = onNodeWithTag("tag-hero").fetchSemanticsNode().boundsInRoot.height
            // It starts at Home's My lists hero size for this window.
            val homeCollapsed = with(checkNotNull(appDensity)) { homeBoardHeroHeight(windowHeightDp, false).toPx() }
            assertTrue(kotlin.math.abs(collapsed - homeCollapsed) <= 1f, "Expected Home's collapsed height; tag=$collapsed home=$homeCollapsed")
            onNodeWithContentDescription("Expand and play trailer").performClick()
            // Mid-animation the Top 10 row stays below the growing hero; it used to lag and run under it.
            repeat(6) {
                mainClock.advanceTimeBy(50)
                val hero = onNodeWithTag("tag-hero").fetchSemanticsNode().boundsInRoot
                val row = onAllNodesWithText("Top 10", substring = true).fetchSemanticsNodes().first().boundsInRoot
                assertTrue(row.top >= hero.bottom - 1f, "Top 10 overlapped the expanding hero: hero=$hero row=$row")
            }
            mainClock.advanceTimeBy(800)
            val expanded = onNodeWithTag("tag-hero").fetchSemanticsNode().boundsInRoot.height
            // From 640 dp the card grew a few pixels; from Home's height it grows clearly.
            assertTrue(expanded > collapsed + 200f, "Expected the card to grow clearly; collapsed=$collapsed expanded=$expanded")
            onNodeWithContentDescription("Collapse").assertExists()

            // Esc collapses the card instead of leaving the page.
            runOnIdle { assertTrue(AppKeyboardShortcuts.overlayEscape?.invoke() == true) }
            mainClock.advanceTimeBy(800)
            val back = onNodeWithTag("tag-hero").fetchSemanticsNode().boundsInRoot.height
            assertTrue(kotlin.math.abs(back - collapsed) <= 1f, "Expected the card to collapse; collapsed=$collapsed now=$back")
            onNodeWithContentDescription("Expand and play trailer").assertExists()
        } finally {
            AppKeyboardShortcuts.clearOverlayEscapes()
        }
    }
}
