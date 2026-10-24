package com.nuvio.app.features.tmdb

import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces duplicate visible cards and bounds successful and missing-artwork results. */
internal class LandscapeArtworkCache<V>(
    private val capacity: Int = 256,
    private val now: () -> Long = WatchProgressClock::nowEpochMs,
) {
    private data class Entry<V>(val value: V?, val expiresAt: Long)
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, Entry<V>>()
    private val pending = mutableMapOf<String, CompletableDeferred<V?>>()

    suspend fun get(key: String, load: suspend () -> V?): V? {
        var owner = false
        val request = mutex.withLock {
            entries[key]?.takeIf { it.expiresAt > now() }?.let { return it.value }
            pending[key] ?: CompletableDeferred<V?>().also { pending[key] = it; owner = true }
        }
        if (!owner) return try { request.await() } catch (cancelled: CancellationException) {
            // A disposed card may own the original request. An active waiter can retry it.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            get(key, load)
        }
        try {
            val value = load()
            mutex.withLock {
                entries[key] = Entry(value, now() + if (value == null) 60_000L else 6 * 60 * 60 * 1000L)
                while (entries.size > capacity) entries.remove(entries.keys.first())
                pending.remove(key)
                request.complete(value)
            }
            return value
        } catch (error: Throwable) {
            // Cancellation must release waiters without poisoning the cache.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock { pending.remove(key); request.completeExceptionally(error) }
            }
            throw error
        }
    }
}
