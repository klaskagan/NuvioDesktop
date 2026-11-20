@file:OptIn(ExperimentalForeignApi::class)

package com.nuvio.app.features.handoff

import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.value
import kotlinx.cinterop.convert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import platform.Network.nw_browse_descriptor_create_bonjour_service
import platform.Network.nw_browse_result_copy_endpoint
import platform.Network.nw_browser_cancel
import platform.Network.nw_browser_create
import platform.Network.nw_browser_set_browse_results_changed_handler
import platform.Network.nw_browser_set_queue
import platform.Network.nw_browser_set_state_changed_handler
import platform.Network.nw_browser_state_failed
import platform.Network.nw_browser_state_waiting
import platform.Network.nw_browser_start
import platform.Network.nw_connection_copy_endpoint
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_endpoint_create_url
import platform.Network.nw_endpoint_get_bonjour_service_name
import platform.Network.nw_listener_cancel
import platform.Network.nw_listener_create
import platform.Network.nw_listener_get_port
import platform.Network.nw_listener_set_new_connection_handler
import platform.Network.nw_listener_set_queue
import platform.Network.nw_listener_set_state_changed_handler
import platform.Network.nw_listener_start
import platform.Network.nw_listener_state_failed
import platform.Network.nw_listener_state_ready
import platform.Network.nw_parameters_create
import platform.Network.nw_parameters_set_local_endpoint
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.SOCK_DGRAM
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.sendto
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual fun platformTvHandoffTransport(): TvHandoffTransport? = IosTvHandoffTransport

/**
 * Network.framework transport. Differences from desktop are platform limits: iOS needs Apple's
 * multicast entitlement for SSDP and broadcast, so discovery uses Bonjour (Android TV) plus saved
 * addresses, and Wake-on-LAN is a best-effort unicast packet.
 */
private object IosTvHandoffTransport : TvHandoffTransport {
    private val log = co.touchlab.kermit.Logger.withTag("TvHandoff/iOS")
    override val localNetworkSettingsHint =
        "Allow Nuvio in iOS Settings → Privacy & Security → Local Network, then retry."

    override fun readFile(name: String): String? = IosTvHandoffKeychain.read(name)
    override fun writeFile(name: String, text: String) = IosTvHandoffKeychain.write(name, text)
    override fun sha256(bytes: ByteArray): ByteArray = iosSha256(bytes)

    private suspend fun tvIdentity(): IosTvIdentity = withContext(Dispatchers.Default) { IosTvIdentity.load() }

    override suspend fun identity(): TvSenderIdentity =
        tvIdentity().let { TvSenderIdentity(it.certificateDer, it.subjectPublicKeyInfo) }

    @kotlin.concurrent.Volatile
    override var lastDiscoveryNote: String? = null
        private set

    override suspend fun discover(known: List<DiscoveredTv>): List<DiscoveredTv> = coroutineScope {
        lastDiscoveryNote = null
        log.i { "Discovery started; ${known.size} saved address(es). LG needs its IP on iOS (no SSDP without multicast entitlement)." }
        val advertised = async { browseAndroidTvs() }
        val reachable = known.distinctBy { it.platform to it.host }
            .filter { runCatching { requirePrivateTvAddress(it.host) }.isSuccess }
            .map { tv -> async { tv to probe(tv.host, if (tv.platform == "sony") 6466 else WebOsProtocol.PORT) } }
            .awaitAll().filter { it.second }.map { it.first }.toSet()
        mergeKnownTvs(advertised.await(), known) { it in reachable || it.copy(previouslySeen = false) in reachable }
            .also { log.i { "Discovery finished: ${it.size} TV(s), ${reachable.size} saved address(es) reachable" } }
    }

    private suspend fun probe(host: String, port: Int): Boolean = runCatching {
        NwConnection.open(nw_endpoint_create_host(host, port.toString()), tcpParameters(1u), timeoutMs = 600).close()
    }.isSuccess

