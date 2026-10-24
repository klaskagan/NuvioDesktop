package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.nuvio.app.AppEnvironment
import com.nuvio.app.AppScreenTab
import com.nuvio.app.RootTabHost
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaArtworkWarmReturnTest {
    @Test fun preparedDescriptorsRemainSynchronouslyAvailableBeyond128AcrossOwners() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val scheduler = SharedMediaPreparationScheduler<String>(scope)
            val loads = AtomicInteger()
            val received = AtomicInteger()
            val ready = CompletableDeferred<Unit>()
            fun request(index: Int) = SharedMediaPreparationScheduler.Request("warm-$index", index, load = {
                loads.incrementAndGet(); "descriptor-$index"
            }, ready = { _: String -> if (received.incrementAndGet() == 180) ready.complete(Unit) })
            scheduler.update("first-screen", (0 until 180).map(::request))
            withTimeout(10_000) { ready.await() }
            scheduler.remove("first-screen")
            // Peek must work immediately; it cannot rely on processing a new viewport Plan event.
            repeat(180) { assertEquals("descriptor-$it", scheduler.peek("warm-$it")) }
            val secondReady = CompletableDeferred<Unit>()
            scheduler.update("reopened-screen", listOf(SharedMediaPreparationScheduler.Request("warm-0", 0,
                load = { loads.incrementAndGet(); "unexpected" }, ready = { value: String ->
                    assertEquals("descriptor-0", value); secondReady.complete(Unit)
                })))
            withTimeout(10_000) { secondReady.await() }
            assertEquals(180, loads.get(), "A new screen must reuse warm descriptors beyond the old 128-item limit")
        } finally { scope.cancel() }
    }

    @Test fun coldVisibleRowsRevealInOrderWithoutWaitingForSkippedOffscreenRows() = runDesktopComposeUiTest(width = 1000, height = 600) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        PosterCardStyleRepository.setWidthDp(70)
        val file = File.createTempFile("cold-row-order-", ".png").apply { deleteOnExit() }
        ImageIO.write(BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB), "png", file)
        val art = file.toURI().toString()
        val firstRowGate = CompletableDeferred<Unit>()
        val sections = (0 until 3).map { row -> HomeCatalogSection("order-row-$row", "Order row $row", "", "Isolated fixture",
            CatalogTarget.Addon("https://fixture.invalid/manifest.json", "series", "order-row-$row"),
            (0 until 6).map { card -> MetaPreview("order-$row-$card", "series", "Order series $row:$card", banner = art) }) }
        var store: PreparedArtworkStore? = null
        setContent {
            AppEnvironment {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { id, _, _ ->
                    if (id == "order-0-0") firstRowGate.await()
                    TmdbLandscapeArtwork(art, null, art)
                }) {
                    val state = rememberLazyListState()
                    val prepared = rememberListArtworkPrefetch(state, sections.map { ArtworkPrefetchRow(it.key, it.items) }, "cold-row-order")
                    SideEffect { store = prepared }
                    CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared) {
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                            items(sections, key = { it.key }) { section ->
                                Box(Modifier.height(300.dp)) { HomeCatalogRowSection(section, sectionPadding = 0.dp) }
                            }
                        }
                    }
                }
            }
        }
        try {
            waitUntil(timeoutMillis = 10_000) { sections[1].items.all { store?.get(it.stableKey())?.isLoading == false } }
            waitForIdle()
            onAllNodesWithContentDescription("Order series 1:0").assertCountEquals(0)
            runOnIdle { firstRowGate.complete(Unit) }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithContentDescription("Order series 0:0").fetchSemanticsNodes().isNotEmpty() &&
                    onAllNodesWithContentDescription("Order series 1:0").fetchSemanticsNodes().isNotEmpty()
            }
        } finally { firstRowGate.complete(Unit) }
        // A skipped, unprepared row cannot hold up a later visible row after a jump.
        runOnIdle {
            val skipped = MetaPreview("skipped", "series", "Skipped")
            val visible = sections[1].items.first()
            val isolated = PreparedArtworkStore()
            isolated.manage(setOf(skipped.stableKey(), visible.stableKey()))
            isolated.rows(listOf(ArtworkPrefetchRow("skipped", listOf(skipped)), ArtworkPrefetchRow("visible", listOf(visible))))
            isolated.visibleRows = listOf("visible")
            isolated.complete(visible.stableKey(), LandscapeMediaArtworkState())
            assertTrue(isolated.mayReveal(visible.stableKey()))
        }
    }

    @Test fun backscrollAndRetainedTabReturnKeepWarmRowsVisibleAfterFullImageCacheClear() = runDesktopComposeUiTest(width = 1000, height = 300) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        PosterCardStyleRepository.setWidthDp(70)
        val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(60, 100, 140); fillRect(0, 0, 320, 180); dispose() }
        val file = File.createTempFile("warm-return-art-", ".png").apply { deleteOnExit() }
        ImageIO.write(image, "png", file)
        val art = (File("/tmp/nuvio-landscape-art/dune.jpg").takeIf { it.isFile } ?: file).toURI().toString()
        val logo = (File("/tmp/nuvio-landscape-art/dune-logo.png").takeIf { it.isFile } ?: file).toURI().toString()
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val sections = (0 until 30).map { row -> HomeCatalogSection("warm-row-$row", "Warm row $row", "", "Isolated fixture",
            CatalogTarget.Addon("https://fixture.invalid/manifest.json", "series", "warm-row-$row"),
            (0 until 6).map { card -> MetaPreview("warm-$row-$card", "series", "Warm series $row:$card", banner = art) }) }
        val rows = sections.map { ArtworkPrefetchRow(it.key, it.items) }
        var selected by mutableStateOf(AppScreenTab.Home)
        var store: PreparedArtworkStore? = null
        var loader: ImageLoader? = null
        setContent {
            AppEnvironment {
                val context = LocalPlatformContext.current
                SideEffect { loader = SingletonImageLoader.get(context) }
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { id, _, _ ->
                    calls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
                    TmdbLandscapeArtwork(art, logo, art)
                }) {
                    RootTabHost(selectedTab = selected) { tab ->
                        if (tab == AppScreenTab.Home) {
                            val state = rememberLazyListState()
                            val prepared = rememberListArtworkPrefetch(state, rows, "warm-return")
                            SideEffect { store = prepared }
                            CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared) {
                                LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("warm_rows")) {
                                    items(sections, key = { it.key }) { section ->
                                        Box(Modifier.height(300.dp)) { HomeCatalogRowSection(section, sectionPadding = 0.dp) }
                                    }
                                }
                            }
                        } else Box(Modifier.fillMaxSize().testTag("other_tab"))
                    }
                }
            }
        }
        for (row in sections.indices) {
            onNodeWithTag("warm_rows").performScrollToIndex(row)
            waitUntil(timeoutMillis = 10_000) { sections[row].items.all { store?.get(it.stableKey())?.isLoading == false } }
            if (row == 0) waitUntil(timeoutMillis = 10_000) {
                (0 until 6).all { card -> onAllNodesWithContentDescription("Warm series 0:$card").fetchSemanticsNodes().isNotEmpty() }
            }
        }
        assertEquals(180, calls.size, "The test must cross the historical 128-item boundary")
        waitForIdle()
        runOnIdle {
            sections.flatMap { it.items }.forEach { assertFalse(store!![it.stableKey()]!!.isLoading) }
            loader!!.memoryCache!!.clear()
        }
        onNodeWithTag("warm_rows").performScrollToIndex(0)
        waitForIdle()
        for (card in 0 until 6) onAllNodesWithContentDescription("Warm series 0:$card").assertCountEquals(1)
        runOnIdle { selected = AppScreenTab.Search }
        waitForIdle()
        runOnIdle {
            loader!!.memoryCache!!.clear()
            sections.flatMap { it.items }.forEach { assertFalse(store!![it.stableKey()]!!.isLoading) }
            selected = AppScreenTab.Home
        }
        waitForIdle()
        for (card in 0 until 6) onAllNodesWithContentDescription("Warm series 0:$card").assertCountEquals(1)
        assertTrue(calls.values.all { it.get() == 1 }, "Backscroll/tab return must not repeat metadata: ${calls.mapValues { it.value.get() }}")
        val reports = File("build/reports/media-artwork-warm-return").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "warm-tab-return-after-memory-clear.png"))
    }
}
