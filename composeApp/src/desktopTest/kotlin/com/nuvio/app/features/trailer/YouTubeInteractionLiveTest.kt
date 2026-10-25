package com.nuvio.app.features.trailer

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeInteractionLiveTest {
    @Test fun recommendationPlaysInTheSameWebViewAndBrowserLinkPausesIt() = fixture { session ->
        val handle = session.handle
        val previousTitle = session.currentTitle
        SwingUtilities.invokeAndWait { session.navigate("https://www.youtube.com/watch?v=EXeTwQWrcwY", 2) }
        await("recommended upload") { session.currentVideoId == "EXeTwQWrcwY" && session.ready && session.isPlaying && session.positionMs > 1_000 && session.currentTitle != previousTitle }
        check(session.handle == handle)
        val watch = "https://www.youtube.com/watch?v=EXeTwQWrcwY"
        SwingUtilities.invokeAndWait { session.navigate(watch) }
        await("browser action pause") { !session.isPlaying }
        check(opened.single() == watch)
        // Model a resume using YouTube's own controls, bypassing Kotlin intent.
        YouTubeNativeBridge.configure(handle, true, true)
        await("iframe-controlled resume") { session.isPlaying }
        SwingUtilities.invokeAndWait { session.navigate(watch) }
        await("second browser action pause") { !session.isPlaying }
        check(opened.toList() == listOf(watch, watch))
        println("YouTube interaction: recommendation started in retained player; browser destination and paused playback verified")
    }

    @Test fun webKitDownloadsTheLinkedPageAndImageToTheChosenDestination() = fixture { session ->
        val page = "<!doctype html><title>Linked page fixture</title>".toByteArray()
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/q9sAAAAASUVORK5CYII=")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        for ((name, body) in listOf("page.html" to page, "image.png" to png)) server.createContext("/$name") { exchange ->
            exchange.responseHeaders.add("Content-Type", if (name.endsWith("png")) "image/png" else "text/html")
            exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"$name\"")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        Files.createDirectories(Path.of("build/qa"))
        val directory = Files.createTempDirectory(Path.of("build/qa"), "youtube-download-")
        server.start()
        try {
            for ((name, body) in listOf("page.html" to page, "image.png" to png)) {
                val destination = directory.resolve(name).toAbsolutePath()
                YouTubeNativeBridge.downloadFixture(session.handle, "http://127.0.0.1:${server.address.port}/$name", destination.toString())
                await("download $name") { Files.exists(destination) && Files.size(destination) == body.size.toLong() }
                check(Files.readAllBytes(destination).contentEquals(body))
            }
            println("YouTube interaction: actual WKDownload page/image bytes verified in isolated build output")
        } finally { server.stop(0) }
    }

    private val opened = CopyOnWriteArrayList<String>()
    private fun fixture(test: (YouTubeSession) -> Unit) {
        assumeTrue(System.getenv("NUVIO_YOUTUBE_LIVE_TEST") == "1")
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val session = YouTubeSession("R5jcEBVBEbQ", true, true, 0, openBrowser = { opened += it })
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                setSize(900, 600)
                setContent { NuvioTheme { YouTubeSurface(session, Modifier.size(620.dp, 350.dp)) } }
                isVisible = true
            }
        }
        try {
            await("initial video") { session.error != null || session.ready && session.positionMs > 1_000 }
            check(session.error == null) { session.error.orEmpty() }
            test(session)
        } finally { SwingUtilities.invokeAndWait { session.close(); window.dispose() } }
    }
    private fun await(label: String, check: () -> Boolean) {
        val deadline = System.nanoTime() + 40_000_000_000L
        while (System.nanoTime() < deadline) { if (check()) return; Thread.sleep(50) }
        error("Timed out: $label")
    }
}
