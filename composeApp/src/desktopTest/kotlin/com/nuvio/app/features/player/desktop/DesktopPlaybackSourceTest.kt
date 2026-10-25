package com.nuvio.app.features.player.desktop

import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.Collections
import kotlin.test.*

class DesktopPlaybackSourceTest {
    @Test fun separateTracksReachMpvAsOneConcurrentSourceWithByteSafeQuoting() {
        val video = "https://example.invalid/映画;clip,1.mp4"
        val audio = "https://example.invalid/audio.m4a?x=1;2"
        assertEquals(
            "edl://!no_clip;%${video.toByteArray().size}%$video;!new_stream;!no_clip;%${audio.toByteArray().size}%$audio",
            nativePlaybackUrl(video, audio),
        )
        assertEquals(video, nativePlaybackUrl(video, null))
        assertEquals(video, nativePlaybackUrl(video, ""))
    }

    @Test fun controllerCarriesSeparateAudioThroughNativeCreateAndRetry() {
        val created = java.util.concurrent.LinkedBlockingQueue<String>()
        val failed = java.util.concurrent.LinkedBlockingQueue<String>()
        val controller = NativePlayerController(
            host = NativePlayerHost(),
            nativeCreate = { _, source, _, _, _, _, _, _, _ ->
                created.add(source)
                error("fixture create stopped before native initialization")
            },
            nativeDispose = {},
            isHostDisplayable = { true },
            resolveHostView = { 123L },
        )
        val video = "https://example.invalid/video.mp4"
        val audio = "https://example.invalid/audio.m4a"
        try {
            controller.attach(
                sourceUrl = video, sourceAudioUrl = audio, sourceHeaders = emptyMap(),
                playWhenReady = true, initialPositionMs = 0, decoderPriority = 1,
                nvidiaRtxSuperResolutionEnabled = false, onError = { if (it != null) failed.add(it) },
            )
            assertEquals(nativePlaybackUrl(video, audio), created.poll(3, java.util.concurrent.TimeUnit.SECONDS))
            assertNotNull(failed.poll(3, java.util.concurrent.TimeUnit.SECONDS))
            controller.retry()
            assertEquals(nativePlaybackUrl(video, audio), created.poll(3, java.util.concurrent.TimeUnit.SECONDS))
            assertNotNull(failed.poll(3, java.util.concurrent.TimeUnit.SECONDS))
        } finally { controller.dispose() }
    }

    @Test fun onlyBoundedGooglevideoFilesNeedChunking() {
        assertTrue(DesktopYoutubeStreamProxy.supports("https://rr1.googlevideo.com/videoplayback?clen=100"))
        listOf(
            "https://evilgooglevideo.com/videoplayback?clen=100",
            "https://manifest.googlevideo.com/api/manifest/hls_variant/file/index.m3u8",
            "https://rr1.googlevideo.com/videoplayback",
            "https://example.invalid/video.mp4?clen=100",
        ).forEach { assertFalse(DesktopYoutubeStreamProxy.supports(it), it) }
    }

    @Test fun closingProxyCancelsAnUpstreamRequestThatHasNotReturnedHeaders() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/videoplayback") { exchange ->
            started.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            exchange.close()
        }
        upstream.start()
        val proxy = DesktopYoutubeStreamProxy(emptyMap())
        try {
            val url = proxy.register("http://127.0.0.1:${upstream.address.port}/videoplayback?clen=32".toHttpUrl())
            val result = reader.submit<Boolean> {
                runCatching {
                    (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2_000; readTimeout = 2_000
                    }.inputStream.use { it.readBytes() }
                }.isFailure
            }
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            proxy.close()
            assertTrue(result.get(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            proxy.close()
            release.countDown()
            upstream.stop(0)
            reader.shutdownNow()
        }
    }

    @Test fun proxyTranslatesSeeksAndOpenEndedRangesIntoBoundedQueries() {
        val data = ByteArray(32) { it.toByte() }
        val ranges = Collections.synchronizedList(mutableListOf<String>())
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/videoplayback") { exchange ->
            assertEquals("fixture-agent", exchange.requestHeaders.getFirst("User-Agent"))
            val query = exchange.requestURI.rawQuery.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
            val range = query.getValue("range")
            ranges += range
            val (start, end) = range.split('-').map(String::toInt)
            val bytes = data.copyOfRange(start, end + 1)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        upstream.start()
        try {
            DesktopYoutubeStreamProxy(mapOf("User-Agent" to "fixture-agent"), chunkSize = 8).use { proxy ->
                val source = "http://127.0.0.1:${upstream.address.port}/videoplayback?clen=32&mime=video%2Fmp4"
                val url = proxy.register(source.toHttpUrl())
                fun connect(range: String? = null, method: String = "GET"): HttpURLConnection =
                    (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2_000; readTimeout = 2_000; requestMethod = method
                        range?.let { setRequestProperty("Range", it) }
                    }
                connect("bytes=5-22").let {
                    assertEquals(206, it.responseCode)
                    assertEquals("bytes 5-22/32", it.getHeaderField("Content-Range"))
                    assertContentEquals(data.copyOfRange(5, 23), it.inputStream.use { stream -> stream.readBytes() })
                    it.disconnect()
                }
                assertEquals(listOf("5-12", "13-20", "21-22"), ranges.toList())
                connect("bytes=-3").let {
                    assertContentEquals(data.takeLast(3).toByteArray(), it.inputStream.use { stream -> stream.readBytes() })
                    it.disconnect()
                }
                connect(method = "HEAD").let {
                    assertEquals(200, it.responseCode)
                    assertEquals(32, it.contentLength)
                    it.disconnect()
                }
                connect("bytes=99-").let { assertEquals(416, it.responseCode); it.disconnect() }
                connect().let {
                    assertContentEquals(data, it.inputStream.use { stream -> stream.readBytes() })
                    it.disconnect()
                }
            }
        } finally { upstream.stop(0) }
    }
}
