package com.nuvio.app.navigation

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistorySwipeSessionTest {
    @Test fun holdingPastTheOldTimeoutDoesNotCommitOrResetTheSwipe() {
        val session = HistorySwipeSession()
        session.onPhase(HistoryGesturePhase.Began)
        session.onScroll(Offset(-2f, 0f), 0, false)
        assertFalse(session.needsFallbackCompletion)
        assertNull(session.finishFallback())
        session.onScroll(Offset(-2f, 0f), 10_000, false)
        assertEquals(1f, session.preview?.progress)
        assertNull(session.finishFallback())
        assertEquals(HistorySwipe.Back, session.onPhase(HistoryGesturePhase.Ended))
    }

    @Test fun returningToTheStartingPositionBeforeReleaseCancels() {
        val session = HistorySwipeSession()
        session.onPhase(HistoryGesturePhase.Began)
        session.onScroll(Offset(-4f, 0f), 0, false)
        session.onScroll(Offset(4f, 0f), 10_000, false)
        assertNull(session.preview)
        assertNull(session.onPhase(HistoryGesturePhase.Ended))
    }

    @Test fun releaseAtTheHeldPositionCommitsExactlyOnceWithoutAnotherDelta() {
        val session = HistorySwipeSession()
        session.onPhase(HistoryGesturePhase.Began)
        session.onScroll(Offset(4f, 0f), 0, false)
        assertEquals(HistorySwipe.Forward, session.onPhase(HistoryGesturePhase.Ended))
        assertNull(session.onPhase(HistoryGesturePhase.Ended))
        session.onPhase(HistoryGesturePhase.Momentum)
        session.onScroll(Offset(20f, 0f), 1000, false)
        assertNull(session.preview)
        assertNull(session.finishFallback())
    }

    @Test fun momentumArrivingBeforeDeferredReleaseDoesNotAddToDistance() {
        val session = HistorySwipeSession()
        session.onPhase(HistoryGesturePhase.Began)
        session.onScroll(Offset(-1f, 0f), 0, false)
        session.onPhase(HistoryGesturePhase.Momentum)
        session.onScroll(Offset(-20f, 0f), 100, false)
        assertNull(session.onPhase(HistoryGesturePhase.Ended))
    }

    @Test fun nextContactStartsFreshEvenWithoutAQuietGap() {
        val session = HistorySwipeSession()
        session.onPhase(HistoryGesturePhase.Began)
        session.onScroll(Offset(-4f, 0f), 0, false)
        assertEquals(HistorySwipe.Back, session.onPhase(HistoryGesturePhase.Ended))
        session.onPhase(HistoryGesturePhase.Began)
        session.onScroll(Offset(4f, 0f), 30, false)
        assertEquals(HistorySwipe.Forward, session.onPhase(HistoryGesturePhase.Ended))
    }

    @Test fun cancellationAndCollectionOwnershipPreventCommit() {
        for (blocked in listOf(true, false)) {
            val session = HistorySwipeSession()
            session.onPhase(HistoryGesturePhase.Began)
            session.onScroll(Offset(-4f, 0f), 0, blocked)
            if (!blocked) session.onPhase(HistoryGesturePhase.Cancelled)
            assertNull(session.onPhase(HistoryGesturePhase.Ended))
        }
    }

    @Test fun ordinaryWheelsRetainFallbackCompletionAfterTrackpadUse() {
        val session = HistorySwipeSession()
        session.onPhase(HistoryGesturePhase.Began)
        session.onPhase(HistoryGesturePhase.Ended)
        session.onPhase(HistoryGesturePhase.Unphased)
        session.onScroll(Offset(4f, 0f), 0, false)
        assertTrue(session.needsFallbackCompletion)
        assertEquals(HistorySwipe.Forward, session.finishFallback())
    }
}
