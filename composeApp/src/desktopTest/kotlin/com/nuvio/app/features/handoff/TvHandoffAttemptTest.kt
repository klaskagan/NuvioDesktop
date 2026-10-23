package com.nuvio.app.features.handoff

import java.io.Closeable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvHandoffAttemptTest {
    @Test fun `a connection returned after cancel is closed immediately`() {
        val attempt = TvHandoffAttempt()
        assertFalse(attempt.cancelAndCheckPlayback())
        var closed = 0
        assertFalse(attempt.attach(Closeable { closed++ }))
        assertEquals(1, closed)
        assertFalse(attempt.isActive)
    }

    @Test fun `cancel remembers a committed playback and closes resources once`() {
        val attempt = TvHandoffAttempt()
        var cancelled = 0
        var closed = 0
        attempt.attach(Closeable { closed++ }) { cancelled++; true }
        assertTrue(attempt.cancelAndCheckPlayback())
        assertTrue(attempt.cancelAndCheckPlayback())
        assertEquals(1, cancelled)
        assertEquals(1, closed)
    }

    @Test fun `closing one failed connection does not revive or cancel a new attempt`() {
        val old = TvHandoffAttempt()
        old.close()
        val current = TvHandoffAttempt()
        var oldClosed = false
        old.attach(Closeable { oldClosed = true })
        assertTrue(oldClosed)
        assertTrue(current.isActive)
        current.close()
    }
}
