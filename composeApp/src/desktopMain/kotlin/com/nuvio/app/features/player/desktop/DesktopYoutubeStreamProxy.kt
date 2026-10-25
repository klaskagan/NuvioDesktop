package com.nuvio.app.features.player.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Adapts mpv byte ranges to YouTube's bounded range-query requests, like Android's datasource. */
internal class DesktopYoutubeStreamProxy(
    private val headers: Map<String, String>,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
    private val chunkSize: Long = 10L * 1024 * 1024,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "nuvio-youtube-range").apply { isDaemon = true }
    }
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val token = UUID.randomUUID().toString()
    @Volatile private var closed = false
    private var nextId = 0

    init {
        require(chunkSize > 0)
        server.executor = executor
        server.start()
    }

    fun register(url: HttpUrl): String {
        val size = requireNotNull(url.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 })
        val path = "/$token/${nextId++}"
        server.createContext(path) { exchange ->
            exchange.use {
                if (exchange.requestURI.path != path) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    serve(exchange, url, size)
                }
            }
        }
        return "http://127.0.0.1:${server.address.port}$path"
    }

    private fun serve(exchange: HttpExchange, url: HttpUrl, size: Long) {
        if (exchange.requestMethod !in setOf("GET", "HEAD")) {
            exchange.sendResponseHeaders(405, -1)
            return
        }
        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        val range = parseRange(rangeHeader, size)
        if (range == null) {
            exchange.responseHeaders.set("Content-Range", "bytes */$size")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        val (start, end) = range
        val length = end - start + 1
        exchange.responseHeaders.set("Accept-Ranges", "bytes")
        exchange.responseHeaders.set("Content-Type", url.queryParameter("mime") ?: "application/octet-stream")
        exchange.responseHeaders.set("Content-Length", length.toString())
        if (rangeHeader != null) exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$size")
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(if (rangeHeader == null) 200 else 206, -1)
            return
        }
        var responseStarted = false
        try {
            var position = start
            while (!closed && position <= end) {
                val chunkEnd = position + minOf(chunkSize - 1, end - position)
                val request = Request.Builder()
                    .url(url.newBuilder().setQueryParameter("range", "$position-$chunkEnd").build())
                    .apply { headers.forEach { (key, value) -> header(key, value) } }
                    .header("Accept-Encoding", "identity")
                    .build()
                val call = client.newCall(request)
                calls.add(call)
                try {
                    if (closed) call.cancel()
                    call.execute().use { response ->
                        check(response.isSuccessful) { "YouTube range request failed (${response.code})" }
                        val body = checkNotNull(response.body)
                        val expectedLength = chunkEnd - position + 1
                        check(body.contentLength() < 0 || body.contentLength() == expectedLength) {
                            "YouTube ignored the bounded range request"
                        }
                        if (!responseStarted) {
                            exchange.sendResponseHeaders(if (rangeHeader == null) 200 else 206, length)
                            responseStarted = true
                        }
                        var remaining = chunkEnd - position + 1
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (remaining > 0 && !closed) {
                                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                check(read > 0) { "Incomplete YouTube range response" }
                                exchange.responseBody.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                        position = chunkEnd + 1
                    }
                } finally {
                    calls.remove(call)
                }
            }
        } catch (_: Exception) {
            // Closing a player cancels active upstream reads and disconnects the local client.
            if (!responseStarted && !closed) exchange.sendResponseHeaders(502, -1)
        }
    }

    override fun close() {
        closed = true
        calls.forEach { it.cancel() }
        server.stop(0)
        executor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        fun supports(url: String): Boolean = url.toHttpUrlOrNull()?.let {
            (it.host == "googlevideo.com" || it.host.endsWith(".googlevideo.com")) &&
                it.encodedPath == "/videoplayback" &&
                (it.queryParameter("clen")?.toLongOrNull() ?: 0) > 0
        } == true

        internal fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
            if (header == null) return 0L to size - 1
            val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header) ?: return null
            val (first, last) = match.destructured
            val start: Long
            val end: Long
            if (first.isEmpty()) {
                val suffix = last.toLongOrNull()?.takeIf { it > 0 } ?: return null
                start = (size - suffix).coerceAtLeast(0)
                end = size - 1
            } else {
                start = first.toLongOrNull() ?: return null
                end = if (last.isEmpty()) size - 1 else last.toLongOrNull()?.coerceAtMost(size - 1) ?: return null
            }
            return (start to end).takeIf { start < size && end >= start }
        }
    }
}
