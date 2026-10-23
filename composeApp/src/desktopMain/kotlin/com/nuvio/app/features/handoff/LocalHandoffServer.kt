package com.nuvio.app.features.handoff

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.Closeable
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

internal fun privateTvAddress(host: String): InetAddress {
    require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) { "Enter the TV's local IPv4 address, without a port." }
    return InetAddress.getByName(host).also {
        require(it.isSiteLocalAddress || it.isLinkLocalAddress) { "TV must be on your local network." }
        require(!it.isLoopbackAddress && !it.isMulticastAddress)
    }
}

internal class LocalHandoffServer(
    identity: TvIdentity,
    private val target: InetAddress,
    val request: TvHandoffRequest,
    private val onUpdate: (TvHandoffUpdate) -> Unit,
) : Closeable {
    private val lock = Any()
    private val session = TvHandoffSession(request)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val token = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "nuvio-handoff-http").apply { isDaemon = true }
    }
    private val address = DatagramSocket().use { socket ->
        socket.connect(target, 9)
        socket.localAddress
    }
    private val server = HttpsServer.create(InetSocketAddress(address, 0), 8).apply {
        httpsConfigurator = HttpsConfigurator(identity.context())
        this.executor = this@LocalHandoffServer.executor
    }
    val launchParameters = buildJsonObject {
        put("endpoint", "https://${address.hostAddress}:${server.address.port}/v1/handoff")
        put("certificatePin", identity.certificate.fingerprint())
        // Native receivers use libcurl SPKI pinning before sending the bearer token.
        put("publicKeyPin", "sha256//" + java.util.Base64.getEncoder().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(identity.certificate.publicKey.encoded)))
        put("token", token)
        put("requestId", request.requestId)
        put("targetDeviceId", request.targetDeviceId)
        put("senderDeviceId", request.senderDeviceId)
    }

    init {
        server.createContext("/v1/handoff") { exchange ->
            exchange.use {
                try {
                    val authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
                    val authenticated = MessageDigest.isEqual(authorization.toByteArray(), "Bearer $token".toByteArray())
                    if (exchange.remoteAddress.address != target || !authenticated) {
                        exchange.sendResponseHeaders(403, -1)
                        return@use
                    }
                    val path = exchange.requestURI.path
                    val response = synchronized(lock) {
                        session.expire(System.currentTimeMillis())
                        if (session.phase == TvHandoffPhase.CANCELLED || session.phase == TvHandoffPhase.EXPIRED) {
                            exchange.sendResponseHeaders(410, -1)
                            return@synchronized null
                        }
                        when {
                            exchange.requestMethod == "GET" && path == "/v1/handoff" -> json.encodeToString(request)
                            exchange.requestMethod == "POST" && path == "/v1/handoff/status" -> {
                                val body = exchange.requestBody.readNBytes(16_385)
                                require(body.size <= 16_384)
                                val update = json.decodeFromString<TvHandoffUpdate>(body.decodeToString())
                                if (!session.accept(update, System.currentTimeMillis())) {
                                    exchange.sendResponseHeaders(409, -1)
                                    return@synchronized null
                                }
                                onUpdate(update)
                                "{}"
                            }
                            else -> {
                                exchange.sendResponseHeaders(404, -1)
                                null
                            }
                        }
                    } ?: return@use
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    val body = response.toByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.write(body)
                } catch (_: Exception) {
                    runCatching { exchange.sendResponseHeaders(400, -1) }
                }
            }
        }
        server.start()
    }

    fun cancel() = synchronized(lock) { session.cancel() }
    fun cancelAndCheckPlayback(): Boolean = synchronized(lock) {
        session.cancel()
        session.phase == TvHandoffPhase.PLAYING
    }
    val playbackConfirmed: Boolean get() = synchronized(lock) { session.phase == TvHandoffPhase.PLAYING }
    override fun close() {
        cancel()
        server.stop(0)
        executor.shutdownNow()
    }
}
