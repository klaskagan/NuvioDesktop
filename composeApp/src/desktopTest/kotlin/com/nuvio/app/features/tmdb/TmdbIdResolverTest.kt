package com.nuvio.app.features.tmdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbIdResolverTest {
    @Test fun simultaneousArtworkAndDetailsShareFindResponseAcrossMediaTypes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()
        val resolver = TmdbIdResolver { url ->
            requests += url
            started.complete(Unit)
            release.await()
            """{"movie_results":[{"id":11}],"tv_results":[{"id":22}]}"""
        }
        val artwork = async { resolver.ensureTmdbId("tt123", "series", "key") }
        started.await()
        val details = async { resolver.ensureTmdbId("tt123", "tv", "key") }
        val movie = async { resolver.ensureTmdbId("tt123", "movie", "key") }
        yield()
        release.complete(Unit)
        assertEquals("22", artwork.await())
        assertEquals("22", details.await())
        assertEquals("11", movie.await())
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("/find/tt123?"))
        assertEquals("22", resolver.ensureTmdbId("tt123", "show", "key"))
        assertEquals("tt123", resolver.tmdbToImdb(22, "tv", "key"))
        assertEquals(1, requests.size, "Resolved IDs must also seed the reverse mapping")
    }

    @Test fun missingMappingsAreCachedWithoutChangingNumericOrFallbackResolution() = runBlocking {
        var requests = 0
        val resolver = TmdbIdResolver { requests++; """{"movie_results":[],"tv_results":[]}""" }
        assertEquals("42", resolver.ensureTmdbId("tmdb:42", "movie", "key"))
        assertEquals(0, requests)
        assertNull(resolver.ensureTmdbId("addon:show", "tv", "key", "tt404"))
        assertNull(resolver.ensureTmdbId("tt404", "series", "key"))
        assertEquals(1, requests)
        assertNull(resolver.ensureTmdbId("tt404", "series", "new-key"))
        assertEquals(2, requests, "A different credential must not reuse a failed lookup")
    }

    @Test fun concurrentReverseLookupsShareExternalIdsAndSeedForwardMapping() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val resolver = TmdbIdResolver { requests++; release.await(); """{"imdb_id":"tt123"}""" }
        val first = async { resolver.tmdbToImdb(42, "series", "key") }
        yield()
        val second = async { resolver.tmdbToImdb(42, "tv", "key") }
        yield(); release.complete(Unit)
        assertEquals("tt123", first.await())
        assertEquals("tt123", second.await())
        assertEquals("42", resolver.ensureTmdbId("tt123", "show", "key"))
        assertEquals(1, requests)
    }

    @Test fun cancelledRequestDoesNotBecomeCachedMissingMapping() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var requests = 0
        val resolver = TmdbIdResolver {
            requests++
            if (requests == 1) { started.complete(Unit); awaitCancellation() }
            """{"tv_results":[{"id":22}]}"""
        }
        val first = async { resolver.ensureTmdbId("tt123", "tv", "key") }
        started.await()
        first.cancelAndJoin()
        assertEquals("22", resolver.ensureTmdbId("tt123", "tv", "key"))
        assertEquals(2, requests)
    }
}
