package com.nuvio.app.features.player.desktop

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.nio.file.Files
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises native/shared/seek_thumbnailer.h through the JNI bridge. */
class SeekPreviewNativeLiveTest {
    private val fixture = File("../testFixtures/subtitles/video.mp4").absoluteFile

    @Test
    fun `local file thumbnails decode keyframes as small JPEGs`() {
        assumeTrue(System.getenv("NUVIO_PLAYER_LIVE_TEST") == "1")
        check(fixture.isFile)
        val source = checkNotNull(NativeSeekPreviewFrameSource.open(fixture.absolutePath, emptyList()))
        val output = Files.createTempFile("seek-thumb", ".jpg").toFile()
        try {
            for (target in listOf(15_000L, 2_000L, 9_000L)) {
                val position = source.frame(target, output, 10_000)
                assertTrue(position >= 0, "frame at $target returned $position")
                assertTrue(position <= target + 500 && abs(position - target) < 10_000, "keyframe $position for $target")
                val image = checkNotNull(ImageIO.read(output)) { "not a JPEG" }
                assertEquals(320, image.width)
                assertTrue(image.height in 100..320)
            }
        } finally {
            source.close()
            output.delete()
        }
    }

    @Test
    fun `http thumbnails send the stream headers and fail cleanly without them`() {
        assumeTrue(System.getenv("NUVIO_PLAYER_LIVE_TEST") == "1")
        check(fixture.isFile)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/stream") { exchange ->
                if (exchange.requestHeaders.getFirst("X-Nuvio-Test") != "secret") {
                    exchange.sendResponseHeaders(403, -1)
                    exchange.close()
                    return@createContext
                }
                val size = fixture.length()
                val range = Regex("bytes=(\\d+)-(\\d*)").find(exchange.requestHeaders.getFirst("Range").orEmpty())
                val start = range?.groupValues?.get(1)?.toLong() ?: 0L
                val end = range?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toLong() ?: (size - 1)
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                if (range != null) exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$size")
                exchange.sendResponseHeaders(if (range != null) 206 else 200, end - start + 1)
                RandomAccessFile(fixture, "r").use { file ->
                    file.seek(start)
                    val buffer = ByteArray((end - start + 1).toInt())
                    file.readFully(buffer)
                    runCatching { exchange.responseBody.use { it.write(buffer) } }
                }
            }
            start()
        }
        val url = "http://127.0.0.1:${server.address.port}/stream"
        val output = Files.createTempFile("seek-thumb", ".jpg").toFile()
        try {
            val withHeaders = checkNotNull(NativeSeekPreviewFrameSource.open(url, listOf("X-Nuvio-Test: secret")))
            try {
                val position = withHeaders.frame(12_000, output, 10_000)
                assertTrue(position >= 0, "header-authorized stream returned $position")
                assertEquals(320, checkNotNull(ImageIO.read(output)).width)
            } finally {
                withHeaders.close()
            }
            val withoutHeaders = checkNotNull(NativeSeekPreviewFrameSource.open(url, emptyList()))
            try {
                assertEquals(SeekPreviewFrameSource.UNAVAILABLE, withoutHeaders.frame(12_000, output, 10_000))
            } finally {
                withoutHeaders.close()
            }
        } finally {
            server.stop(0)
            output.delete()
        }
    }
}
