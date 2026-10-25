package com.nuvio.app.features.trailer

import com.nuvio.app.features.player.desktop.NativePlayerEventSink
import javax.swing.SwingUtilities
import kotlin.test.*

class YouTubeSessionLifecycleTest {
    @Test fun failedAndClosedSessionsIgnoreLateNativeEvents() {
        for (failed in listOf(true, false)) {
            val session = YouTubeSession("R5jcEBVBEbQ", controls = true, muted = true, startMs = 0)
            if (failed) session.fail("Startup timeout") else session.close()
            deliver(session, "state", 1.0)
            deliver(session, "time", 45.0)
            assertFalse(session.ready)
            assertFalse(session.isPlaying)
            assertEquals(0L, session.positionMs)
            assertEquals(if (failed) "Startup timeout" else null, session.error)
        }
    }

    @Test fun retryHasAnIndependentLifetime() {
        val old = YouTubeSession("R5jcEBVBEbQ", true, true, 0)
        old.fail("Startup timeout")
        val fresh = YouTubeSession("R5jcEBVBEbQ", true, true, 0)
        try {
            deliver(old, "error", 100.0)
            deliver(fresh, "state", 1.0)
            assertTrue(fresh.ready)
            assertTrue(fresh.isPlaying)
            assertNull(fresh.error)
        } finally { fresh.close() }
    }

    private fun deliver(session: YouTubeSession, type: String, value: Double) {
        val sink = session.javaClass.getDeclaredField("sink").apply { isAccessible = true }.get(session) as NativePlayerEventSink
        sink.onPlayerEvent(type, value)
        SwingUtilities.invokeAndWait { }
    }
}
