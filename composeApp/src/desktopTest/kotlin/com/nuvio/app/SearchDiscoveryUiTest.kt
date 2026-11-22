package com.nuvio.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import com.nuvio.app.features.search.LocalSearchDiscoverySession
import com.nuvio.app.features.search.SearchDiscoverySession
import androidx.compose.ui.test.ExperimentalTestApi
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.search.SearchDiscoveryUi
import com.nuvio.app.features.search.SearchDiscoveryShelvesUi
import com.nuvio.app.features.search.SearchScope
import com.nuvio.app.features.search.SearchShelfKey
import com.nuvio.app.features.search.SearchShelfSort
import com.nuvio.app.features.search.rememberSearchDiscoveryShelves
import com.nuvio.app.features.search.spotlight
import com.nuvio.app.features.search.SearchStreamingCountry
import com.nuvio.app.features.search.rememberSearchDiscovery
import com.nuvio.app.features.search.searchDiscoverySuggestions
import com.nuvio.app.features.tmdb.TmdbEntityRailPageResult
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.core.ui.DesktopEscapeShortcut
import com.nuvio.app.features.search.SearchHistoryRepository
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchDiscoveryUiTest {
    @Test
    fun aNewChipCancelsOldResultsAndPaginationCanRetry() = runDesktopComposeUiTest {
        val first = searchDiscoverySuggestions("comedy").first()
        val second = searchDiscoverySuggestions("korean").single()
        val pending = CompletableDeferred<Unit>()
        val mounted = mutableStateOf(true)
        val session = SearchDiscoverySession().apply { select(first) }
        var secondPageAttempts = 0
        lateinit var result: SearchDiscoveryUi
        setContent {
            CompositionLocalProvider(LocalSearchDiscoverySession provides session) {
                if (mounted.value) result = rememberSearchDiscovery(
                    selection = session.selection,
                    active = true,
                    hideUnplayable = false,
                    hideUnreleased = false,
                    loadPage = { television, page, chip, _, _ ->
                        if (television) null else {
                            if (chip == first) pending.await()
                            if (page == 2 && secondPageAttempts++ == 0) error("Temporary failure")
                            TmdbEntityRailPageResult(
                                listOf(MetaPreview("tmdb:$page", "movie", "${chip.name} $page")),
                                hasMore = page == 1,
                            )
                        }
                    },
                    filterResults = { it },
                )
            }
        }
        runOnIdle { session.select(second) }
        runOnIdle {
            assertEquals(listOf("Korean 1"), result.items.map { it.name })
            pending.complete(Unit)
            result.load()
        }
        runOnIdle {
            assertEquals(true, result.failed)
            assertEquals(listOf("Korean 1"), result.items.map { it.name })
            result.load()
        }
        runOnIdle {
            assertEquals(listOf("Korean 1", "Korean 2"), result.items.map { it.name })
            assertEquals(false, result.hasMore)
            mounted.value = false
        }
        runOnIdle { mounted.value = true }
        runOnIdle {
            assertEquals(second, session.selection)
            assertEquals(listOf("Korean 1", "Korean 2"), result.items.map { it.name })
            assertEquals(2, secondPageAttempts)
        }
    }

    @Test
    fun tagCarouselsHideEmptyOrFailedRailsAndFeatureTheTopTitle() = runDesktopComposeUiTest {
        val comedy = searchDiscoverySuggestions("comedy").first()
        val session = SearchDiscoverySession().apply { select(comedy) }
        lateinit var shelves: SearchDiscoveryShelvesUi
        setContent {
            CompositionLocalProvider(LocalSearchDiscoverySession provides session) {
                shelves = rememberSearchDiscoveryShelves(
                    selection = session.selection,
                    active = true,
                    hideUnplayable = false,
                    hideUnreleased = false,
                    loadRail = { key, _, _, _ ->
                        when {
                            key.sort == SearchShelfSort.TopRated && key.television -> error("TMDB down")
                            key.sort == SearchShelfSort.New && !key.television -> null
                            else -> TmdbEntityRailPageResult(
                                listOf(
                                    MetaPreview(
                                        "tmdb:${key.sort}-${key.television}",
                                        if (key.television) "series" else "movie",
                                        "${key.sort} ${if (key.television) "show" else "movie"}",
                                        popularity = if (key.television) 90.0 else 40.0,
                                    ),
                                ),
                                hasMore = true,
                            )
                        }
                    },
                    filterResults = { it },
                )
            }
        }
        runOnIdle {
            assertEquals(false, shelves.loading)
            // One failed rail and one rail with no query don't fail the page; they're just hidden.
            assertEquals(false, shelves.failed)
            assertEquals(emptyList(), shelves.rails[SearchShelfKey(SearchShelfSort.TopRated, television = true)])
            assertEquals(emptyList(), shelves.rails[SearchShelfKey(SearchShelfSort.New, television = false)])
            assertEquals(listOf("Popular show"), shelves.rails[SearchShelfKey(SearchShelfSort.Popular, television = true)]?.map { it.name })
            // The spotlight is the most popular title for the active tab.
            assertEquals("Popular show", shelves.spotlight(SearchScope.All)?.name)
            assertEquals("Popular movie", shelves.spotlight(SearchScope.Movies)?.name)
        }
    }

    @Test
    fun streamingCountryUsesOverrideOrSupportedDeviceRegion() {
        val regions = mapOf("LT" to "Lithuania", "US" to "United States")
        assertEquals("LT", SearchStreamingCountry.effective(null, "LT", regions))
        assertEquals("US", SearchStreamingCountry.effective("US", "LT", regions))
        assertEquals(null, SearchStreamingCountry.effective(null, null, regions))
        assertEquals(null, SearchStreamingCountry.effective(null, "FR", regions))
    }

    @Test
    fun searchEscapeAndClearHistoryUseTheActiveSearchScreen() = runDesktopComposeUiTest(width = 1200, height = 850) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        SearchHistoryRepository.clearHistory()
        SearchHistoryRepository.recordSearch("Earlier search")
        setContent {
            AppEnvironment {
                MainAppContent(initialTab = AppScreenTab.Search, ownsAppRuntime = false, showLaunchOverlay = false)
            }
        }
        // Desktop renders the redesigned Search (SearchDesktopContent): its own placeholder and layout.
        val field = onNodeWithText("Search movies, shows, and people")
        field.performClick()
        onNode(hasSetTextAction()).performTextInput("apple")
        onNodeWithText("Apple TV").assertIsDisplayed()
        runOnIdle { checkNotNull(DesktopEscapeShortcut.onBack).invoke() }
        field.assertIsDisplayed()
        onNodeWithText("Earlier search").assertIsDisplayed()
        runOnIdle { checkNotNull(DesktopEscapeShortcut.onBack).invoke() }
        onNodeWithText("Earlier search").assertDoesNotExist()
        field.performClick()
        // The redesign shows recents as chips with "Clear all" in the section header, so the old
        // check that Clear all lines up with the per-row remove buttons no longer applies.
        val clearAll = onNodeWithContentDescription("Clear all recent searches")
        clearAll.assertIsDisplayed()
        onNodeWithContentDescription("Remove recent search").assertIsDisplayed()
        val captures = File("build/reports/search-discovery-review").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", captures.resolve("history.png"))
        clearAll.performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Earlier search").assertIsDisplayed()
        onNodeWithContentDescription("Clear all recent searches").performClick()
        onNodeWithText("Clear all").performClick()
        onNodeWithText("Earlier search").assertDoesNotExist()
        runOnIdle {
            SearchHistoryRepository.onProfileChanged()
            assertEquals(emptyList(), SearchHistoryRepository.uiState.value)
        }
        onNode(hasSetTextAction()).performTextInput("com")
        onNodeWithText("Comedy").assertIsDisplayed()
        onNodeWithText("Dark comedy").assertIsDisplayed()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", captures.resolve("comedy.png"))
        onNode(hasSetTextAction()).performTextReplacement("sci-fi")
        onNodeWithText("Time travel").assertIsDisplayed()
        onNodeWithText("Alien invasion").assertIsDisplayed()
        onNodeWithText("Topic: Time travel").assertDoesNotExist()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", captures.resolve("subgenres.png"))
    }
}
