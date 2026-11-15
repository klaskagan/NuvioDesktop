package com.nuvio.app.features.trailer

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID

/** The preview session owns this master. Its variant and audio URLs point to the CDN. */
internal class DesktopHeroPlaylist(playlist: String) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val path = "/${UUID.randomUUID()}/trailer.m3u8"
    val url = "http://127.0.0.1:${server.address.port}$path"

    init {
        val bytes = playlist.toByteArray(Charsets.UTF_8)
        server.createContext(path) { exchange ->
            exchange.use {
                if (exchange.requestURI.path != path) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    exchange.responseHeaders.set("Content-Length", bytes.size.toString())
                    when (exchange.requestMethod) {
                        "HEAD" -> exchange.sendResponseHeaders(200, -1)
                        "GET" -> {
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.write(bytes)
                        }
                        else -> {
                            exchange.responseHeaders.remove("Content-Length")
                            exchange.sendResponseHeaders(405, -1)
                        }
                    }
                }
            }
        }
        server.start()
    }

    override fun close() = server.stop(0)
}
