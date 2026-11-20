package com.nuvio.app.features.handoff

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock

internal class TvHandoffHttpReply(val status: Int, val body: String? = null)

/**
 * Protocol state of the sender's one-request HTTPS endpoint. Platform servers own TLS, sockets and
 * the remote-address check; this decides every response, so all senders accept identical requests.
 */
internal class TvHandoffHttpRouter(
    val request: TvHandoffRequest,
    private val token: String,
    private val onUpdate: (TvHandoffUpdate) -> Unit,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val lock = SynchronizedObject()
    private val session = TvHandoffSession(request)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(method: String, path: String, authorization: String?, fromTarget: Boolean, body: ByteArray): TvHandoffHttpReply {
        val authenticated = constantTimeEquals(authorization.orEmpty().encodeToByteArray(), "Bearer $token".encodeToByteArray())
        if (!fromTarget || !authenticated) return TvHandoffHttpReply(403)
        return try {
            synchronized(lock) {
                session.expire(now())
                if (session.phase == TvHandoffPhase.CANCELLED || session.phase == TvHandoffPhase.EXPIRED) {
                    return TvHandoffHttpReply(410)
                }
                when {
                    method == "GET" && path == "/v1/handoff" -> TvHandoffHttpReply(200, json.encodeToString(TvHandoffRequest.serializer(), request))
                    method == "POST" && path == "/v1/handoff/status" -> {
                        require(body.size <= MAX_BODY)
                        val update = json.decodeFromString(TvHandoffUpdate.serializer(), body.decodeToString())
                        if (!session.accept(update, now())) TvHandoffHttpReply(409) else {
                            onUpdate(update)
                            TvHandoffHttpReply(200, "{}")
                        }
                    }
                    else -> TvHandoffHttpReply(404)
                }
            }
        } catch (_: Exception) {
            TvHandoffHttpReply(400)
        }
    }

    fun cancel(): Boolean = synchronized(lock) { session.cancel() }
    fun cancelAndCheckPlayback(): Boolean = synchronized(lock) {
        session.cancel()
        session.phase == TvHandoffPhase.PLAYING
    }
    val playbackConfirmed: Boolean get() = synchronized(lock) { session.phase == TvHandoffPhase.PLAYING }

    companion object {
        const val MAX_BODY = 16_384
    }
}

private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    var difference = a.size xor b.size
    for (index in 0 until maxOf(a.size, b.size)) {
        difference = difference or ((a.getOrElse(index) { 0 }.toInt()) xor (b.getOrElse(index) { 0 }.toInt()))
    }
    return difference == 0
}

/** Launch parameters understood by every patched receiver. */
internal fun tvHandoffLaunchParameters(
    host: String,
    port: Int,
    certificateDer: ByteArray,
    subjectPublicKeyInfo: ByteArray,
    token: String,
    request: TvHandoffRequest,
    sha256: (ByteArray) -> ByteArray,
): JsonObject = buildJsonObject {
    put("endpoint", "https://$host:$port/v1/handoff")
    put("certificatePin", bytesToHex(sha256(certificateDer)))
    // Native receivers use libcurl SPKI pinning before sending the bearer token.
    put("publicKeyPin", "sha256//" + Base64.encode(sha256(subjectPublicKeyInfo)))
    put("token", token)
    put("requestId", request.requestId)
    put("targetDeviceId", request.targetDeviceId)
    put("senderDeviceId", request.senderDeviceId)
}

/** Code shown on both devices on the first transfer; Native Legacy displays a base64 SPKI digest. */
internal fun tvHandoffApprovalCode(
    appId: String,
    certificateDer: ByteArray,
    subjectPublicKeyInfo: ByteArray,
    sha256: (ByteArray) -> ByteArray,
): String = if (isNativeLegacyApp(appId)) Base64.encode(sha256(subjectPublicKeyInfo)).take(6)
    else bytesToHex(sha256(certificateDer)).take(6).uppercase()

/** Owns one transfer's resources, including connections returned after cancellation. */
internal class TvHandoffAttempt : AutoCloseable {
    @kotlin.concurrent.Volatile var receiverReached = false
    private val lock = SynchronizedObject()
    private var stopped = false
    private val resources = mutableListOf<AutoCloseable>()
    private var cancelPlayback: (() -> Boolean)? = null
    private var playbackCommitted = false

    val isActive: Boolean get() = synchronized(lock) { !stopped }

    fun attach(resource: AutoCloseable, cancelPlayback: (() -> Boolean)? = null): Boolean {
        val accepted = synchronized(lock) {
            if (stopped) false else {
                resources += resource
                if (cancelPlayback != null) this.cancelPlayback = cancelPlayback
                true
            }
        }
        if (!accepted) runCatching { resource.close() }
        return accepted
    }

    /** Atomically cancels the receiver session or observes its already committed playback. */
    fun cancelAndCheckPlayback(): Boolean {
        val closing = synchronized(lock) {
            if (stopped) return playbackCommitted
            stopped = true
            playbackCommitted = cancelPlayback?.invoke() == true
            resources.toList().also { resources.clear(); cancelPlayback = null }
        }
        closing.asReversed().forEach { runCatching { it.close() } }
        return playbackCommitted
    }

    override fun close() { cancelAndCheckPlayback() }
}

/** Launcher acknowledgements precede app readiness after cold start or network wake. */
internal suspend fun awaitTvReceiver(
    isActive: () -> Boolean,
    receiverReached: () -> Boolean,
    launch: suspend () -> Unit,
    retryable: (Exception) -> Boolean,
    now: () -> Long = ::monotonicNanos,
    pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    val deadline = now() + 60_000_000_000L
    while (isActive() && !receiverReached()) {
        try { launch() } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!isActive()) return
            if (!retryable(error) || now() >= deadline) throw error
        }
        repeat(40) {
            if (!isActive() || receiverReached()) return
            if (now() >= deadline) throw TvHandoffTimeout("Nuvio receiver did not become ready")
            pause(100)
        }
        if (now() >= deadline && !receiverReached()) throw TvHandoffTimeout("Nuvio receiver did not become ready")
    }
}

private val monotonicOrigin = kotlin.time.TimeSource.Monotonic.markNow()
internal fun monotonicNanos(): Long = monotonicOrigin.elapsedNow().inWholeNanoseconds
