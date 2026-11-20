package com.nuvio.app.features.handoff

import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Paired SSAP over WSS. No downgrade to plaintext remote control. */
internal class WebOsRemote(host: String, pin: String? = null) : Closeable {
    private val incoming = LinkedBlockingQueue<String>(64)
    @Volatile private var failure: Throwable? = null
    private var observedPin = ""
    private val trust = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            require(chain.isNotEmpty())
            observedPin = chain.first().fingerprint()
            if (pin != null) require(pin == observedPin) { "LG certificate changed. Pair again." }
        }
    }
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trust), SecureRandom())
        }.socketFactory, trust)
        .hostnameVerifier { _, _ -> true } // TV self-signed certificate is pinned above.
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val socket = client.newWebSocket(Request.Builder().url("wss://$host:3001").build(), object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.length <= WebOsProtocol.MAX_MESSAGE) incoming.offer(text)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failure = t
            incoming.offer("{}")
        }
    })

    fun register(key: String?): WebOsPairing {
        check(socket.send(WebOsProtocol.register(key)))
        val deadline = System.nanoTime() + 60_000_000_000L
        while (System.nanoTime() < deadline) {
            val issued = WebOsProtocol.registrationKey(next(deadline)) ?: continue
            check(observedPin.isNotBlank()) { "LG did not complete pairing." }
            return WebOsPairing(issued, observedPin)
        }
        error("Approve the connection on your LG TV, then try again.")
    }

    fun launch(appId: String, parameters: JsonObject) {
        check(socket.send(WebOsProtocol.launch(appId, parameters)))
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            if (WebOsProtocol.launchAcknowledged(next(deadline))) return
        }
        error("LG did not acknowledge launching Nuvio.")
    }

    private fun next(deadline: Long): JsonObject {
        val message = incoming.poll((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
        failure?.let { throw IllegalStateException("Cannot connect to LG. Check pairing and network wake settings.", it) }
        check(message != null) { "TV connection timed out." }
        return WebOsProtocol.parse(message)
    }

    override fun close() {
        socket.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
