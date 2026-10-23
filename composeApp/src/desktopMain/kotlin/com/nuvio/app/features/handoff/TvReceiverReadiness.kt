package com.nuvio.app.features.handoff

import java.net.SocketTimeoutException

/** Launcher acknowledgements precede app readiness after cold start or network wake. */
internal fun awaitTvReceiver(
    isActive: () -> Boolean,
    receiverReached: () -> Boolean,
    launch: () -> Unit,
    retryable: (Exception) -> Boolean,
    now: () -> Long = System::nanoTime,
    pause: (Long) -> Unit = Thread::sleep,
) {
    val deadline = now() + 60_000_000_000L
    while (isActive() && !receiverReached()) {
        try { launch() } catch (error: Exception) {
            if (!isActive()) return
            if (!retryable(error) || now() >= deadline) throw error
        }
        repeat(40) {
            if (!isActive() || receiverReached()) return
            if (now() >= deadline) throw SocketTimeoutException("Nuvio receiver did not become ready")
            pause(100)
        }
        if (now() >= deadline && !receiverReached())
            throw SocketTimeoutException("Nuvio receiver did not become ready")
    }
}

/** WebSocket transport errors retain the TLS failure as a nested cause. */
internal fun Throwable.hasTvIdentityFailure(): Boolean =
    generateSequence(this) { it.cause }.take(16).any {
        it is javax.net.ssl.SSLException || it is SecurityException
    }
