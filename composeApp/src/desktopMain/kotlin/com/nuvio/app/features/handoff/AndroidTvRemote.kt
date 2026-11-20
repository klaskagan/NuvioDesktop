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
    private val session = AndroidTvRemoteSession()
    private fun socket(host: String, port: Int, pin: String?): SSLSocket =
        (identity.context(pin).socketFactory.createSocket() as SSLSocket).apply {
            connect(InetSocketAddress(host, port), 5_000)
            soTimeout = 10_000
            startHandshake()
        }

    fun pair(host: String, readCode: () -> String): String = socket(host, 6467, null).use { socket ->
        fun send(message: ByteArray) = RemoteWire.writeFrame(socket.outputStream, message)
        fun receive(expected: Int) = AndroidTvPairing.requireResponse(RemoteWire.readFrame(socket.inputStream), expected)
        send(AndroidTvPairing.request("Nuvio Desktop"))
        receive(11)
        send(AndroidTvPairing.options())
        receive(20)
        send(AndroidTvPairing.configuration())
        receive(31)
        val code = AndroidTvPairing.normalizedCode(readCode())
        val server = socket.session.peerCertificates.first() as X509Certificate
        val secret = AndroidTvPairing.secretFor(
            (identity.certificate.publicKey as RSAPublicKey).parts(), (server.publicKey as RSAPublicKey).parts(), code,
        ) { MessageDigest.getInstance("SHA-256").digest(it) }
        send(AndroidTvPairing.secret(secret))
        receive(41)
        server.fingerprint()
    }

    fun launch(host: String, pin: String, link: String): Closeable {
        session.resetPower()
        val socket = socket(host, 6466, pin)
        try {
            val deadline = System.nanoTime() + 15_000_000_000L
            var started = false
            while (!started && System.nanoTime() < deadline) {
                started = process(socket)
            }
            check(started) { "Sony did not accept the remote connection." }
            RemoteWire.writeFrame(socket.outputStream, session.wakeKey())
            if (session.reportedPower == false) {
                val wakeDeadline = System.nanoTime() + 15_000_000_000L
                while (session.reportedPower == false && System.nanoTime() < wakeDeadline) process(socket)
                if (session.reportedPower != true) throw SocketTimeoutException("Sony did not report waking up")
            }
            RemoteWire.writeFrame(socket.outputStream, session.appLink(link))
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
        val (replies, started) = session.process(RemoteWire.readFrame(socket.inputStream))
        replies.forEach { RemoteWire.writeFrame(socket.outputStream, it) }
        return started
    }
}

private fun RSAPublicKey.parts(): RsaPublicKeyParts {
    fun BigInteger.unsigned() = toByteArray().let { if (it.first() == 0.toByte()) it.drop(1).toByteArray() else it }
    return RsaPublicKeyParts(modulus.unsigned(), publicExponent.unsigned())
}
