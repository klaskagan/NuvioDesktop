@file:OptIn(ExperimentalForeignApi::class)

package com.nuvio.app.features.handoff

import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.Network.nw_parameters_create
import platform.Network.nw_protocol_stack_set_transport_protocol
import platform.Network.nw_tcp_create_options
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_copy_current_path
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_receive_message
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_state_waiting
import platform.Network.nw_content_context_create
import platform.Network.nw_content_context_set_metadata_for_protocol
import platform.Network.nw_endpoint_copy_address_string
import platform.Network.nw_error_domain_posix
import platform.Network.nw_error_domain_tls
import platform.Network.nw_error_get_error_code
import platform.Network.nw_error_get_error_domain
import platform.Network.nw_ip_options_set_version
import platform.Network.nw_ip_version_4
import platform.Network.nw_parameters_copy_default_protocol_stack
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_path_copy_effective_remote_endpoint
import platform.Network.nw_path_get_unsatisfied_reason
import platform.Network.nw_path_unsatisfied_reason_local_network_denied
import platform.Network.nw_protocol_stack_copy_internet_protocol
import platform.Network.nw_protocol_stack_prepend_application_protocol
import platform.Network.nw_tcp_options_set_connection_timeout
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Network.nw_ws_create_metadata
import platform.Network.nw_ws_create_options
import platform.Network.nw_ws_opcode_text
import platform.Network.nw_ws_options_set_auto_reply_ping
import platform.Network.nw_ws_options_set_maximum_message_size
import platform.Network.nw_ws_version_13
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateCopyKey
import platform.Security.SecCertificateRef
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.sec_protocol_options_set_local_identity
import platform.Security.sec_protocol_options_set_verify_block
import platform.Security.sec_trust_copy_ref
import platform.darwin.NSObject
import platform.darwin.dispatch_data_apply
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_get_size
import platform.darwin.dispatch_queue_create
import platform.posix.free
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal val tvHandoffQueue: NSObject = dispatch_queue_create("com.nuvio.tv-handoff", null)!!

/** A Network.framework failure, classified like the desktop JVM socket exceptions. */
internal class NwFailure(val kind: TvHandoffFailure, message: String) : Exception(message)

internal class PeerCertificate(val der: ByteArray, val rsaPkcs1: ByteArray?) {
    fun fingerprint(sha256: (ByteArray) -> ByteArray): String = bytesToHex(sha256(der))
}

internal fun ByteArray.toDispatchData(): NSObject? =
    if (isEmpty()) null else usePinned { dispatch_data_create(it.addressOf(0), size.toULong(), null, null) }

internal fun NSObject.dispatchBytes(): ByteArray {
    val result = ByteArray(dispatch_data_get_size(this).toInt())
    dispatch_data_apply(this) { _, offset, buffer, size ->
        val bytes = buffer?.reinterpret<ByteVar>()?.readBytes(size.toInt()) ?: ByteArray(0)
        bytes.copyInto(result, offset.toInt())
        true
    }
    return result
}

internal fun CFDataRef.bytes(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    val pointer = CFDataGetBytePtr(this) ?: return ByteArray(0)
    return pointer.reinterpret<ByteVar>().readBytes(length)
}

internal fun nwFailure(error: NSObject?, connection: NSObject? = null): Exception {
    val path = connection?.let { nw_connection_copy_current_path(it) }
    if (path != null && nw_path_get_unsatisfied_reason(path) == nw_path_unsatisfied_reason_local_network_denied) {
        return NwFailure(TvHandoffFailure.LocalNetworkDenied, "Local network access denied")
    }
    if (error == null) return NwFailure(TvHandoffFailure.Other, "TV connection failed")
    val code = nw_error_get_error_code(error)
    return when (nw_error_get_error_domain(error)) {
        nw_error_domain_tls -> NwFailure(TvHandoffFailure.IdentityFailure, "TLS failure $code")
        // kDNSServiceErr_PolicyDenied: Bonjour blocked by the Local Network permission.
        platform.Network.nw_error_domain_dns -> if (code == -65570) {
            NwFailure(TvHandoffFailure.LocalNetworkDenied, "Local network access denied")
        } else NwFailure(TvHandoffFailure.Other, "Bonjour error $code")
        nw_error_domain_posix -> when (code) {
            platform.posix.EPERM, platform.posix.EACCES -> NwFailure(TvHandoffFailure.LocalNetworkDenied, "Network permission denied")
            platform.posix.EHOSTUNREACH, platform.posix.ENETUNREACH, platform.posix.EHOSTDOWN ->
                NwFailure(TvHandoffFailure.Unreachable, "No route to TV")
            platform.posix.ECONNREFUSED, platform.posix.ETIMEDOUT -> NwFailure(TvHandoffFailure.Timeout, "TV did not respond")
            else -> NwFailure(TvHandoffFailure.Other, "Network error $code")
        }
        else -> NwFailure(TvHandoffFailure.Other, "Network error $code")
    }
}

