package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

/** A controlled latency/scroll benchmark, not a claim about live provider performance. */
@OptIn(ExperimentalTestApi::class)
class AdaptiveMediaScrollingTest {
    @Test fun scriptedScrollingComparesFixedAndAdaptiveLeadAndCapturesRenderedRows() = runDesktopComposeUiTest(width = 640, height = 180) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        val art = File("/tmp/nuvio-landscape-art/dune.jpg").takeIf { it.isFile } ?: File.createTempFile("adaptive-row", ".png").also {
            val image = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
            ImageIO.write(image, "png", it); it.deleteOnExit()
        }
        val url = art.toURI().toString()
        val network = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "fixture-latency").apply { isDaemon = true } }
        var adaptive by mutableStateOf(false)
        var store: PreparedArtworkStore? = null
        var window: MediaPrefetchWindow? = null
        var metrics: MediaPreparationMetrics? = null
        setContent { AppEnvironment {
            key(adaptive) {
                val mode = adaptive
                val rows = remember { (0 until 24).map { i ->
                    ArtworkPrefetchRow("row:$i", listOf(MetaPreview("$mode-$i", "movie", "Dune $i", banner = url)))
                } }
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { _, _, _ ->
                    val response = CompletableDeferred<Unit>()
                    network.schedule({ response.complete(Unit) }, 500, TimeUnit.MILLISECONDS)
                    response.await()
                    TmdbLandscapeArtwork(url, null, url)
                }) {
                    val state = rememberLazyListState()
                    val currentWindow = remember { MediaPrefetchWindow(if (mode) 6 else 2) }
                    val prepared = rememberListArtworkPrefetch(state, rows, "scroll:$mode", window = currentWindow)
                    val scheduler = rememberMediaPreparationScheduler()
                    SideEffect { store = prepared; window = currentWindow; metrics = scheduler.metrics }
                    CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared) {
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("adaptive_rows")) {
                            items(rows, key = { it.key }) { row ->
                                Box(Modifier.height(180.dp).width(320.dp)) { HomePosterCard(row.items.single(), useLandscapeBackdropMode = true, fillWidth = true) }
                            }
                        }
                    }
                }
            }
        } }
        val report = File("build/reports/adaptive-prefetch").apply { mkdirs() }
        val results = mutableListOf<String>()
        val lateCounts = mutableListOf<Int>()
        for (mode in listOf(false, true)) {
            runOnIdle { adaptive = mode }
            waitUntil(timeoutMillis = 10_000) { store?.get("movie:$mode-2")?.isLoading == false }
            var late = 0
            var maximumAhead = 2
            for (row in 1..16) {
                onNodeWithTag("adaptive_rows").performScrollToIndex(row)
                runOnIdle {
                    if (row > 4 && store?.get("movie:$mode-$row")?.isLoading != false) late++
                    maximumAhead = maxOf(maximumAhead, window?.aheadRows ?: 2)
                }
                val stepStarted = System.nanoTime()
                waitUntil(timeoutMillis = 2000) { System.nanoTime() - stepStarted >= 175_000_000L }
            }
            waitUntil(timeoutMillis = 10_000) { store?.get("movie:$mode-16")?.isLoading == false }
            waitForIdle()
            val label = if (mode) "adaptive" else "fixed-two-row"
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(report, "$label-ready.png"))
            results += "$label: late rows=$late/12; maximum ahead=$maximumAhead; global maximum active=${metrics?.maximumActive}; cumulative queue ms=${metrics?.queueMillis}; metadata ms=${metrics?.metadataMillis}; image ms=${metrics?.imageMillis}"
            lateCounts += late
            assertTrue((metrics?.maximumActive ?: 0) <= 4)
            if (mode) assertTrue(maximumAhead > 2, "Scrolling through slow metadata must expand lookahead")
        }
        File(report, "metrics.txt").writeText(results.joinToString("\n") + "\nControlled metadata latency=500ms, interval=175ms; local decoded artwork.\n")
        network.shutdownNow()
        assertTrue(lateCounts[1] < lateCounts[0], "Adaptive prefetch must improve readiness under the controlled latency: $results")
    }
}
