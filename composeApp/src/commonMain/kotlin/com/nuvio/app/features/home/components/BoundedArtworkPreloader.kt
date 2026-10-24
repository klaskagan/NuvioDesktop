package com.nuvio.app.features.home.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/** Keeps useful in-flight requests alive while replacing queued work with the latest viewport. */
internal class BoundedArtworkPreloader<T>(
    scope: CoroutineScope,
    parallelism: Int = 4,
    private val key: (T) -> String,
    private val load: suspend (T) -> Unit,
) {
    private data class Completion(val key: String, val cancelled: Boolean)
    private val updates = Channel<List<T>>(Channel.CONFLATED)

    init {
        require(parallelism > 0)
        scope.launch {
            val completions = Channel<Completion>(Channel.UNLIMITED)
            val active = mutableSetOf<String>()
            val completed = linkedSetOf<String>()
            var queued = emptyList<T>()

            fun replaceQueue(candidates: List<T>) {
                val seen = mutableSetOf<String>()
                queued = candidates.filter { candidate ->
                    val candidateKey = key(candidate)
                    seen.add(candidateKey) && candidateKey !in active && candidateKey !in completed
                }
            }

            while (true) {
                select<Unit> {
                    // A viewport update wins over a completion when both are available.
                    updates.onReceive { replaceQueue(it) }
                    completions.onReceive { completion ->
                        active.remove(completion.key)
                        if (!completion.cancelled) {
                            completed.add(completion.key)
                            while (completed.size > 128) completed.remove(completed.first())
                        }
                    }
                }
                // Coalesce scroll updates that arrived while handling the previous event.
                updates.tryReceive().getOrNull()?.let(::replaceQueue)
                while (active.size < parallelism && queued.isNotEmpty()) {
                    val candidate = queued.first()
                    queued = queued.drop(1)
                    val candidateKey = key(candidate)
                    if (!active.add(candidateKey)) continue
                    launch {
                        var cancelled = false
                        try {
                            load(candidate)
                        } catch (error: CancellationException) {
                            cancelled = true
                            throw error
                        } catch (_: Exception) {
                            // A broken resource must neither kill the scheduler nor retry forever.
                        } finally {
                            completions.trySend(Completion(candidateKey, cancelled))
                        }
                    }
                }
            }
        }
    }

    fun update(candidates: List<T>) {
        updates.trySend(candidates)
    }
}