/** Reads the leaf certificate from a TLS handshake, before trust is decided. */
internal fun peerCertificate(trust: NSObject?): PeerCertificate? {
    val secTrust = sec_trust_copy_ref(trust) ?: return null
    try {
        val chain = SecTrustCopyCertificateChain(secTrust) ?: return null
        try {
            if (CFArrayGetCount(chain) < 1) return null
            val leaf: SecCertificateRef = CFArrayGetValueAtIndex(chain, 0)?.reinterpret() ?: return null
            val data = SecCertificateCopyData(leaf) ?: return null
            val der = try { data.bytes() } finally { CFRelease(data) }
            val key = SecCertificateCopyKey(leaf)
            val pkcs1 = key?.let {
                try {
                    SecKeyCopyExternalRepresentation(it, null)?.let { external ->
                        try { external.bytes() } finally { CFRelease(external) }
                    }
                } finally { CFRelease(it) }
            }
            return PeerCertificate(der, pkcs1)
        } finally { CFRelease(chain) }
    } finally { CFRelease(secTrust) }
}

/**
 * TLS over TCP (IPv4). [verify] replaces system trust: TVs use self-signed certificates, so callers
 * pin them by fingerprint. [identity] presents the sender's client certificate.
 */
internal fun tlsParameters(
    identity: NSObject?,
    verify: (PeerCertificate) -> Boolean,
    connectTimeoutSeconds: UInt = 5u,
): NSObject = nw_parameters_create_secure_tcp({ options ->
    val security = nw_tls_copy_sec_protocol_options(options)
    if (identity != null) sec_protocol_options_set_local_identity(security, identity)
    sec_protocol_options_set_verify_block(security, { _, trust, complete ->
        val accepted = runCatching { peerCertificate(trust)?.let(verify) == true }.getOrDefault(false)
        complete?.invoke(accepted)
    }, tvHandoffQueue)
}, { tcp -> nw_tcp_options_set_connection_timeout(tcp, connectTimeoutSeconds) })!!.also(::preferIpv4)

/**
 * Plain TCP. Built from explicit options: Network.framework's macro sentinels
 * (`NW_PARAMETERS_DISABLE_PROTOCOL`, `NW_CONNECTION_*_CONTEXT`) do not survive Kotlin/Native
 * bridging, and using them aborted the app on device.
 */
internal fun tcpParameters(connectTimeoutSeconds: UInt = 5u): NSObject {
    val parameters = nw_parameters_create()!!
    val tcp = nw_tcp_create_options()
    nw_tcp_options_set_connection_timeout(tcp, connectTimeoutSeconds)
    nw_protocol_stack_set_transport_protocol(nw_parameters_copy_default_protocol_stack(parameters), tcp)
    return parameters.also(::preferIpv4)
}

internal fun withWebSocket(parameters: NSObject): NSObject = parameters.also {
    val options = nw_ws_create_options(nw_ws_version_13)
    nw_ws_options_set_auto_reply_ping(options, true)
    nw_ws_options_set_maximum_message_size(options, WebOsProtocol.MAX_MESSAGE.toULong())
    nw_protocol_stack_prepend_application_protocol(nw_parameters_copy_default_protocol_stack(it), options)
}

private fun preferIpv4(parameters: NSObject) {
    val ip = nw_protocol_stack_copy_internet_protocol(nw_parameters_copy_default_protocol_stack(parameters))
    if (ip != null) nw_ip_options_set_version(ip, nw_ip_version_4)
}

internal fun endpointAddress(endpoint: NSObject?): String? {
    val pointer: CPointer<ByteVar> = endpoint?.let { nw_endpoint_copy_address_string(it) } ?: return null
    return try { pointer.toKString() } finally { free(pointer) }
}

/** One Network.framework connection with suspending, cancellable I/O. Closing cancels it. */
internal class NwConnection private constructor(private val connection: NSObject) : AutoCloseable {
    private var buffer = ByteArray(0)
    private var offset = 0
    private val closed = atomic(false)

