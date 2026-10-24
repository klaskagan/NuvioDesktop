package com.nuvio.app.features.home.components

import kotlinx.coroutines.*
import kotlin.test.*

class SharedMediaPreparationSchedulerTest {
    @Test fun ownersShareGlobalBudgetAndVisibleWorkOvertakesQueuedPrefetch() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val scheduler = SharedMediaPreparationScheduler<String>(scope, parallelism = 2)
            val started = mutableListOf<String>()
            val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
            fun request(id: String, priority: Int) = SharedMediaPreparationScheduler.Request(id, priority, load = {
                started += id; gates.getOrPut(id) { CompletableDeferred() }.await(); id
            }, ready = { _: String -> })
            scheduler.update("first-screen", listOf(request("a", 0), request("b", 0), request("stale", 2000)))
            repeat(10) { yield() }
            assertEquals(listOf("a", "b"), started)
            scheduler.update("second-screen", listOf(request("visible", 0), request("ahead", 1000)))
            scheduler.update("first-screen", listOf(request("a", 0), request("b", 0)))
            repeat(10) { yield() }
            gates.getValue("a").complete(Unit)
            repeat(10) { yield() }
            assertEquals(listOf("a", "b", "visible"), started)
            gates.getValue("b").complete(Unit)
            repeat(10) { yield() }
            assertEquals(listOf("a", "b", "visible", "ahead"), started)
            assertEquals(2, scheduler.metrics.maximumActive)
            assertFalse("stale" in started)
        } finally { scope.cancel() }
    }

    @Test fun duplicateOwnersShareOneLoadAndCacheSurvivesScreenChanges() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val scheduler = SharedMediaPreparationScheduler<String>(scope)
            val gate = CompletableDeferred<Unit>()
            var loads = 0
            val delivered = mutableListOf<String>()
            fun request(owner: String) = SharedMediaPreparationScheduler.Request("same-artwork", 0, load = {
                loads++; gate.await(); "ready"
            }, ready = { _: String -> delivered += owner })
            scheduler.update("home", listOf(request("home")))
            repeat(10) { yield() }
            scheduler.update("catalog", listOf(request("catalog")))
            repeat(10) { yield() }
            gate.complete(Unit)
            repeat(10) { yield() }
            assertEquals(1, loads)
            assertEquals(setOf("home", "catalog"), delivered.toSet())
            scheduler.remove("home"); scheduler.remove("catalog")
            scheduler.update("library", listOf(request("library")))
            repeat(10) { yield() }
            assertEquals(1, loads)
            assertEquals("library", delivered.last())
            assertEquals(1, scheduler.metrics.cacheHits)
            assertEquals(1, scheduler.metrics.coalesced)
        } finally { scope.cancel() }
    }

    @Test fun removedOwnersCannotReceiveLateResultsOrStartTheirQueuedWork() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val scheduler = SharedMediaPreparationScheduler<String>(scope, parallelism = 1)
            val gate = CompletableDeferred<Unit>()
            val started = mutableListOf<String>()
            var lateCallbacks = 0
            fun request(id: String) = SharedMediaPreparationScheduler.Request(id, 0, load = {
                started += id; gate.await(); id
            }, ready = { _: String -> lateCallbacks++ })
            scheduler.update("old-filter", listOf(request("active"), request("obsolete")))
            repeat(10) { yield() }
            scheduler.remove("old-filter")
            repeat(10) { yield() }
            gate.complete(Unit)
            repeat(10) { yield() }
            assertEquals(listOf("active"), started)
            assertEquals(0, lateCallbacks)
        } finally { scope.cancel() }
    }

    @Test fun boundedCacheDoesNotRefetchStillDeliveredWorkOrLoopOnFailure() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val scheduler = SharedMediaPreparationScheduler<String>(scope, parallelism = 1, capacity = 2)
            var loads = 0
            fun request(id: String) = SharedMediaPreparationScheduler.Request(id, 0, load = {
                loads++; if (id == "broken") error("missing image"); id
            }, ready = { _: String -> })
            scheduler.update("owner", (0..5).map { request("$it") } + request("broken"))
            repeat(100) { yield() }
            assertEquals(7, loads)
            scheduler.update("owner", (0..5).map { request("$it") })
            repeat(30) { yield() }
            assertEquals(7, loads, "Cache eviction must not refetch assets already delivered to a mounted row")
            scheduler.remove("owner")
            scheduler.update("new-owner", listOf(request("0")))
            repeat(10) { yield() }
            assertEquals(8, loads, "Evicted results may be prepared for a new owner")
        } finally { scope.cancel() }
    }
}
