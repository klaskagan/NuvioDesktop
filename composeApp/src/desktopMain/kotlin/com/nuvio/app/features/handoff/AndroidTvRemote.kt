package com.nuvio.app.features.handoff

import java.io.Closeable
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/** Android TV Remote v2, independent of ADB and Nuvio's account backend. */
internal class AndroidTvRemote(private val identity: TvIdentity) {
    private var features = 551L
    private var reportedPower: Boolean? = null
    private fun socket(host: String, port: Int, pin: String?): SSLSocket =
        (identity.context(pin).socketFactory.createSocket() as SSLSocket).apply {
            connect(InetSocketAddress(host, port), 5_000)
            soTimeout = 10_000
            startHandshake()
        }

    fun pair(host: String, readCode: () -> String): String = socket(host, 6467, null).use { socket ->
        fun send(field: Int, value: ByteArray) = RemoteWire.writeFrame(socket.outputStream,
            RemoteWire.number(1, 2) + RemoteWire.number(2, 200) + RemoteWire.bytes(field, value))
        fun receive(expected: Int) {
            val fields = RemoteWire.fields(RemoteWire.readFrame(socket.inputStream))
            check(fields.any { it.number == 2 && it.value == 200L } && fields.any { it.number == expected }) {
                "The TV rejected pairing. Start pairing again."
            }
        }
        send(10, RemoteWire.text(1, "atvremote") + RemoteWire.text(2, "Nuvio Desktop"))
        receive(11)
        val encoding = RemoteWire.number(1, 3) + RemoteWire.number(2, 6)
        send(20, RemoteWire.bytes(1, encoding) + RemoteWire.number(3, 1))
        receive(20)
        send(30, RemoteWire.bytes(1, encoding) + RemoteWire.number(2, 1))
        receive(31)
        val code = readCode().trim()
        require(code.matches(Regex("[0-9A-Fa-f]{6}"))) { "Enter the six-character code shown on your TV." }
        val server = socket.session.peerCertificates.first() as X509Certificate
        val clientKey = identity.certificate.publicKey as RSAPublicKey
        val serverKey = server.publicKey as RSAPublicKey
        fun BigInteger.unsigned() = toByteArray().let { if (it.first() == 0.toByte()) it.drop(1).toByteArray() else it }
        val secret = MessageDigest.getInstance("SHA-256").digest(
            clientKey.modulus.unsigned() + clientKey.publicExponent.unsigned() +
                serverKey.modulus.unsigned() + serverKey.publicExponent.unsigned() +
                code.drop(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )
        require(secret.first().toInt() and 255 == code.take(2).toInt(16)) { "Pairing code did not match this TV." }
        send(40, RemoteWire.bytes(1, secret))
        receive(41)
        server.fingerprint()
    }

    fun launch(host: String, pin: String, link: String): Closeable {
        reportedPower = null
        val socket = socket(host, 6466, pin)
        try {
            val deadline = System.nanoTime() + 15_000_000_000L
            var started = false
            while (!started && System.nanoTime() < deadline) {
                started = process(socket)
            }
            check(started) { "Sony did not accept the remote connection." }
            RemoteWire.writeFrame(socket.outputStream,
                RemoteWire.bytes(10, RemoteWire.number(1, androidTvWakeKey(reportedPower).toLong()) + RemoteWire.number(2, 3)))
            if (reportedPower == false) {
                val wakeDeadline = System.nanoTime() + 15_000_000_000L
                while (reportedPower == false && System.nanoTime() < wakeDeadline) process(socket)
                if (reportedPower != true) throw SocketTimeoutException("Sony did not report waking up")
            }
            RemoteWire.writeFrame(socket.outputStream, RemoteWire.bytes(90, RemoteWire.text(1, link)))
            Thread({
                while (!socket.isClosed) {
                    try { process(socket) } catch (_: SocketTimeoutException) {
                        // Remote sends periodic pings; cancellation closes the socket.
                    } catch (_: Exception) { socket.close() }
                }
            }, "nuvio-tv-remote").apply { isDaemon = true; start() }
            return socket
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }

    private fun process(socket: SSLSocket): Boolean {
        val fields = RemoteWire.fields(RemoteWire.readFrame(socket.inputStream))
        if (fields.any { it.number == 40 }) reportedPower = androidTvReportedPower(fields)
        fields.forEach { field ->
            val body = field.bytes ?: return@forEach
            val reply = when (field.number) {
                1 -> {
                    val supported = RemoteWire.fields(body).firstOrNull { it.number == 1 }?.value ?: 0
                    features = supported and 551L // ping, keys, IME, power, app links
                    check(features and 512L != 0L) { "Sony remote service does not support app links." }
                    RemoteWire.bytes(1, RemoteWire.number(1, features) + RemoteWire.bytes(2,
                        RemoteWire.number(3, 1) + RemoteWire.text(4, "1") +
                            RemoteWire.text(5, "nuvio") + RemoteWire.text(6, "1.0")))
                }
                2 -> RemoteWire.bytes(2, RemoteWire.number(1, features))
                8 -> RemoteWire.bytes(9, RemoteWire.number(1,
                    RemoteWire.fields(body).firstOrNull { it.number == 1 }?.value ?: 0))
                else -> null
            }
            if (reply != null) RemoteWire.writeFrame(socket.outputStream, reply)
        }
        return fields.any { it.number == 40 }
    }
}

/** POWER is a toggle: use it only after this authenticated handshake reports off. */
internal fun androidTvWakeKey(reportedPower: Boolean?): Int = if (reportedPower == false) 26 else 224

internal fun androidTvReportedPower(fields: List<RemoteWire.Field>): Boolean? = try {
    val body = fields.singleOrNull { it.number == 40 }?.bytes
    if (body == null) null else {
        val values = RemoteWire.fields(body).filter { it.number == 1 }
        val value = when (values.size) { 0 -> 0L; 1 -> values.single().value; else -> null }
        when (value) { 0L -> false; 1L -> true; else -> null }
    }
} catch (_: Exception) { null }

internal fun androidTvHandoffLink(appId: String, launch: String): String {
    val scheme = when (appId) {
        "space.nuvio.nativelegacy.strictdebug" -> "nuvio-legacy-handoff-debug"
        "space.nuvio.nativelegacy" -> "nuvio-legacy-handoff"
        else -> "nuvio-handoff"
    }
    return "$scheme://handoff?launch=" + java.net.URLEncoder.encode(launch, "UTF-8")
}