    val remoteAddress: String?
        get() = nw_connection_copy_current_path(connection)?.let { endpointAddress(nw_path_copy_effective_remote_endpoint(it)) }

    private suspend fun start(timeoutMs: Long) {
        try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val settled = atomic(false)
                    nw_connection_set_state_changed_handler(connection) { state, error ->
                        val failure = when (state) {
                            nw_connection_state_ready -> null
                            nw_connection_state_waiting, nw_connection_state_failed -> nwFailure(error, connection)
                            nw_connection_state_cancelled -> TvHandoffEndOfStream()
                            else -> return@nw_connection_set_state_changed_handler
                        }
                        if (!settled.compareAndSet(false, true)) return@nw_connection_set_state_changed_handler
                        if (failure == null) continuation.resume(Unit) else continuation.resumeWithException(failure)
                    }
                    continuation.invokeOnCancellation { close() }
                    nw_connection_set_queue(connection, tvHandoffQueue)
                    nw_connection_start(connection)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            close()
            throw TvHandoffTimeout("TV did not respond")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    /** Sends one message; the caller closes the connection after a final response. */
    suspend fun send(bytes: ByteArray, context: NSObject? = null) {
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { close() }
            nw_connection_send(connection, bytes.toDispatchData(),
                context ?: nw_content_context_create("send"), true) { error ->
                if (error == null) continuation.resume(Unit) else continuation.resumeWithException(nwFailure(error, connection))
            }
        }
    }

    suspend fun sendText(text: String) {
        val context = nw_content_context_create("text")
        nw_content_context_set_metadata_for_protocol(context, nw_ws_create_metadata(nw_ws_opcode_text))
        send(text.encodeToByteArray(), context)
    }

    private suspend fun receiveChunk(): ByteArray = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        nw_connection_receive(connection, 1u, 65_536u) { content, _, isComplete, error ->
            when {
                content != null -> continuation.resume(content.dispatchBytes())
                error != null -> continuation.resumeWithException(nwFailure(error, connection))
                isComplete -> continuation.resumeWithException(TvHandoffEndOfStream())
                else -> continuation.resume(ByteArray(0))
            }
        }
    }

    /** A complete WebSocket message, decoded as text. */
    suspend fun receiveMessage(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        nw_connection_receive_message(connection) { content, _, _, error ->
            when {
                content != null -> continuation.resume(content.dispatchBytes().decodeToString())
                error != null -> continuation.resumeWithException(nwFailure(error, connection))
                else -> continuation.resumeWithException(TvHandoffEndOfStream())
            }
        }
    }

    suspend fun readByte(): Int {
        while (offset >= buffer.size) {
            buffer = receiveChunk(); offset = 0
        }
        return buffer[offset++].toInt() and 255
    }

    suspend fun readExact(count: Int): ByteArray {
        val result = ByteArray(count)
        var filled = 0
        while (filled < count) {
            if (offset >= buffer.size) { buffer = receiveChunk(); offset = 0; continue }
            val take = minOf(count - filled, buffer.size - offset)
            buffer.copyInto(result, filled, offset, offset + take)
            filled += take; offset += take
        }
        return result
    }

    /** Bytes up to and including [delimiter], bounded by [limit]. */
    suspend fun readUntil(delimiter: ByteArray, limit: Int): ByteArray {
        val result = ArrayList<Byte>()
        while (true) {
            result += readByte().toByte()
            require(result.size <= limit) { "Request too large" }
            if (result.size >= delimiter.size &&
                (delimiter.indices).all { result[result.size - delimiter.size + it] == delimiter[it] }) return result.toByteArray()
        }
    }

    suspend fun readFrame(): ByteArray =
        readExact(RemoteWire.requireFrameLength(RemoteWire.readVarint { readByte() }))

    suspend fun writeFrame(message: ByteArray) = send(RemoteWire.frame(message))

    override fun close() {
        if (closed.compareAndSet(false, true)) nw_connection_cancel(connection)
    }

    companion object {
        suspend fun open(endpoint: NSObject?, parameters: NSObject, timeoutMs: Long = 5_000): NwConnection {
            val created = nw_connection_create(endpoint, parameters) ?: throw NwFailure(TvHandoffFailure.Other, "Invalid TV address")
            return NwConnection(created).also { it.start(timeoutMs) }
        }

        /** Starts a connection accepted by a listener. */
        suspend fun accept(connection: NSObject, timeoutMs: Long = 5_000): NwConnection =
            NwConnection(connection).also { it.start(timeoutMs) }
    }
}
