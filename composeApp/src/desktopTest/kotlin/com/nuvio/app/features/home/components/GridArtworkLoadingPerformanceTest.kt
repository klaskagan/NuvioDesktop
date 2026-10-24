package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.features.catalog.*
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.*
import com.nuvio.app.features.search.DiscoverUiState
import com.nuvio.app.features.search.discoverContent
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GridArtworkLoadingPerformanceTest {
    private fun artwork(): String {
        val real = File("/tmp/nuvio-landscape-art/dune.jpg")
        if (real.isFile) return real.toURI().toString()
        val file = File.createTempFile("grid-prefetch-", ".png").apply { deleteOnExit() }
        val image = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = java.awt.Color(60, 90, 120); fillRect(0, 0, 320, 180); dispose() }
        ImageIO.write(image, "png", file)
        return file.toURI().toString()
    }
    private fun prepare() {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        PosterCardStyleRepository.setWidthDp(100)
    }
    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/grid-loading-performance").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    @Test fun realCatalogGridRevealsWholeRowsAndPreparesAheadWithoutFetchingEntireCatalog() = runDesktopComposeUiTest(width = 1000, height = 380) {
        prepare()
        val art = artwork()
        val slow = CompletableDeferred<Unit>()
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        var renderedGrid: androidx.compose.foundation.lazy.grid.LazyGridState? = null
        val entries = (0 until 80).map { MetaPreview("grid-$it", "movie", "Movie $it", banner = art) }
        setContent { AppEnvironment { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { id, _, _ ->
            calls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
            if (id == "grid-1") slow.await()
            TmdbLandscapeArtwork(art, null, art)
        }) {
            val grid = rememberLazyGridState()
            SideEffect { renderedGrid = grid }
            CatalogContent(title = "Decade fixture", subtitle = "", target = CatalogTarget.Addon("https://fixture.invalid/manifest.json", "movie", "decade"),
                uiState = CatalogUiState(items = entries), gridState = grid, watchedKeys = emptySet(), fullyWatchedSeriesKeys = emptySet(),
                networkCondition = NetworkCondition.Online, onBack = {}, onRetry = {})
        } } }
        try {
            waitUntil(timeoutMillis = 10_000) { calls.containsKey("grid-9") }
            var firstRow = emptyList<Int>()
            runOnIdle { firstRow = checkNotNull(renderedGrid).layoutInfo.visibleItemsInfo.filter { it.row == 0 }.map { it.index } }
            assertEquals(4, firstRow.size, "The readiness cohort must match the actual pixel-rounded grid")
            for (index in firstRow) onAllNodesWithContentDescription("Movie $index").assertCountEquals(0)
            save("catalog-row-pending")
            runOnIdle { slow.complete(Unit) }
            waitUntil(timeoutMillis = 10_000) { firstRow.all { onAllNodesWithContentDescription("Movie $it").fetchSemanticsNodes().isNotEmpty() } }
            save("catalog-row-ready")
            // Rows ahead of the viewport have metadata, while most of the catalog is untouched.
            assertTrue(calls.size < 30, "Bound prefetch to nearby rows, got ${calls.size}/80")
            onNodeWithTag("catalog_grid").performScrollToIndex(9)
            waitUntil(timeoutMillis = 10_000) { (9..11).all { onAllNodesWithContentDescription("Movie $it").fetchSemanticsNodes().isNotEmpty() } }
            save("catalog-prefetched-scroll")
            assertTrue(calls.values.all { it.get() == 1 }, "Cards and prefetch must share work: ${calls.mapValues { it.value.get() }}")
        } finally { slow.complete(Unit) }
    }

    @Test fun realDiscoverRowsUseListPrefetchAndRevealTogether() = runDesktopComposeUiTest(width = 1000, height = 420) {
        prepare()
        val art = artwork()
        val slow = CompletableDeferred<Unit>()
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val entries = (0 until 60).map { MetaPreview("discover-$it", "series", "Series $it", banner = art) }
        setContent { AppEnvironment { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { id, _, _ ->
            calls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
            if (id == "discover-2") slow.await()
            TmdbLandscapeArtwork(art, null, art)
        }) {
            val state = rememberLazyListState()
            val rows = remember(entries) { entries.chunked(3).mapIndexed { index, items -> ArtworkPrefetchRow("discover-row:$index", items) } }
            val prepared = rememberListArtworkPrefetch(state, rows, "discover-fixture")
            CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("discover_rows")) {
                    discoverContent(DiscoverUiState(items = entries), false, 3, NetworkCondition.Online, {}, {}, {})
                }
            }
        } } }
        try {
            waitUntil(timeoutMillis = 10_000) { calls.containsKey("discover-8") }
            for (index in 0..2) onAllNodesWithContentDescription("Series $index").assertCountEquals(0)
            save("discover-row-pending")
            runOnIdle { slow.complete(Unit) }
            waitUntil(timeoutMillis = 10_000) { (0..2).all { onAllNodesWithContentDescription("Series $it").fetchSemanticsNodes().isNotEmpty() } }
            save("discover-row-ready")
            assertTrue(calls.size < 30)
            // Two headers precede the explicitly keyed grid rows.
            onNodeWithTag("discover_rows").performScrollToIndex(5)
            waitUntil(timeoutMillis = 10_000) { (9..11).all { onAllNodesWithContentDescription("Series $it").fetchSemanticsNodes().isNotEmpty() } }
            save("discover-prefetched-scroll")
            assertTrue(calls.values.all { it.get() == 1 }, "Duplicate metadata calls: ${calls.mapValues { it.value.get() }}")
        } finally { slow.complete(Unit) }
    }
    @Test fun trackingLibraryRowsUseSharedPrefetchAndRowReveal() = runDesktopComposeUiTest(width = 1000, height = 420) {
        prepare()
        val art = artwork()
        val slow = CompletableDeferred<Unit>()
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val items = (0 until 60).map { LibraryItem("library-$it", "movie", "Library $it", banner = art, savedAtEpochMs = it.toLong()) }
        val section = LibrarySection("watchlist", "Watchlist", items)
        val projection = LibraryVerticalProjection(listOf(section), "watchlist", listOf("movie"), null, items.map { LibraryVerticalEntry(it, section) })
        setContent { AppEnvironment { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { id, _, _ ->
            calls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
            if (id == "library-1") slow.await()
            TmdbLandscapeArtwork(art, null, art)
        }) {
            val state = rememberLazyListState()
            val rows = remember(projection) { projection.entries.chunked(3).map { entries ->
                val first = entries.first().item
                ArtworkPrefetchRow("library-vertical:${first.type}:${first.id}", entries.map { it.item.toMetaPreview() })
            } }
            val prepared = rememberListArtworkPrefetch(state, rows, "library-fixture")
            CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("library_rows")) {
                    libraryVerticalContent(projection, 3, emptySet(), emptySet(), "", null, null)
                }
            }
        } } }
        try {
            waitUntil(timeoutMillis = 10_000) { calls.containsKey("library-8") }
            for (index in 0..2) onAllNodesWithContentDescription("Library $index").assertCountEquals(0)
            save("library-row-pending")
            runOnIdle { slow.complete(Unit) }
            waitUntil(timeoutMillis = 10_000) { (0..2).all { onAllNodesWithContentDescription("Library $it").fetchSemanticsNodes().isNotEmpty() } }
            save("library-row-ready")
            assertTrue(calls.size < 30)
            onNodeWithTag("library_rows").performScrollToIndex(3)
            waitUntil(timeoutMillis = 10_000) { (9..11).all { onAllNodesWithContentDescription("Library $it").fetchSemanticsNodes().isNotEmpty() } }
            save("library-prefetched-scroll")
            assertTrue(calls.values.all { it.get() == 1 }, "Duplicate metadata calls: ${calls.mapValues { it.value.get() }}")
        } finally { slow.complete(Unit) }
    }

}
