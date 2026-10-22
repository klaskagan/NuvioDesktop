package com.nuvio.app.navigation

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistorySwipeDetectorTest {
    @Test fun deliberateHorizontalSwipesNavigateOnceIncludingMomentum() {
        val detector = HistorySwipeDetector()
        assertNull(detector.onScroll(Offset(1f, 0f), 0, false))
        assertEquals(HistorySwipe.Forward, detector.onScroll(Offset(2f, 0f), 30, false))
        assertEquals(HistorySwipe.Forward, detector.finish())
        for (time in 60L..900L step 30) {
            assertNull(detector.onScroll(Offset(4f, 0f), time, false))
        }
        assertEquals(HistorySwipe.Back, detector.onScroll(Offset(-4f, 0f), 1500, false))
    }

    @Test fun verticalAndDiagonalScrollingDoNotNavigate() {
        for (delta in listOf(Offset(0f, 4f), Offset(2f, 3f))) {
            val detector = HistorySwipeDetector()
            assertNull(detector.onScroll(delta, 0, false))
            assertNull(detector.onScroll(Offset(4f, 0f), 30, false))
        }
    }

    @Test fun contentOwnsTheWholeGestureEvenAfterReachingItsScrollEdge() {
        val detector = HistorySwipeDetector()
        assertNull(detector.onScroll(Offset(1f, 0f), 0, true))
        assertNull(detector.onScroll(Offset(4f, 0f), 30, false))
        assertEquals(HistorySwipe.Forward, detector.onScroll(Offset(4f, 0f), 600, false))
    }

    @Test fun reversingBeforeReleaseCancelsNavigation() {
        val detector = HistorySwipeDetector()
        detector.onScroll(Offset(-4f, 0f), 0, false)
        assertEquals(HistorySwipe.Back, detector.preview?.direction)
        detector.onScroll(Offset(3.5f, 0f), 30, false)
        assertNull(detector.finish())
    }

    @Test fun smallOrCancelledSwipesDoNotNavigate() {
        val detector = HistorySwipeDetector()
        assertNull(detector.onScroll(Offset(2f, 0f), 0, false))
        assertNull(detector.onScroll(Offset(-2f, 0f), 30, false))
        assertNull(detector.onScroll(Offset(2f, 0f), 600, false))
    }
}
