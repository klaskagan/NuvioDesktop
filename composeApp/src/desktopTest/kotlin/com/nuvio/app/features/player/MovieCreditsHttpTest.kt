package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.fetchMovieCreditText
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class MovieCreditsHttpTest {
    @Test fun transportReadsPublicJsonAndCancelsSlowResponsesPromptly() = runBlocking {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = executor
            createContext("/credits") { exchange ->
                val bytes = "{\"type\":\"movie\"}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/slow") { exchange ->
                try {
                    exchange.sendResponseHeaders(200, 100)
                    Thread.sleep(10_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally { exchange.close() }
            }
            start()
        }
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals("{\"type\":\"movie\"}", fetchMovieCreditText("$base/credits"))
            val started = System.nanoTime()
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(100) { fetchMovieCreditText("$base/slow") }
            }
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "Cancellation must not wait for the blocking body")
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
