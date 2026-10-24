package com.nuvio.app.features.tmdb

import kotlinx.coroutines.*
import kotlin.test.*

class LandscapeArtworkCacheTest {
    @Test fun repeatedCardsShareRequestsAndMissingResultsExpire() = runBlocking {
        var time = 0L
        val cache = LandscapeArtworkCache<String>(capacity = 2, now = { time })
        val gate = CompletableDeferred<Unit>()
        var loads = 0
        val first = async { cache.get("show") { loads++; gate.await(); "clean" } }
        yield()
        val second = async { cache.get("show") { error("duplicate request") } }
        yield(); gate.complete(Unit)
        assertEquals("clean", first.await()); assertEquals("clean", second.await()); assertEquals(1, loads)
        assertNull(cache.get("missing") { loads++; null })
        assertNull(cache.get("missing") { error("missing result should be cached") })
        time = 60_001L
        assertEquals("recovered", cache.get("missing") { "recovered" })
        assertEquals("clean", cache.get("show") { error("successful result should remain cached") })
    }

    @Test fun disposedOwnerDoesNotCancelOtherCardsOrPoisonCache() = runBlocking {
        val cache = LandscapeArtworkCache<String>()
        val started = CompletableDeferred<Unit>()
        val owner = async { cache.get("show") { started.complete(Unit); awaitCancellation() } }
        started.await()
        val waiter = async { cache.get("show") { "retry" } }
        yield(); owner.cancelAndJoin()
        assertEquals("retry", waiter.await())
        assertEquals("retry", cache.get("show") { error("unexpected request") })
    }

    @Test fun boundedCacheEvictsOldResultsAndSeparatesLanguages() = runBlocking {
        val cache = LandscapeArtworkCache<String>(capacity = 2)
        cache.get("show:en") { "English" }; cache.get("show:fr") { "French" }
        cache.get("other:en") { "Other" }
        assertEquals("Reloaded", cache.get("show:en") { "Reloaded" })
    }
}
