package com.nuvio.app.features.home.components

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** No URLs or account identifiers are recorded by these diagnostics. */
@Stable
internal class MediaPreparationMetrics {
    var completed by mutableIntStateOf(0); private set
    var cacheHits by mutableIntStateOf(0); private set
    var coalesced by mutableIntStateOf(0); private set
    var maximumActive by mutableIntStateOf(0); private set
    var preparationMillis by mutableLongStateOf(750); private set
    var queueMillis: Long = 0; private set
    var metadataMillis: Long = 0; private set
    var imageMillis: Long = 0; private set
    var visibleRows = 0; private set
    var rowsReadyOnEntry = 0; private set
    fun cacheHit() { cacheHits++ }
    fun share() { coalesced++ }
    fun started(active: Int, queue: Long) { maximumActive = maxOf(maximumActive, active); queueMillis += queue }
    fun metadata(elapsed: Long) { metadataMillis += elapsed }
    fun images(elapsed: Long) { imageMillis += elapsed }
    fun rowEntered(ready: Boolean) { visibleRows++; if (ready) rowsReadyOnEntry++ }
    fun settled(elapsed: Long) {
        preparationMillis = if (completed == 0) elapsed.coerceAtLeast(100) else (preparationMillis * 3 + elapsed) / 4
        completed++
    }
}

/** One app-scoped queue. Viewport owners replace their work without restarting shared requests. */
internal class SharedMediaPreparationScheduler<V>(
    private val scope: CoroutineScope,
    private val parallelism: Int = 4,
    private val capacity: Int = 4096,
    private val now: () -> Long = ::mediaPrefetchNowMillis,
    private val cacheLifetimeMillis: (V) -> Long = { 6 * 60 * 60 * 1000L },
) {
    internal data class Request<V>(val key: Any, val priority: Int, val load: suspend () -> V, val ready: (V) -> Unit)
    private sealed interface Event<V> {
        class Plan<V>(val owner: Any, val requests: List<Request<V>>) : Event<V>
        class Finished<V>(val key: Any, val result: Result<V>, val elapsed: Long) : Event<V>
    }
    private data class Cached<V>(val value: V, val expiresAt: Long)
    private data class Waiting<V>(val request: Request<V>, val enqueued: Long)
    // Accessed on the composition/main dispatcher; pixels live in separate byte-bounded caches.
    private val cache = linkedMapOf<Any, Cached<V>>()
    fun peek(key: Any): V? = cache[key]?.takeIf { it.expiresAt > now() }?.value
    private val events = Channel<Event<V>>(Channel.UNLIMITED)
    val metrics = MediaPreparationMetrics()

    init {
        require(parallelism > 0 && capacity > 0)
        scope.launch {
            val owners = linkedMapOf<Any, List<Request<V>>>()
            val active = mutableSetOf<Any>()
            val jobs = mutableMapOf<Any, Job>()
            val obsolete = mutableSetOf<Any>()
            val delivered = mutableMapOf<Any, MutableSet<Any>>()
            val failed = mutableSetOf<Any>()
            val waitingSince = mutableMapOf<Any, Long>()
            for (event in events) {
                when (event) {
                    is Event.Plan -> {
                        cache.entries.removeAll { it.value.expiresAt <= now() }
                        val requests = event.requests.distinctBy { it.key }
                        val old = owners[event.owner].orEmpty().map { it.key }.toSet()
                        val received = delivered.getOrPut(event.owner) { mutableSetOf() }
                        received.retainAll(requests.map { it.key }.toSet())
                        if (requests.isEmpty()) { owners.remove(event.owner); delivered.remove(event.owner) } else owners[event.owner] = requests
                        requests.forEach { request ->
                            failed.remove(request.key)
                            if (request.key in received) return@forEach
                            if (cache.containsKey(request.key)) {
                                metrics.cacheHit(); received.add(request.key); request.ready(cache.getValue(request.key).value)
                            } else {
                                waitingSince.getOrPut(request.key, now)
                                if (request.key in active && request.key !in old) metrics.share()
                            }
                        }
                    }
                    is Event.Finished -> {
                        active.remove(event.key)
                        jobs.remove(event.key)
                        val wasObsolete = obsolete.remove(event.key)
                        if (event.result.isSuccess) {
                            val value = event.result.getOrThrow()
                            cache.remove(event.key); cache[event.key] = Cached(value, now() + cacheLifetimeMillis(value))
                            while (cache.size > capacity) cache.remove(cache.keys.first())
                            metrics.settled(event.elapsed)
                            owners.forEach { (owner, requests) -> requests.filter { it.key == event.key }.forEach {
                                delivered.getValue(owner).add(event.key); it.ready(value)
                            } }
                        } else if (!wasObsolete) failed.add(event.key)
                        // Retry failures only on a new viewport plan, never in a busy loop.
                    }
                }
                val wanted = owners.flatMap { (owner, requests) -> requests.filter { it.key !in delivered.getValue(owner) } }
                    .sortedBy { it.priority }.distinctBy { it.key }
                val wantedKeys = wanted.map { it.key }.toSet()
                active.filter { it !in wantedKeys }.forEach { key ->
                    if (obsolete.add(key)) jobs[key]?.cancel()
                }
                waitingSince.keys.retainAll(wantedKeys + active)
                failed.retainAll(wantedKeys)
                val queue = wanted.filter { it.key !in active && it.key !in failed && !cache.containsKey(it.key) }
                    .map { Waiting(it, waitingSince.getOrPut(it.key, now)) }
                for ((request, enqueued) in queue.take((parallelism - active.size).coerceAtLeast(0))) {
                    active.add(request.key)
                    metrics.started(active.size, now() - enqueued)
                    var outcome: Result<V>? = null
                    val job = scope.launch(start = CoroutineStart.LAZY) { outcome = runCatching { request.load() } }
                    jobs[request.key] = job
                    job.invokeOnCompletion { cause ->
                        // Also releases the slot if cancelled before the coroutine starts.
                        val result = outcome ?: Result.failure(cause ?: CancellationException("Unrequested preparation"))
                        events.trySend(Event.Finished(request.key, result, now() - enqueued))
                    }
                    job.start()
                }
            }
        }
    }

    fun update(owner: Any, requests: List<Request<V>>) { events.trySend(Event.Plan(owner, requests)) }
    fun remove(owner: Any) { update(owner, emptyList()) }
}

internal val LocalMediaPreparationScheduler = staticCompositionLocalOf<SharedMediaPreparationScheduler<LandscapeMediaArtworkState>?> { null }

@Composable
internal fun rememberMediaPreparationScheduler(): SharedMediaPreparationScheduler<LandscapeMediaArtworkState> {
    val shared = LocalMediaPreparationScheduler.current
    val scope = rememberCoroutineScope()
    return shared ?: remember(scope) { SharedMediaPreparationScheduler(scope, cacheLifetimeMillis = { result ->
        if (result.logoFailed) 60_000 else 6 * 60 * 60 * 1000L
    }) }
}
