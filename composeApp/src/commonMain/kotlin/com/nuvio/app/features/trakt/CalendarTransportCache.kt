package com.nuvio.app.features.trakt

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.time.isoEpochDay
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** UTC-aligned seven-day batches deduplicate overlapping Home/screen windows. No credentials stored. */
internal class CalendarTransportCache(
    private val account: () -> String?,
    private val fetch: suspend (Boolean, String, Int) -> CalendarBatch,
    private val now: () -> Long = EpisodeReleaseDatePlatform::nowEpochMs,
) {
    private data class Key(val owner: String, val shows: Boolean, val start: String)
    private data class Cached(val batch: CalendarBatch, val time: Long)
    private val lock = SynchronizedObject()
    private var generation = 0L
    private val batches = linkedMapOf<Key, Cached>()
    private val endpointLocks = listOf(Mutex(), Mutex())
    fun invalidate() = synchronized(lock) { generation++; batches.clear() }

    fun expire() = synchronized(lock) { batches.clear() }

    suspend fun load(shows: Boolean, start: String, days: Int): CalendarBatch =
        endpointLocks[if (shows) 0 else 1].withLock {
            val owner = account() ?: throw CalendarAuthenticationException()
            val version = synchronized(lock) { generation }
            val firstDay = isoEpochDay(start)
            val offset = ((firstDay % 7 + 7) % 7).toInt()
            var cursor = calendarDatePlusDays(start, -offset)
            val end = calendarDatePlusDays(start, days)
            val results = mutableListOf<CalendarBatch>()
            while (cursor < end) {
                if (account() != owner || synchronized(lock) { generation != version }) throw CancellationException()
                val key = Key(owner, shows, cursor)
                val cached = synchronized(lock) { batches[key]?.takeIf { now() - it.time < 600_000 } }
                val batch = cached?.batch ?: fetch(shows, cursor, 7).also { result ->
                    synchronized(lock) {
                        if (account() != owner || generation != version) throw CancellationException()
                        batches[key] = Cached(result, now())
                        while (batches.size > 120) batches.remove(batches.keys.first())
                    }
                }
                results.add(batch)
                cursor = calendarDatePlusDays(cursor, 7)
            }
            CalendarBatch(results.flatMap { it.episodes }.distinctBy { it.id },
                results.flatMap { it.movies }.distinctBy { it.movie.id to it.released })
        }
}
