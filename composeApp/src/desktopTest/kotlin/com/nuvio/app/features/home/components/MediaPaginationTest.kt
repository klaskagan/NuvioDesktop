package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.catalog.CatalogContent
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.CatalogUiState
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MediaPaginationTest {
    private fun rows(count: Int) = (0 until count).map { index ->
        ArtworkPrefetchRow("media:$index", listOf(MetaPreview("fixture-$index", "movie", "Movie $index")))
    }

    @Test fun largerAdaptiveWindowRequestsBeforeTheOldBoundaryAndHonorsLoading() = runDesktopComposeUiTest(width = 800, height = 180) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val mediaRows = rows(10)
        val window = MediaPrefetchWindow()
        var loading by mutableStateOf(false)
        var calls = 0
        setContent {
            AppEnvironment {
                val state = rememberLazyListState()
                MediaPagePrefetchEffect(mediaRows, window, state, "fixture", canLoadMore = true,
                    isLoading = loading, onLoadMore = { calls++; loading = true },
                    visibleKeys = { state.layoutInfo.visibleItemsInfo.map { it.key } })
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("media_pages")) {
                    item(key = "header") { Box(Modifier.height(100.dp)) { Text("Header") } }
                    item(key = "filters") { Box(Modifier.height(100.dp)) { Text("Filters") } }
                    items(mediaRows, key = { it.key }) { row -> Box(Modifier.height(100.dp)) { Text(row.key.toString()) } }
                    item(key = "footer") { Box(Modifier.height(100.dp)) { Text("Footer") } }
                }
            }
        }
        waitForIdle()
        assertEquals(0, calls)
        onNodeWithTag("media_pages").performScrollToIndex(5) // Media row 3, not layout index 5.
        waitForIdle()
        assertEquals(0, calls, "Two-row lookahead must not count headers as media")
        runOnIdle {
            loading = true
            window.observe(0.0, 0, 1000)
            window.observe(2.0, 100, 1000)
        }
        waitForIdle()
        assertEquals(6, window.aheadRows)
        assertEquals(0, calls, "An in-flight page must not be requested again")
        runOnIdle { loading = false }
        waitUntil(timeoutMillis = 5_000) { calls == 1 }
        waitForIdle()
        assertEquals(1, calls)
    }

    @Test fun backwardTravelInactiveScreensAndExhaustedSourcesDoNotRequestPages() = runDesktopComposeUiTest(width = 700, height = 180) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val mediaRows = rows(4)
        val window = MediaPrefetchWindow().apply {
            observe(4.0, 0, 1000)
            observe(3.0, 100, 1000)
        }
        var active by mutableStateOf(true)
        var canLoad by mutableStateOf(true)
        var calls = 0
        setContent {
            AppEnvironment {
                CompositionLocalProvider(LocalScreenActive provides active) {
                    val state = rememberLazyListState()
                    MediaPagePrefetchEffect(mediaRows, window, state, "guards", canLoadMore = canLoad,
                        isLoading = false, onLoadMore = { calls++ },
                        visibleKeys = { state.layoutInfo.visibleItemsInfo.map { it.key } })
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("guard_pages")) {
                        items(mediaRows, key = { it.key }) { row -> Box(Modifier.height(100.dp)) { Text(row.key.toString()) } }
                    }
                }
            }
        }
        onNodeWithTag("guard_pages").performScrollToIndex(2)
        waitForIdle()
        assertEquals(0, calls, "Reverse travel must not speculatively retrieve forward pages")
        runOnIdle { active = false; window.observe(4.0, 200, 1000) }
        waitForIdle()
        assertEquals(0, calls)
        runOnIdle { canLoad = false; active = true }
        waitForIdle()
        assertEquals(0, calls)
        runOnIdle { canLoad = true }
        waitUntil(timeoutMillis = 5_000) { calls == 1 }
    }

    @Test fun fullyFilteredPageAdvancesItsCursorWithoutVisibleMedia() = runDesktopComposeUiTest(width = 700, height = 180) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val window = MediaPrefetchWindow()
        var canLoad by mutableStateOf(true)
        var calls = 0
        setContent {
            AppEnvironment {
                MediaPagePrefetchEffect(emptyList(), window, "empty-grid", "filtered-page", canLoadMore = canLoad,
                    isLoading = false, onLoadMore = { calls++; canLoad = false },
                    visibleKeys = { listOf("filters", "empty-state") })
                Text("No released titles in this provider page")
            }
        }
        waitUntil(timeoutMillis = 5_000) { calls == 1 }
        waitForIdle()
        assertEquals(1, calls, "An exhausted source must stop after advancing the fully filtered page")
    }

    @Test fun catalogContentMapsItsRealGridToTheSharedPageTrigger() = runDesktopComposeUiTest(width = 1000, height = 500) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        PosterCardStyleRepository.setWidthDp(100)
        var state by mutableStateOf(CatalogUiState(
            items = (0 until 90).map { MetaPreview("page-$it", "movie", "Movie $it") }, nextSkip = 90))
        var calls = 0
        setContent {
            AppEnvironment {
                CatalogContent("Pagination fixture", "", CatalogTarget.Addon("https://fixture.invalid", "movie", "all"),
                    state, rememberLazyGridState(), emptySet(), emptySet(), NetworkCondition.Online,
                    onBack = {}, onRetry = {}, onLoadMore = { calls++; state = state.copy(isLoading = true) })
            }
        }
        waitForIdle()
        assertEquals(0, calls)
        onNodeWithTag("catalog_grid").performScrollToIndex(85)
        waitUntil(timeoutMillis = 5_000) { calls == 1 }
        waitForIdle()
        assertEquals(1, calls)
    }
}
