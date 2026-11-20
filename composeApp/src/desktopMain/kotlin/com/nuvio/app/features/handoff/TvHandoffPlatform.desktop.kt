package com.nuvio.app.features.handoff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal actual fun platformTvHandoffTransport(): TvHandoffTransport? = DesktopTvHandoffTransport

/** JVM sockets, BouncyCastle identity and owner-only files under ~/.nuvio/tv-handoff. */
private object DesktopTvHandoffTransport : TvHandoffTransport {
    private val directory = Path.of(System.getProperty("user.home"), ".nuvio", "tv-handoff")
    private val identityLock = Any()
    @Volatile private var cachedIdentity: TvIdentity? = null

    private fun tvIdentity(): TvIdentity = cachedIdentity ?: synchronized(identityLock) {
        cachedIdentity ?: TvIdentity(directory).also { cachedIdentity = it }
    }

    override val localNetworkSettingsHint =
        "Allow Nuvio in macOS Settings → Privacy & Security → Local Network, then retry."

    override fun readFile(name: String): String? =
        directory.resolve(name).takeIf(Files::exists)?.let(Files::readString)

    override fun writeFile(name: String, text: String) = privateWrite(directory.resolve(name), text.toByteArray())

    override fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    override suspend fun identity(): TvSenderIdentity = runInterruptible(Dispatchers.IO) {
        val certificate = tvIdentity().certificate
        TvSenderIdentity(certificate.encoded, certificate.publicKey.encoded)
    }

    override suspend fun discover(known: List<DiscoveredTv>): List<DiscoveredTv> =
        runInterruptible(Dispatchers.IO) { TvDiscovery.discover(known) }

    override suspend fun pairAndroidTv(host: String, attempt: TvHandoffAttempt, readCode: suspend () -> String?): String =
        runInterruptible(Dispatchers.IO) {
            AndroidTvRemote(tvIdentity()).pair(host) {
                runBlocking { readCode() } ?: error("Pairing cancelled.")
            }
        }

    override suspend fun pairWebOs(host: String, attempt: TvHandoffAttempt): WebOsPairing =
        runInterruptible(Dispatchers.IO) {
            WebOsRemote(host).use { remote ->
                check(attempt.attach(remote)) { "Pairing cancelled." }
                remote.register(null)
            }
        }

    override suspend fun startServer(
        host: String,
        request: TvHandoffRequest,
        onUpdate: (TvHandoffUpdate) -> Unit,
    ): TvHandoffServer = runInterruptible(Dispatchers.IO) {
        LocalHandoffServer(tvIdentity(), privateTvAddress(host), request, onUpdate)
    }

    override suspend fun launchAndroidTv(tv: PairedTv, link: String, attempt: TvHandoffAttempt) {
        runInterruptible(Dispatchers.IO) {
            attempt.attach(AndroidTvRemote(tvIdentity()).launch(tv.host, tv.certificatePin, link))
        }
    }

    override suspend fun launchWebOs(tv: PairedTv, parameters: JsonObject, attempt: TvHandoffAttempt) {
        runInterruptible(Dispatchers.IO) {
            val remote = WebOsRemote(tv.host, tv.certificatePin)
            if (attempt.attach(remote)) {
                try {
                    remote.register(tv.clientKey)
                    if (attempt.isActive) remote.launch(tv.appId, parameters)
                } catch (error: Exception) { remote.close(); throw error }
            }
        }
    }

    override suspend fun wake(tv: PairedTv) {
        runInterruptible(Dispatchers.IO) { sendTvWakePackets(privateTvAddress(tv.host), tv.mac) }
    }

    override fun classify(error: Throwable): TvHandoffFailure = when {
        error.hasLocalNetworkDenial() -> TvHandoffFailure.LocalNetworkDenied
        generateSequence(error) { it.cause }.take(8).any { it is java.net.NoRouteToHostException } ->
            TvHandoffFailure.Unreachable
        error.hasTvIdentityFailure() -> TvHandoffFailure.IdentityFailure
        error is java.net.SocketTimeoutException || error is java.net.ConnectException || error is TvHandoffTimeout ->
            TvHandoffFailure.Timeout
        else -> TvHandoffFailure.Other
    }
}
