package com.nuvio.app.features.home.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.SuccessResult
import coil3.decode.DataSource
import com.nuvio.app.core.ui.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopMediaImageCacheTest {
    @Test fun encodedArtworkSurvivesMemoryEvictionWithoutAnotherNetworkRequest() = runBlocking {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val image = BufferedImage(600, 338, BufferedImage.TYPE_INT_RGB)
        val data = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/poster") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.responseHeaders.add("Cache-Control", "public, max-age=86400")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
        server.start()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(coil3.network.ktor3.KtorNetworkFetcherFactory()) }
            .configurePlatformImageLoader().build()
        try {
            val request = landscapeImageRequest(PlatformContext.INSTANCE, "http://127.0.0.1:${server.address.port}/poster")
            val first = assertIs<SuccessResult>(loader.execute(request))
            cacheMediaImagePreview(request, first.image)
            assertNotNull(cachedMediaImagePreview(request))
            assertFalse(cachedMediaImagePreview(request)!!.wasDisplayed)
            markMediaImageDisplayed(request)
            assertFalse(cachedMediaImagePreview(request, "different-media")!!.wasDisplayed,
                "A shared image URL must not mark another media identity as displayed")
            loader.memoryCache!!.clear()
            assertTrue(cachedMediaImagePreview(request)!!.wasDisplayed, "Tiny display preview must survive full-resolution eviction")
            val second = assertIs<SuccessResult>(loader.execute(request))
            assertEquals(DataSource.DISK, second.dataSource)
            assertEquals(1, requests.get(), "Backscroll must restore encoded artwork locally, not refetch it")
        } finally { loader.shutdown(); server.stop(0) }
    }
}
