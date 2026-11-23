package com.nuvio.app.features.search

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class StreamingProviderSessionsTest {
    private val netflix = checkNotNull(searchProviderDiscovery("Netflix"))
    private val grid = SearchShelfView(SearchShelfSort.Popular, television = true)

    @AfterTest
    fun reset() = StreamingProviderSessions.clear()

    @Test
    fun freshVisitClosesALeftOpenGridButKeepsTheSession() {
        val first = StreamingProviderSessions.sessionFor(netflix, freshVisit = true)
        first.view = grid

        val reopened = StreamingProviderSessions.sessionFor(netflix, freshVisit = true)

        assertSame(first, reopened)
        assertNull(reopened.view)
    }

    @Test
    fun returningFromATitleKeepsTheGrid() {
        StreamingProviderSessions.sessionFor(netflix, freshVisit = true).view = grid

        assertEquals(grid, StreamingProviderSessions.sessionFor(netflix, freshVisit = false).view)
    }
}
