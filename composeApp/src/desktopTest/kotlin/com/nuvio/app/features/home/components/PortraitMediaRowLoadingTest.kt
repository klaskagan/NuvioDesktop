package com.nuvio.app.features.home.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.details.components.DetailPosterRailSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.sun.net.httpserver.HttpServer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PortraitMediaRowLoadingTest {
    @Test fun portraitDiscoverRowKeepsItsLayoutAndRevealsTogether() = verifyPortraitRow(rail = false)
    @Test fun portraitDetailRailPrefetchesAndRevealsTogetherWithoutMetadata() = verifyPortraitRow(rail = true)

    private fun bytes(): ByteArray {
        val real = File("/tmp/nuvio-landscape-art/dune.jpg")
        if (real.isFile) return real.readBytes()
        val image = BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(30, 60, 90); fillRect(0, 0, 200, 300); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test fun squareMediaRowAlsoUsesTheSharedLoadingPipeline() = verifyPortraitRow(rail = false, shape = PosterShape.Square)

    private fun verifyPortraitRow(rail: Boolean, shape: PosterShape = PosterShape.Poster) = runDesktopComposeUiTest(width = 1000, height = 700) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        PosterCardStyleRepository.setHideLabelsEnabled(false)
        PosterCardStyleRepository.setHoverPreviewEnabled(false)
        PosterCardStyleRepository.setWidthDp(140)
        val slow = CountDownLatch(1)
        val slowStarted = AtomicBoolean()
        val fastSent = AtomicBoolean()
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        val metadataCalls = AtomicInteger()
        val imageBytes = bytes()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            val ready = if (path.contains("slow")) {
                slowStarted.set(true)
                slow.await(20, TimeUnit.SECONDS)
            } else true
            if (ready) {
                exchange.responseHeaders.set("Content-Type", "image/jpeg")
                exchange.responseHeaders.set("Cache-Control", "public, max-age=3600")
                exchange.sendResponseHeaders(200, imageBytes.size.toLong())
                exchange.responseBody.use { it.write(imageBytes) }
                if (!path.contains("slow")) fastSent.set(true)
            } else exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val items = listOf("fast-a", "fast-b", "slow").map { id ->
                MetaPreview("portrait-$id", "movie", "Portrait $id", poster = "$base/$id.jpg",
                    posterShape = shape, releaseInfo = "2026")
            }
            var clicked: String? = null
            setContent {
                AppEnvironment {
                    CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ ->
                        metadataCalls.incrementAndGet()
                        error("Portrait images must not request landscape metadata")
                    }) {
                        if (rail) DetailPosterRailSection("Related movies", items, emptySet(),
                            onPosterClick = { clicked = it.id })
                        else PosterGridRow(items, columns = 3, onPosterClick = { clicked = it.id })
                    }
                }
            }
            waitUntil(timeoutMillis = 10_000) { slowStarted.get() && fastSent.get() }
            waitForIdle()
            items.forEach { onAllNodesWithContentDescription(it.name).assertCountEquals(0) }
            assertEquals(0, metadataCalls.get())
            val reports = File("build/reports/media-row-loading").apply { mkdirs() }
            val capture = if (rail) "detail-portrait" else if (shape == PosterShape.Square) "square-grid" else "portrait-grid"
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "$capture-pending.png"))
            slow.countDown()
            waitUntil(timeoutMillis = 10_000) {
                items.all { onAllNodesWithContentDescription(it.name).fetchSemanticsNodes().isNotEmpty() }
            }
            items.forEach { onNodeWithText(it.name).assertExists() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "$capture-ready.png"))
            val bounds = onNodeWithContentDescription(items.first().name).fetchSemanticsNode().boundsInRoot
            assertTrue(abs(bounds.width / bounds.height - if (shape == PosterShape.Square) 1f else 0.68f) < .03f, "Portrait geometry must be retained")
            onNodeWithContentDescription(items.first().name).performClick()
            runOnIdle {
                assertEquals(items.first().id, clicked)
                assertEquals(0, metadataCalls.get())
                assertTrue(requests.values.all { it.get() == 1 }, "Prefetch and renderer must share decoded requests: ${requests.mapValues { it.value.get() }}")
            }
        } finally {
            slow.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