    /** Bonjour browse for `_androidtvremote2._tcp`, resolving each service to its IPv4 address. */
    private suspend fun browseAndroidTvs(): List<DiscoveredTv> {
        val services = mutableMapOf<String, NSObject>()
        val browser = nw_browser_create(nw_browse_descriptor_create_bonjour_service("_androidtvremote2._tcp", "local."),
            nw_parameters_create()) ?: return emptyList()
        var browserProblem: String? = null
        nw_browser_set_state_changed_handler(browser) { state, error ->
            val problem = when (state) {
                nw_browser_state_failed, nw_browser_state_waiting -> when ((nwFailure(error) as? NwFailure)?.kind) {
                    TvHandoffFailure.LocalNetworkDenied -> "Local Network access is off for Nuvio"
                    else -> "Bonjour browsing ${if (state == nw_browser_state_failed) "failed" else "is waiting"}" +
                        (error?.let { " (error ${platform.Network.nw_error_get_error_code(it)})" } ?: "")
                }
                else -> null
            }
            log.i { "Bonjour browser state=$state${problem?.let { " · $it" } ?: ""}" }
            if (problem != null) browserProblem = problem
        }
        nw_browser_set_browse_results_changed_handler(browser) { _, added, _ ->
            val endpoint = added?.let { nw_browse_result_copy_endpoint(it) } ?: return@nw_browser_set_browse_results_changed_handler
            val name = nw_endpoint_get_bonjour_service_name(endpoint)?.toKString() ?: return@nw_browser_set_browse_results_changed_handler
            log.i { "Bonjour found Android TV service \"$name\"" }
            synchronizedServices { services[name.take(128)] = endpoint }
        }
        nw_browser_set_queue(browser, tvHandoffQueue)
        nw_browser_start(browser)
        try { delay(3_000) } finally { nw_browser_cancel(browser) }
        val found = synchronizedServices { services.toMap() }
        val failures = mutableListOf<String>()
        if (found.isEmpty()) {
            lastDiscoveryNote = browserProblem ?: "no Android TV answered on Bonjour (_androidtvremote2._tcp)"
            log.w { "Discovery: $lastDiscoveryNote" }
        }
        return coroutineScope {
            found.map { (name, endpoint) ->
                async {
                    runCatching {
                        NwConnection.open(endpoint, tcpParameters(2u), timeoutMs = 2_500).use { connection ->
                            val address = connection.remoteAddress?.removePrefix("::ffff:")
                                ?: error("no IPv4 address")
                            DiscoveredTv(name, requirePrivateTvAddress(address), "sony")
                        }
                    }.onSuccess { log.i { "Resolved \"$name\" to ${it.host}" } }
                        .onFailure { error ->
                            log.w { "Could not resolve \"$name\": ${error.message ?: error::class.simpleName}" }
                            synchronizedServices { failures += "$name: ${error.message ?: error::class.simpleName}" }
                        }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull().also { resolved ->
                if (resolved.isEmpty() && found.isNotEmpty()) {
                    lastDiscoveryNote = "found ${found.size} Android TV(s) but could not resolve: ${failures.joinToString("; ")}"
                }
            }
        }
    }

    private val servicesLock = kotlinx.atomicfu.locks.SynchronizedObject()
    private inline fun <T> synchronizedServices(block: () -> T): T = kotlinx.atomicfu.locks.synchronized(servicesLock, block)

    override suspend fun pairAndroidTv(host: String, attempt: TvHandoffAttempt, readCode: suspend () -> String?): String {
        requirePrivateTvAddress(host)
        val identity = tvIdentity()
        var server: PeerCertificate? = null
        val connection = NwConnection.open(nw_endpoint_create_host(host, "6467"),
            tlsParameters(identity.secIdentity, verify = { server = it; true }))
        check(attempt.attach(connection)) { "Pairing cancelled." }
        connection.use {
            suspend fun receive(expected: Int) = withReadTimeout {
                AndroidTvPairing.requireResponse(connection.readFrame(), expected)
            }
            connection.writeFrame(AndroidTvPairing.request("Nuvio iOS"))
            receive(11)
            connection.writeFrame(AndroidTvPairing.options())
            receive(20)
            connection.writeFrame(AndroidTvPairing.configuration())
            receive(31)
            val code = AndroidTvPairing.normalizedCode(readCode() ?: error("Pairing cancelled."))
            val peer = server ?: throw TvHandoffIdentityFailure("TV certificate unavailable")
            val serverKey = peer.rsaPkcs1 ?: throw TvHandoffIdentityFailure("TV certificate is not RSA")
            val clientKey = TvHandoffDer.pkcs1FromSubjectPublicKeyInfo(identity.subjectPublicKeyInfo)
            val secret = AndroidTvPairing.secretFor(TvHandoffDer.rsaPublicKey(clientKey), TvHandoffDer.rsaPublicKey(serverKey),
                code, ::iosSha256)
            connection.writeFrame(AndroidTvPairing.secret(secret))
            receive(41)
            return peer.fingerprint(::iosSha256)
        }
    }

    override suspend fun launchAndroidTv(tv: PairedTv, link: String, attempt: TvHandoffAttempt) {
        requirePrivateTvAddress(tv.host)
        val identity = tvIdentity()
        val connection = NwConnection.open(nw_endpoint_create_host(tv.host, "6466"),
            tlsParameters(identity.secIdentity, verify = { it.fingerprint(::iosSha256) == tv.certificatePin }))
        val reader = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resource = AutoCloseable { reader.cancel(); connection.close() }
        if (!attempt.attach(resource)) return
        try {
            val session = AndroidTvRemoteSession()
            suspend fun process(): Boolean {
                val (replies, started) = session.process(connection.readFrame())
                replies.forEach { connection.writeFrame(it) }
                return started
            }
            withTimeoutOrNull(15_000) { while (!process()) Unit }
                ?: throw TvHandoffTimeout("Sony did not accept the remote connection.")
            connection.writeFrame(session.wakeKey())
            if (session.reportedPower == false) {
                withTimeoutOrNull(15_000) { while (session.reportedPower == false) process() }
                if (session.reportedPower != true) throw TvHandoffTimeout("Sony did not report waking up")
            }
            connection.writeFrame(session.appLink(link))
            // Keep answering pings until the attempt closes this connection.
            reader.launch { runCatching { while (true) process() }; connection.close() }
        } catch (error: Throwable) {
            resource.close()
            throw error
        }
    }

    /** Null when the attempt was cancelled while connecting; the connection is then already closed. */
    private suspend fun webOs(host: String, pin: String?, attempt: TvHandoffAttempt, observed: (PeerCertificate) -> Unit = {}): NwConnection? {
        requirePrivateTvAddress(host)
        val connection = NwConnection.open(nw_endpoint_create_url("wss://$host:${WebOsProtocol.PORT}"),
            withWebSocket(tlsParameters(null, verify = { peer ->
                observed(peer)
                pin == null || peer.fingerprint(::iosSha256) == pin
            })))
        return connection.takeIf { attempt.attach(it) }
    }

    override suspend fun pairWebOs(host: String, attempt: TvHandoffAttempt): WebOsPairing {
        var observedPin = ""
        val opened = webOs(host, null, attempt) { observedPin = it.fingerprint(::iosSha256) } ?: error("Pairing cancelled.")
        opened.use { connection ->
            connection.sendText(WebOsProtocol.register(null))
            val key = withTimeoutOrNull(60_000) {
                var issued: String? = null
                while (issued == null) issued = WebOsProtocol.registrationKey(WebOsProtocol.parse(connection.receiveMessage()))
                issued
            } ?: error("Approve the connection on your LG TV, then try again.")
            check(observedPin.isNotBlank()) { "LG did not complete pairing." }
            return WebOsPairing(key, observedPin)
        }
    }

    override suspend fun launchWebOs(tv: PairedTv, parameters: JsonObject, attempt: TvHandoffAttempt) {
        val connection = webOs(tv.host, tv.certificatePin, attempt) ?: return
        try {
            connection.sendText(WebOsProtocol.register(tv.clientKey))
            withTimeoutOrNull(60_000) {
                while (WebOsProtocol.registrationKey(WebOsProtocol.parse(connection.receiveMessage())) == null) Unit
            } ?: error("Approve the connection on your LG TV, then try again.")
            if (!attempt.isActive) return
            connection.sendText(WebOsProtocol.launch(tv.appId, parameters))
            withTimeoutOrNull(15_000) {
                while (!WebOsProtocol.launchAcknowledged(WebOsProtocol.parse(connection.receiveMessage()))) Unit
            } ?: error("LG did not acknowledge launching Nuvio.")
        } catch (error: Throwable) {
            connection.close()
            throw error
        }
    }

    override suspend fun startServer(
        host: String,
        request: TvHandoffRequest,
        onUpdate: (TvHandoffUpdate) -> Unit,
    ): TvHandoffServer {
        val target = requirePrivateTvAddress(host)
        val identity = tvIdentity()
        return IosHandoffServer.start(identity, target, localAddressToward(target), request, onUpdate)
    }

    override suspend fun wake(tv: PairedTv) = withContext(Dispatchers.Default) {
        val packet = tvWakePacket(tv.mac)
        repeat(3) { sendUdp(requirePrivateTvAddress(tv.host), 9, packet) }
    }

    override fun classify(error: Throwable): TvHandoffFailure {
        val chain = generateSequence(error) { it.cause }.take(16).toList()
        chain.firstNotNullOfOrNull { (it as? NwFailure)?.kind }?.let { return it }
        return when {
            chain.any { it is TvHandoffIdentityFailure } -> TvHandoffFailure.IdentityFailure
            chain.any { it is TvHandoffTimeout || it is TimeoutCancellationException } -> TvHandoffFailure.Timeout
            else -> TvHandoffFailure.Other
        }
    }
}

private suspend fun <T> withReadTimeout(block: suspend () -> T): T = try {
    withTimeout(10_000) { block() }
} catch (_: TimeoutCancellationException) {
    throw TvHandoffTimeout("TV did not respond")
}

/** The sender's single-request HTTPS endpoint, bound to the interface that routes to the TV. */
private class IosHandoffServer private constructor(
    private val listener: NSObject,
    private val router: TvHandoffHttpRouter,
    override val launchParameters: JsonObject,
    private val scope: CoroutineScope,
) : TvHandoffServer {
    private val closed = atomic(false)

    override fun cancelAndCheckPlayback(): Boolean = router.cancelAndCheckPlayback()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        router.cancel()
        nw_listener_cancel(listener)
        scope.cancel()
    }

    companion object {
        suspend fun start(
            identity: IosTvIdentity,
            target: String,
            localAddress: String,
            request: TvHandoffRequest,
            onUpdate: (TvHandoffUpdate) -> Unit,
        ): IosHandoffServer {
            val token = bytesToHex(iosRandomBytes(32))
            val router = TvHandoffHttpRouter(request, token, onUpdate)
            val parameters = tlsParameters(identity.secIdentity, verify = { true })
            nw_parameters_set_local_endpoint(parameters, nw_endpoint_create_host(localAddress, "0"))
            val listener = nw_listener_create(parameters) ?: throw NwFailure(TvHandoffFailure.Other, "Unable to start handoff service")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val active = atomic(0)
            nw_listener_set_new_connection_handler(listener) { incoming ->
                if (incoming == null) return@nw_listener_set_new_connection_handler
                val from = endpointAddress(nw_connection_copy_endpoint(incoming))?.removePrefix("::ffff:")
                if (active.incrementAndGet() > 8) {
                    active.decrementAndGet()
                    platform.Network.nw_connection_cancel(incoming)
                    return@nw_listener_set_new_connection_handler
                }
                scope.launch {
                    try {
                        withTimeout(15_000) { serve(NwConnection.accept(incoming), router, from == target) }
                    } catch (_: Throwable) {
                        platform.Network.nw_connection_cancel(incoming)
                    } finally { active.decrementAndGet() }
                }
            }
            val port = try {
                withTimeout(5_000) {
                    suspendCancellableCoroutine<Int> { continuation ->
                        val settled = atomic(false)
                        nw_listener_set_state_changed_handler(listener) { state, error ->
                            when (state) {
                                nw_listener_state_ready -> if (settled.compareAndSet(false, true))
                                    continuation.resume(nw_listener_get_port(listener).toInt())
                                nw_listener_state_failed -> if (settled.compareAndSet(false, true))
                                    continuation.resumeWithException(nwFailure(error))
                            }
                        }
                        continuation.invokeOnCancellation { nw_listener_cancel(listener) }
                        nw_listener_set_queue(listener, tvHandoffQueue)
                        nw_listener_start(listener)
                    }
                }
            } catch (error: Throwable) {
                nw_listener_cancel(listener); scope.cancel()
                throw if (error is TimeoutCancellationException) TvHandoffTimeout("Handoff service did not start") else error
            }
            val parametersJson = tvHandoffLaunchParameters(localAddress, port, identity.certificateDer,
                identity.subjectPublicKeyInfo, token, request, ::iosSha256)
            return IosHandoffServer(listener, router, parametersJson, scope)
        }

        private suspend fun serve(connection: NwConnection, router: TvHandoffHttpRouter, fromTarget: Boolean) = connection.use {
            val head = connection.readUntil("\r\n\r\n".encodeToByteArray(), 8_192).decodeToString()
            val lines = head.split("\r\n")
            val requestLine = lines.first().split(' ')
            require(requestLine.size >= 2)
            val headers = lines.drop(1).mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.take(colon).trim().lowercase() to line.substring(colon + 1).trim()
            }.toMap()
            val length = headers["content-length"]?.toLongOrNull() ?: 0L
            require(length >= 0)
            val body = connection.readExact(minOf(length, TvHandoffHttpRouter.MAX_BODY + 1L).toInt())
            val reply = router.handle(requestLine[0], requestLine[1].substringBefore('?'), headers["authorization"], fromTarget, body)
            val content = reply.body?.encodeToByteArray() ?: ByteArray(0)
            val response = buildString {
                append("HTTP/1.1 ${reply.status} ${reasonPhrase(reply.status)}\r\n")
                if (reply.body != null) append("Content-Type: application/json\r\nCache-Control: no-store\r\n")
                append("Content-Length: ${content.size}\r\nConnection: close\r\n\r\n")
            }.encodeToByteArray() + content
            connection.send(response)
        }

        private fun reasonPhrase(status: Int) = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; 410 -> "Gone"
            else -> "Error"
        }
    }
}

