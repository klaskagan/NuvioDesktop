package com.nuvio.app.features.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

    /**
     * The service page as Details: Back in the hero's top-left corner, the tabs row right under the
     * hero, and that row pinned at the list's top once the hero has scrolled away.
     */
    @Test
    fun heroCarriesBackAndTheTabsRowPinsUnderIt() = runDesktopComposeUiTest(width = 1400, height = 1000) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val netflix = searchDiscoverySuggestions("Netflix").first { it.name == "Netflix" }
        val chart = SearchTopTenCatalog(
            SearchAddonCatalog("https://addon.example/manifest.json", "Addon", "series", "flixpatrol.netflix.gb.x", "Top 10"),
            true,
        )
        val section = HomeCatalogSection(
            key = "top", title = "Top 10", subtitle = "", addonName = "",
            target = CatalogTarget.Addon("https://addon.example/manifest.json", "series", "top"),
            items = (1..10).map { MetaPreview("tmdb:$it", "series", "Show $it") },
        )
        val rails = listOf(true, false).flatMap { tv ->
            SearchShelfSort.entries.map { sort -> SearchShelfKey(sort, tv) to (1..10).map { MetaPreview("tmdb:$sort$tv$it", if (tv) "series" else "movie", "Title $it") } }
        }.toMap()
        var backs = 0
        val listState = androidx.compose.foundation.lazy.LazyListState()
        mainClock.autoAdvance = false
        setContent {
            AppEnvironment {
                LazyColumn(Modifier.fillMaxSize().testTag("tag-list"), state = listState) {
                    streamingProviderPage(
                        selection = netflix,
                        shelves = SearchDiscoveryShelvesUi(rails = rails),
                        topTen = SearchTopTenUi(sections = listOf(chart to section)),
                        collectionRows = emptyList(),
                        layout = StreamingRowLayout(),
                        heroMeta = HomeHeroMetaStore(),
                        scope = SearchScope.All,
                        gutter = 24.dp,
                        itemModifier = { Modifier },
                        watchedKeys = emptySet(),
                        fullyWatchedSeriesKeys = emptySet(),
                        onPosterClick = null,
                        onPosterLongClick = null,
                        onViewAll = {},
                        heroTopPadding = 0.dp,
                        onHeroBack = { backs++ },
                        tabsBar = { TagScopeTabs(SearchScope.All, {}, Modifier.testTag("tabs-row")) },
                    )
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        val hero = onNodeWithTag("tag-hero").fetchSemanticsNode().boundsInRoot
        val back = onNodeWithTag("tag_hero_back").fetchSemanticsNode().boundsInRoot
        assertTrue(back.left - hero.left < 30f && back.top - hero.top < 30f, "Back should sit in the hero's top-left corner: hero=$hero back=$back")
        onNodeWithTag("tag_hero_back").performClick()
        runOnIdle { kotlin.test.assertEquals(1, backs) }
        val tabs = onNodeWithTag("tabs-row").fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.top >= hero.bottom - 1f && tabs.top - hero.bottom < 80f, "Tabs should sit right under the hero: hero=$hero tabs=$tabs")

        // Scrolled well past the hero, the tabs row stays at the list's top.
        runOnIdle { listState.requestScrollToItem(4) }
        mainClock.advanceTimeBy(500)
        val list = onNodeWithTag("tag-list").fetchSemanticsNode().boundsInRoot
        val pinned = onNodeWithTag("tabs-row").fetchSemanticsNode().boundsInRoot
        assertTrue(pinned.top >= list.top - 1f && pinned.bottom <= list.top + 80f, "Tabs should pin at the top: list=$list tabs=$pinned")
    }

    /**
     * Crunchyroll has many shows and few movies. Picking Movies from the pinned row used to let the
     * list scroll back (the short tab couldn't fill the window), so the row lost its frosted surface,
     * Back and logo. Like Details, a filler and a short hold keep it pinned on every frame.
     */
    @Test
    fun aShortTabPickedFromThePinnedRowKeepsItPinned() = runDesktopComposeUiTest(width = 1400, height = 1000) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val service = searchDiscoverySuggestions("Netflix").first { it.name == "Netflix" }
        val chart = SearchTopTenCatalog(
            SearchAddonCatalog("https://addon.example/manifest.json", "Addon", "series", "flixpatrol.netflix.gb.x", "Top 10"),
            true,
        )
        val section = HomeCatalogSection(
            key = "top", title = "Top 10", subtitle = "", addonName = "",
            target = CatalogTarget.Addon("https://addon.example/manifest.json", "series", "top"),
            items = (1..10).map { MetaPreview("tmdb:s$it", "series", "Show $it") },
        )
        // Many show rows, one short movie row.
        val rails = SearchShelfSort.entries.associate { sort ->
            SearchShelfKey(sort, true) to (1..10).map { MetaPreview("tmdb:$sort$it", "series", "Show $sort $it") }
        } + (SearchShelfKey(SearchShelfSort.Popular, false) to (1..3).map { MetaPreview("tmdb:m$it", "movie", "Movie $it") })
        val listState = androidx.compose.foundation.lazy.LazyListState()
        var pin: TagTabsPinState? = null
        mainClock.autoAdvance = false
        setContent {
            AppEnvironment {
                var scope by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SearchScope.All) }
                val tabsPin = rememberTagTabsPinState(listState, "test").also { pin = it }
                TagTabsSettleEffect(tabsPin, scope)
                LazyColumn(Modifier.fillMaxSize().testTag("tag-list"), state = listState) {
                    streamingProviderPage(
                        selection = service,
                        shelves = SearchDiscoveryShelvesUi(rails = rails),
                        topTen = SearchTopTenUi(sections = listOf(chart to section)),
                        collectionRows = emptyList(),
                        layout = StreamingRowLayout(),
                        heroMeta = HomeHeroMetaStore(),
                        scope = scope,
                        gutter = 24.dp,
                        itemModifier = { Modifier },
                        watchedKeys = emptySet(),
                        fullyWatchedSeriesKeys = emptySet(),
                        onPosterClick = null,
                        onPosterLongClick = null,
                        onViewAll = {},
                        heroTopPadding = 0.dp,
                        tabsBar = {
                            TagScopeTabs(scope, { next -> if (next != scope) tabsPin.beforeTabChange(); scope = next }, Modifier.testTag("tabs-row"))
                        },
                        listState = listState,
                        tabsSettling = { tabsPin.holding },
                    )
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        // Scroll the hero away so the row pins.
        runOnIdle { listState.requestScrollToItem(4) }
        mainClock.advanceTimeBy(500)
        val list = onNodeWithTag("tag-list").fetchSemanticsNode().boundsInRoot
        assertTrue(checkNotNull(pin).pinned, "The row should be pinned before the switch")

        onNode(hasText("Movies") and hasAnyAncestor(hasTestTag("tabs-row"))).performClick()
        repeat(30) { frame ->
            mainClock.advanceTimeByFrame()
            val tabs = onNodeWithTag("tabs-row").fetchSemanticsNode().boundsInRoot
            assertTrue(checkNotNull(pin).pinned, "Frame $frame: the row dropped out of its pinned state")
            assertTrue(kotlin.math.abs(tabs.top - list.top) < 80f, "Frame $frame: the row left the top: list=$list tabs=$tabs")
        }
        mainClock.advanceTimeBy(1_000)
        assertTrue(checkNotNull(pin).pinned, "After settling the row should stay pinned")
        assertTrue(!checkNotNull(pin).holding, "The hold should end once the rows settle")
        onNodeWithTag("tag-tabs-fill").assertExists()
    }
}
