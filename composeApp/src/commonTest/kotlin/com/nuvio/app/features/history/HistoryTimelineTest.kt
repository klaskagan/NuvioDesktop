package com.nuvio.app.features.history

import com.nuvio.app.features.tracking.TrackingHistoryRemover
import com.nuvio.app.features.tracking.TrackingHistoryAccount
import com.nuvio.app.features.tracking.TrackingHistoryEvent
import com.nuvio.app.features.tracking.TrackingHistoryPage
import com.nuvio.app.features.tracking.TrackingHistoryProvider
import com.nuvio.app.features.tracking.TrackingHistorySource
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTimelineTest {
    @Test
    fun retryKeepsRowsAndRetriesTheSameCursor() = runTest {
        val cursors = mutableListOf<String?>()
        val first = event("watch", 20)
        val rewatch = event("rewatch", 10)
        val source = fixture(TrackingProviderId.TRAKT) { cursor ->
            cursors += cursor
            when {
                cursors.size in listOf(1, 3) -> error("Offline")
                cursor == null -> TrackingHistoryPage(listOf(first), "next")
                else -> TrackingHistoryPage(listOf(first, rewatch), null)
            }
        }
        val timeline = HistoryTimeline(source)
        timeline.load()
        assertFalse(timeline.loaded)
        assertTrue(timeline.failed)
        timeline.retry()
        timeline.load("next")
        assertEquals(listOf(first), timeline.events)
        assertTrue(timeline.failed)
        timeline.retry()
        assertEquals(listOf(first, rewatch), timeline.events)
        assertNull(timeline.nextCursor)
        assertEquals(listOf(null, null, "next", "next"), cursors)
    }

    @Test
    fun replacingProviderRejectsLateResponseFromDisconnectedAccount() = runTest {
        val started = CompletableDeferred<Unit>()
        val pending = CompletableDeferred<TrackingHistoryPage>()
        val first = fixture(TrackingProviderId.TRAKT) {
            started.complete(Unit)
            pending.await()
        }
        val oldTimeline = HistoryTimeline(first)
        val request = launch { oldTimeline.load() }
        started.await()
        (first.provider.account as MutableStateFlow).value = null
        val replacement = HistoryTimeline(fixture(TrackingProviderId.SIMKL) {
            TrackingHistoryPage(listOf(event("watch", 30)), null)
        })
        replacement.load()
        pending.complete(TrackingHistoryPage(listOf(event("watch", 20)), null))
        request.join()
        assertTrue(oldTimeline.events.isEmpty())
        assertEquals(listOf(event("watch", 30)), replacement.events)
    }

    @Test
    fun emptyPageIsLoaded() = runTest {
        val timeline = HistoryTimeline(fixture(TrackingProviderId.TRAKT) {
            TrackingHistoryPage(emptyList(), null)
        })
        timeline.load()
        assertTrue(timeline.loaded)
        assertTrue(timeline.events.isEmpty())
    }

    @Test
    fun filteredReaderSkipsEmptyMixedPagesWithoutUserScrolling() = runTest {
        val cursors = mutableListOf<String?>()
        val movie = event("movie", 10)
        val timeline = HistoryTimeline(fixture(TrackingProviderId.SIMKL) { cursor ->
            cursors += cursor
            if (cursor == null) TrackingHistoryPage(listOf(movie.copy(media = movie.media.copy(kind = TrackingMediaKind.SHOW))), "older")
            else TrackingHistoryPage(listOf(movie), null)
        }, TrackingMediaKind.MOVIE)
        timeline.load()
        assertEquals(listOf(null, "older"), cursors)
        assertEquals(listOf(movie), timeline.events)
    }

    @Test
    fun removalPreservesRewatchesAndFailureKeepsRows() = runTest {
        val session = TrackingHistoryAccount(1, 1)
        val watch = event("watch", 20)
        val rewatch = event("rewatch", 10)
        var fail = true
        val reader = object : TrackingHistoryProvider, TrackingHistoryRemover {
            override val providerId = TrackingProviderId.SIMKL
            override val account = MutableStateFlow<TrackingHistoryAccount?>(session)
            override suspend fun loadPage(profileId: Int, cursor: String?) = TrackingHistoryPage(listOf(watch, rewatch), null)
            override suspend fun removeEvent(profileId: Int, eventId: String) {
                assertEquals("watch", eventId)
                if (fail) error("Offline")
            }
        }
        val timeline = HistoryTimeline(TrackingHistorySource(reader, session))
        timeline.load()
        timeline.remove("watch")
        assertTrue(timeline.removalFailed)
        assertEquals(listOf(watch, rewatch), timeline.events)
        fail = false
        timeline.remove("watch")
        assertEquals(listOf(rewatch), timeline.events)
        timeline.load() // an overlapping, stale server page must not resurrect the removed event
        assertEquals(listOf(rewatch), timeline.events)
    }

    private fun fixture(
        id: TrackingProviderId,
        fetch: suspend (String?) -> TrackingHistoryPage,
    ): TrackingHistorySource {
        val session = TrackingHistoryAccount(1, 1L)
        val reader = object : TrackingHistoryProvider {
            override val providerId = id
            override val account = MutableStateFlow<TrackingHistoryAccount?>(session)
            override suspend fun loadPage(profileId: Int, cursor: String?): TrackingHistoryPage {
                assertEquals(session.profileId, profileId)
                return fetch(cursor)
            }
        }
        return TrackingHistorySource(reader, session)
    }

    private fun event(id: String, time: Long) = TrackingHistoryEvent(
        id, time, TrackingMediaReference(TrackingMediaKind.MOVIE, title = "Movie"),
    )
}