/** The local IPv4 address the system routes toward [target]; connecting UDP sends nothing. */
private fun localAddressToward(target: String): String = memScoped {
    val descriptor = socket(AF_INET, SOCK_DGRAM, 0)
    check(descriptor >= 0) { "Unable to open a network socket" }
    try {
        val address = ipv4SocketAddress(target, 9)
        if (connect(descriptor, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
            throw posixFailure(errno)
        }
        val local = alloc<sockaddr_in>()
        val length = alloc<socklen_tVar>().also { it.value = sizeOf<sockaddr_in>().convert() }
        check(getsockname(descriptor, local.ptr.reinterpret<sockaddr>(), length.ptr) == 0) { "Unable to read local address" }
        // s_addr holds network byte order; its first octet is the lowest byte in memory.
        val raw = local.sin_addr.s_addr
        (0 until 4).joinToString(".") { ((raw shr (it * 8)) and 0xffu).toString() }
    } finally { close(descriptor) }
}

private fun sendUdp(host: String, port: Int, payload: ByteArray) = memScoped {
    val descriptor = socket(AF_INET, SOCK_DGRAM, 0)
    check(descriptor >= 0) { "Unable to open a network socket" }
    try {
        val address = ipv4SocketAddress(host, port)
        val sent = payload.usePinned {
            sendto(descriptor, it.addressOf(0), payload.size.convert(), 0, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
        if (sent < 0) throw posixFailure(errno)
    } finally { close(descriptor) }
}

private fun kotlinx.cinterop.MemScope.ipv4SocketAddress(host: String, port: Int): sockaddr_in {
    val address = alloc<sockaddr_in>()
    address.sin_len = sizeOf<sockaddr_in>().convert()
    address.sin_family = AF_INET.convert()
    address.sin_port = (((port and 0xff) shl 8) or ((port shr 8) and 0xff)).convert()
    val octets = requirePrivateTvAddress(host).split('.').map { it.toUInt() }
    address.sin_addr.s_addr = octets[0] or (octets[1] shl 8) or (octets[2] shl 16) or (octets[3] shl 24)
    return address
}

private fun posixFailure(code: Int): NwFailure = when (code) {
    platform.posix.EPERM, platform.posix.EACCES -> NwFailure(TvHandoffFailure.LocalNetworkDenied, "Network permission denied")
    platform.posix.EHOSTUNREACH, platform.posix.ENETUNREACH, platform.posix.EHOSTDOWN ->
        NwFailure(TvHandoffFailure.Unreachable, "No route to TV")
    else -> NwFailure(TvHandoffFailure.Other, "Network error $code")
}
