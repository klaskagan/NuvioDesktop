package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.*

/** Owns only synthetic local media and an isolated profile; no real watch progress is touched. */
class NativeBufferedQualityLiveTest {
    @Test fun oldVideoContinuesWhileNewQualityBuffersThenRendererIsAdopted() = runBlocking {
        assumeTrue(System.getenv("NUVIO_QUALITY_NATIVE_TEST") == "1" && DesktopHostOs.current == DesktopHostOs.MACOS)
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val high = File("build/qa/quality/720p.mkv").absoluteFile
        val low = File("build/qa/quality/360p.mp4").absoluteFile
        check(high.isFile && low.isFile)
        val requested = CountDownLatch(1)
        val releaseBody = CountDownLatch(1)
        val bytes = low.readBytes()
        val executor = Executors.newCachedThreadPool { task -> Thread(task, "buffered-quality-fixture").apply { isDaemon = true } }
        val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/video.mp4") { exchange ->
            try {
                requested.countDown()
                check(releaseBody.await(15, TimeUnit.SECONDS))
                val range = exchange.requestHeaders.getFirst("Range")?.removePrefix("bytes=")
                val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                val end = (range?.substringAfter('-')?.toIntOrNull() ?: bytes.lastIndex).coerceAtMost(bytes.lastIndex)
                exchange.responseHeaders.set("Content-Type", "video/mp4")
                exchange.responseHeaders.set("Accept-Ranges", "bytes")
                if (start > end) exchange.sendResponseHeaders(416, -1)
                else {
                    if (range != null) exchange.responseHeaders.set("Content-Range", "bytes $start-$end/${bytes.size}")
                    exchange.sendResponseHeaders(if (range == null) 200 else 206, (end - start + 1).toLong())
                    exchange.responseBody.write(bytes, start, end - start + 1)
                }
            } finally { exchange.close() }
        }
        server.start()
        lateinit var frame: JFrame
        lateinit var player: NativePlayerController
        SwingUtilities.invokeAndWait {
            val host = NativePlayerHost()
            frame = JFrame("Background quality fixture").apply { add(host); setSize(960, 640); isVisible = true }
            player = NativePlayerController(host)
            player.attach(high.toURI().toString(), emptyMap(), false, 4000, 0, false, { check(it == null) })
        }
        fun handle() = player.javaClass.getDeclaredField("handle").apply { isAccessible = true }.getLong(player)
        var prepared: BufferedPlayerQuality? = null
        try {
            withTimeout(20_000) { while (player.snapshot().isLoading || player.snapshot().videoHeight != 720) delay(100) }
            NativePlayerBridge.qualitySet(handle(), false, "mute", "yes")
            player.play()
            withTimeout(5000) { while (!player.snapshot().isPlaying) delay(100) }
            val original = handle()
            val checkpoint = player.qualityCheckpoint()!!
            val choice = PlayerQualityChoice(0, 360, "http://127.0.0.1:${server.address.port}/video.mp4", emptyMap())
            val pending = async(Dispatchers.IO) { player.prepareBufferedQuality(choice, checkpoint) }
            withTimeout(10_000) { while (!requested.await(100, TimeUnit.MILLISECONDS)) yield() }
            val positionBeforeDelay = player.qualityCheckpoint()!!.positionMs
            delay(600)
            assertEquals(original, handle())
            assertEquals(720, player.snapshot().videoHeight)
            assertTrue(player.snapshot().isPlaying)
            assertTrue(player.qualityCheckpoint()!!.positionMs > positionBeforeDelay + 200)
            assertFalse(pending.isCompleted)
            releaseBody.countDown()
            prepared = withTimeout(20_000) { pending.await() }
            assertNotNull(prepared)
            assertEquals(original, handle(), "Preparation must not replace the visible player")
            assertTrue(player.snapshot().isPlaying)
            prepared!!.cancel()
            assertEquals(original, handle(), "Cancelling a buffered candidate must retain the old renderer")
            assertTrue(player.snapshot().isPlaying)
            prepared = withTimeout(20_000) { player.prepareBufferedQuality(choice, player.qualityCheckpoint()!!) }
            assertNotNull(prepared)
            val cut = prepared!!.promote()
            assertNotEquals(original, handle())
            assertTrue(player.consumeBufferedSource(choice.url, choice.headers))
            assertFalse(player.consumeBufferedSource(choice.url, choice.headers))
            withTimeout(5000) { while (!player.snapshot().isPlaying || player.snapshot().videoHeight != 360) delay(100) }
            assertTrue(abs(player.qualityCheckpoint()!!.positionMs - cut.positionMs) < 1500)
            withTimeout(5000) { while (NativePlayerBridge.videoFrameSignature(handle()) == 0L) delay(100) }
        } finally {
            releaseBody.countDown()
            withContext(NonCancellable) { prepared?.cancel() }
            SwingUtilities.invokeAndWait { player.dispose(); frame.dispose() }
            server.stop(0); executor.shutdownNow()
        }
    }
}
