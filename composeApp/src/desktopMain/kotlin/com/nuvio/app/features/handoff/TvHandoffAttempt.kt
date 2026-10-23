package com.nuvio.app.features.handoff

import java.io.Closeable

/** Owns one transfer's resources, including connections returned after cancellation. */
internal class TvHandoffAttempt : Closeable {
    @Volatile var receiverReached = false
    private val lock = Any()
    private var stopped = false
    private val resources = mutableListOf<Closeable>()
    private var cancelPlayback: (() -> Boolean)? = null
    private var playbackCommitted = false

    val isActive: Boolean get() = synchronized(lock) { !stopped }

    fun attach(resource: Closeable, cancelPlayback: (() -> Boolean)? = null): Boolean {
        val accepted = synchronized(lock) {
            if (stopped) false else {
                resources += resource
                if (cancelPlayback != null) this.cancelPlayback = cancelPlayback
                true
            }
        }
        if (!accepted) resource.close()
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
