package com.nuvio.app.features.handoff

import kotlinx.serialization.json.JsonObject

internal enum class TvHandoffFailure { LocalNetworkDenied, Unreachable, IdentityFailure, Timeout, Other }

internal class TvSenderIdentity(val certificateDer: ByteArray, val subjectPublicKeyInfo: ByteArray)

/** The sender's temporary HTTPS endpoint; closing it cancels the pending session. */
internal interface TvHandoffServer : AutoCloseable {
    val launchParameters: JsonObject
    fun cancelAndCheckPlayback(): Boolean
}

/**
 * Platform networking for Continue on TV. Shared code owns the flow, state and UI; transports own
 * sockets, TLS identity/pinning and private storage. Suspending calls must honour cancellation by
 * closing their connections, and every connection is attached to the given attempt.
 */
internal interface TvHandoffTransport {
    /** Guidance shown when the operating system blocks local-network access. */
    val localNetworkSettingsHint: String

    /** Owner-private storage for pairing records (they include pinned certificates and LG client keys). */
    fun readFile(name: String): String?
    fun writeFile(name: String, text: String)

    fun sha256(bytes: ByteArray): ByteArray
    suspend fun identity(): TvSenderIdentity

    suspend fun discover(known: List<DiscoveredTv>): List<DiscoveredTv>

    /** Why the last [discover] found nothing beyond saved addresses, shown when the list is empty. */
    val lastDiscoveryNote: String? get() = null

    /** Returns the TV certificate fingerprint to pin. [readCode] returns null when cancelled. */
    suspend fun pairAndroidTv(host: String, attempt: TvHandoffAttempt, readCode: suspend () -> String?): String
    suspend fun pairWebOs(host: String, attempt: TvHandoffAttempt): WebOsPairing

    suspend fun startServer(host: String, request: TvHandoffRequest, onUpdate: (TvHandoffUpdate) -> Unit): TvHandoffServer
    suspend fun launchAndroidTv(tv: PairedTv, link: String, attempt: TvHandoffAttempt)
    suspend fun launchWebOs(tv: PairedTv, parameters: JsonObject, attempt: TvHandoffAttempt)

    /** Best-effort Wake-on-LAN for [tv]'s MAC address. */
    suspend fun wake(tv: PairedTv)

    fun classify(error: Throwable): TvHandoffFailure
}

/** Null where Continue on TV is not available. */
internal expect fun platformTvHandoffTransport(): TvHandoffTransport?
