package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import kotlinx.coroutines.CompletableDeferred
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeArtworkLoadingPerformanceTest {
    private fun backdrop(): String {
        val real = File("/tmp/nuvio-landscape-art/dune.jpg")
        if (real.isFile) return real.toURI().toString()
        val file = File.createTempFile("home-loading-art-", ".png").apply { deleteOnExit() }
        val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = GradientPaint(0f, 0f, Color(30, 45, 60), 320f, 180f, Color(120, 100, 80))
            fillRect(0, 0, 320, 180)
            dispose()
        }
        ImageIO.write(image, "png", file)
        return file.toURI().toString()
    }

    @Test fun wholeRowRevealsWithoutScrollingAndApproachingRowsArePreparedOnce() = runDesktopComposeUiTest(width = 1000, height = 300) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        PosterCardStyleRepository.setWidthDp(70)
        val art = backdrop()
        val slowVisibleCard = CompletableDeferred<Unit>()
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val sections = (0..4).map { row ->
            HomeCatalogSection("loading-row-$row", "Performance row $row", "", "Isolated fixture",
                CatalogTarget.Addon("https://fixture.invalid/manifest.json", "series", "loading-row-$row"),
                (0 until 20).map { card -> MetaPreview("row-$row-card-$card", "series", "Series $row:$card", banner = art) })
        }
        val rows: List<Pair<String, HomeCatalogSection?>> = sections.map { it.key to it }
        var firstVisible = -1
        var preparedStore: PreparedArtworkStore? = null
        setContent {
            AppEnvironment {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { id, _, _ ->
                    calls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
                    if (id == "row-0-card-5") slowVisibleCard.await()
                    TmdbLandscapeArtwork(art, null, art)
                }) {
                    val listState = rememberLazyListState()
                    SideEffect { firstVisible = listState.firstVisibleItemIndex }
                    val store = rememberHomeArtworkPrefetch(listState, rows, visibleCardCount = 6,
                        landscape = true, profileKey = "isolated-loading", language = "en-US", apiKey = "fixture-key")
                    SideEffect { preparedStore = store }
                    CompositionLocalProvider(LocalPreparedLandscapeArtwork provides store) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("performance_rows")) {
                            items(sections, key = { it.key }) { section ->
                                Box(Modifier.height(300.dp)) { HomeCatalogRowSection(section, sectionPadding = 0.dp) }
                            }
                        }
                    }
                }
            }
        }
        try {
            waitUntil(timeoutMillis = 10_000) {
                calls.containsKey("row-0-card-5") && calls.containsKey("row-2-card-0")
            }
            waitForIdle()
            assertEquals(0, firstVisible)
            // Fast cards have settled, but the last visible card still holds the whole row.
            for (card in 0 until 6) {
                onAllNodesWithContentDescription("Series 0:$card").assertCountEquals(0)
                onAllNodesWithText("Series 0:$card").assertCountEquals(0)
            }
            val reports = File("build/reports/home-loading-performance").apply { mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "cold-row-pending.png"))
            runOnIdle { slowVisibleCard.complete(Unit) }
            try {
                waitUntil(timeoutMillis = 10_000) {
                    (0 until 6).all { card ->
                        onAllNodesWithContentDescription("Series 0:$card").fetchSemanticsNodes().isNotEmpty()
                    }
                }
            } catch (error: Throwable) {
                File(reports, "cold-row-failure.txt").writeText(
                    "Calls: ${calls.mapValues { it.value.get() }}\n" +
                    "Prepared: ${(0 until 7).associateWith { preparedStore?.get("series:row-0-card-$it") }}\n" +
                    onRoot(useUnmergedTree = true).printToString())
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "cold-row-failure.png"))
                throw error
            }
            assertEquals(0, firstVisible, "A row must reveal when ready without requiring a scroll event")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "cold-row-ready.png"))
            // Row 2 has not entered the viewport: only its planned screen-width slice is loaded.
            val offscreenCalls = calls.keys.filter { it.startsWith("row-2-") }
            assertTrue(offscreenCalls.isNotEmpty(), "Approaching rows must start metadata before becoming visible")
            assertTrue(offscreenCalls.size <= 7, "Prefetch must load the visible slice plus one, not the 18-card preview: $offscreenCalls")
            waitUntil(timeoutMillis = 10_000) {
                (0 until 6).all { preparedStore?.get("series:row-2-card-$it")?.isLoading == false }
            }
            val warmStart = System.nanoTime()
            onNodeWithTag("performance_rows").performScrollToIndex(2)
            waitUntil(timeoutMillis = 10_000) {
                (0 until 6).all { card ->
                    onAllNodesWithContentDescription("Series 2:$card").fetchSemanticsNodes().isNotEmpty()
                }
            }
            val warmMillis = (System.nanoTime() - warmStart) / 1_000_000
            assertTrue(warmMillis < 1500, "A decoded prefetched row should reveal promptly: $warmMillis ms")
            File(reports, "metrics.txt").writeText("Offscreen row metadata requests: ${offscreenCalls.size}/20 items. Warm row reveal: $warmMillis ms.\n")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "prefetched-row-visible.png"))
            runOnIdle {
                assertTrue(calls.values.all { it.get() == 1 }, "Cards and prefetch must share metadata work: ${calls.mapValues { it.value.get() }}")
            }
        } finally {
            slowVisibleCard.complete(Unit)
        }
    }
}
