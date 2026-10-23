package com.nuvio.app.features.handoff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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

internal data class WebOsPairing(val clientKey: String, val certificatePin: String)

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
            if (text.length <= 65_536) incoming.offer(text)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failure = t
            incoming.offer("{}")
        }
    })

    fun register(key: String?): WebOsPairing {
        val request = buildJsonObject {
            put("id", "register")
            put("type", "register")
            putJsonObject("payload") {
                if (key != null) put("client-key", key)
                put("pairingType", "PROMPT")
                put("forcePairing", false)
                putJsonObject("manifest") {
                    put("manifestVersion", 1)
                    put("appVersion", "1.0")
                    putJsonArray("permissions") {
                        listOf("LAUNCH", "READ_APP_STATUS", "READ_INSTALLED_APPS", "READ_POWER_STATE")
                            .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    }
                }
            }
        }
        check(socket.send(request.toString()))
        val deadline = System.nanoTime() + 60_000_000_000L
        while (System.nanoTime() < deadline) {
            val message = next(deadline)
            if (message["type"]?.jsonPrimitive?.content == "registered") {
                val issued = message["payload"]?.jsonObject?.get("client-key")?.jsonPrimitive?.content
                check(!issued.isNullOrBlank() && observedPin.isNotBlank()) { "LG did not complete pairing." }
                return WebOsPairing(issued, observedPin)
            }
            check(message["type"]?.jsonPrimitive?.content != "error") { "LG pairing was declined." }
        }
        error("Approve the connection on your LG TV, then try again.")
    }

    fun launch(appId: String, parameters: JsonObject) {
        check(socket.send(buildJsonObject {
            put("id", "launch")
            put("type", "request")
            put("uri", "ssap://system.launcher/launch")
            putJsonObject("payload") { put("id", appId); put("params", parameters) }
        }.toString()))
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            val message = next(deadline)
            if (message["id"]?.jsonPrimitive?.content == "launch") {
                check(message["type"]?.jsonPrimitive?.content != "error") { "LG could not launch Nuvio. Check its installed app ID." }
                return
            }
        }
        error("LG did not acknowledge launching Nuvio.")
    }

    private fun next(deadline: Long): JsonObject {
        val message = incoming.poll((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
        failure?.let { throw IllegalStateException("Cannot connect to LG. Check pairing and network wake settings.", it) }
        check(message != null) { "TV connection timed out." }
        return Json.parseToJsonElement(message).jsonObject
    }

    override fun close() {
        socket.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
