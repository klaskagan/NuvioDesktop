package com.nuvio.app.features.handoff

/** WebSocket transport errors retain the TLS failure as a nested cause. */
internal fun Throwable.hasTvIdentityFailure(): Boolean =
    generateSequence(this) { it.cause }.take(16).any {
        it is javax.net.ssl.SSLException || it is SecurityException || it is TvHandoffIdentityFailure
    }

internal fun Throwable.hasLocalNetworkDenial(): Boolean =
    generateSequence(this) { it.cause }.take(8).any {
        it is java.net.SocketException &&
            (it.message.orEmpty().contains("permission denied", ignoreCase = true) ||
             it.message.orEmpty().contains("operation not permitted", ignoreCase = true))
    }
