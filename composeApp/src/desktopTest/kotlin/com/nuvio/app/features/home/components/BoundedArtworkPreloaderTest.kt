package com.nuvio.app.features.home.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BoundedArtworkPreloaderTest {
    @Test fun viewportUpdatesKeepInFlightWorkAndReplaceQueuedPriority() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val started = mutableListOf<String>()
            val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
            var active = 0
            var maximum = 0
            val loader = BoundedArtworkPreloader<String>(scope, parallelism = 2, key = { it }) {
                started += it
                active++
                maximum = maxOf(maximum, active)
                try { gates.getOrPut(it) { CompletableDeferred() }.await() }
                finally { active-- }
            }
            loader.update(listOf("a", "a", "b", "stale"))
            repeat(5) { yield() }
            assertEquals(listOf("a", "b"), started)
            loader.update(listOf("urgent", "b", "later", "urgent"))
            repeat(5) { yield() }
            assertEquals(2, active, "Scrolling must keep in-flight work alive")
            gates.getValue("a").complete(Unit)
            repeat(5) { yield() }
            assertEquals(listOf("a", "b", "urgent"), started)
            gates.getValue("b").complete(Unit)
            repeat(5) { yield() }
            assertEquals(listOf("a", "b", "urgent", "later"), started)
            assertEquals(2, maximum)
            assertFalse("stale" in started)
            gates.getValue("urgent").complete(Unit)
            gates.getValue("later").complete(Unit)
            repeat(5) { yield() }
            loader.update(listOf("a", "b", "urgent", "later"))
            repeat(5) { yield() }
            assertEquals(4, started.size, "Completed assets must not be fetched again")
        } finally { scope.cancel() }
    }

    @Test fun failedAssetSettlesAndDoesNotBlockFollowingWork() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val started = mutableListOf<String>()
            val loader = BoundedArtworkPreloader<String>(scope, parallelism = 1, key = { it }) {
                started += it
                if (it == "broken") error("unavailable artwork")
            }
            loader.update(listOf("broken", "good"))
            repeat(10) { yield() }
            assertEquals(listOf("broken", "good"), started)
            loader.update(listOf("broken", "good"))
            repeat(5) { yield() }
            assertEquals(2, started.size)
        } finally { scope.cancel() }
    }

    @Test fun cancelledLoadCanBeScheduledAgain() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var attempts = 0
            val loader = BoundedArtworkPreloader<String>(scope, key = { it }) {
                attempts++
                if (attempts == 1) throw CancellationException("cancelled request")
            }
            loader.update(listOf("asset"))
            repeat(5) { yield() }
            loader.update(listOf("asset"))
            repeat(5) { yield() }
            assertEquals(2, attempts)
        } finally { scope.cancel() }
    }

    @Test fun completedHistoryIsBounded() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val started = mutableListOf<Int>()
            val loader = BoundedArtworkPreloader<Int>(scope, key = { "$it" }) { started += it }
            loader.update((0..128).toList())
            repeat(150) { yield() }
            assertEquals(129, started.size)
            loader.update(listOf(0, 128))
            repeat(10) { yield() }
            assertEquals(130, started.size)
            assertEquals(0, started.last())
        } finally { scope.cancel() }
    }
}
