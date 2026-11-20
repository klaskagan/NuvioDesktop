package com.nuvio.app.features.handoff

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import kotlinx.serialization.json.JsonObject
import java.io.Closeable
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.Executors

internal fun privateTvAddress(host: String): InetAddress = InetAddress.getByName(requirePrivateTvAddress(host))

internal class LocalHandoffServer(
    identity: TvIdentity,
    private val target: InetAddress,
    val request: TvHandoffRequest,
    onUpdate: (TvHandoffUpdate) -> Unit,
) : Closeable, TvHandoffServer {
    private val token = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    private val router = TvHandoffHttpRouter(request, token, onUpdate)
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
    override val launchParameters: JsonObject = tvHandoffLaunchParameters(
        address.hostAddress, server.address.port, identity.certificate.encoded, identity.certificate.publicKey.encoded,
        token, request,
    ) { java.security.MessageDigest.getInstance("SHA-256").digest(it) }

    init {
        server.createContext("/v1/handoff") { exchange ->
            exchange.use {
                try {
                    val body = if (exchange.requestMethod == "POST")
                        exchange.requestBody.readNBytes(TvHandoffHttpRouter.MAX_BODY + 1) else ByteArray(0)
                    val reply = router.handle(exchange.requestMethod, exchange.requestURI.path,
                        exchange.requestHeaders.getFirst("Authorization"), exchange.remoteAddress.address == target, body)
                    val response = reply.body
                    if (response == null) {
                        exchange.sendResponseHeaders(reply.status, -1)
                        return@use
                    }
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    val bytes = response.toByteArray()
                    exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                } catch (_: Exception) {
                    runCatching { exchange.sendResponseHeaders(400, -1) }
                }
            }
        }
        server.start()
    }

    fun cancel() = router.cancel()
    override fun cancelAndCheckPlayback(): Boolean = router.cancelAndCheckPlayback()
    val playbackConfirmed: Boolean get() = router.playbackConfirmed
    override fun close() {
        cancel()
        server.stop(0)
        executor.shutdownNow()
    }
}
