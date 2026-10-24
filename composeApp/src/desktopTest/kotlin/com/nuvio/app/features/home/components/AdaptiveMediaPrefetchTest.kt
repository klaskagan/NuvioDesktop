package com.nuvio.app.features.home.components

import kotlin.test.*

class AdaptiveMediaPrefetchTest {
    private val keys = (0..29).map(Int::toString)

    @Test fun visibleRowsComeFirstAndLookaheadFollowsDirection() {
        assertEquals(listOf("10", "11", "12", "13", "9"), adaptivePrefetchRowKeys(keys, listOf("11", "10"), 2, 1))
        assertEquals(listOf("10", "11", "9", "8", "7", "6", "12"), adaptivePrefetchRowKeys(keys, listOf("10", "11"), 4, -1))
        assertEquals(listOf("0", "1", "2"), adaptivePrefetchRowKeys(keys, emptyList(), 2, 1))
        assertEquals(emptyList(), adaptivePrefetchRowKeys(emptyList(), emptyList(), 6, 1))
    }

    @Test fun windowGrowsWithSpeedAndLatencyAndShrinksAfterStopping() {
        val window = MediaPrefetchWindow()
        window.observe(0.0, 0, 1000)
        assertEquals(2, window.aheadRows)
        window.observe(.3, 100, 1000)
        assertTrue(window.aheadRows > 2)
        window.observe(1.0, 200, 1500)
        assertEquals(6, window.aheadRows)
        window.observe(1.0, 500, 1500)
        assertEquals(2, window.aheadRows)
        window.observe(.8, 600, 1500)
        assertEquals(-1, window.direction)
        assertTrue(window.aheadRows in 2..6)
    }

    @Test fun horizontalWindowUsesVisibleCardGroupsAndRemainsBounded() {
        val selected = adaptivePrefetchRowKeys(keys, listOf("5", "6", "7", "8"), 2, 1, unitsPerRow = 4)
        assertEquals((5..16).map(Int::toString) + (4 downTo 1).map(Int::toString), selected)
        val window = MediaPrefetchWindow()
        window.observe(0.0, 0, 1000, 5)
        window.observe(5.0, 1000, 1000, 5)
        assertEquals(2, window.aheadRows, "One shelf-width per second needs the baseline two groups")
        window.observe(100.0, 1010, 12_000, 5)
        assertEquals(6, window.aheadRows)
    }

    @Test fun paginationUsesRowsAndStartsBeforeTheArtworkWindowRunsOut() {
        assertFalse(shouldPrefetchMediaPage(20, 10, 2))
        assertTrue(shouldPrefetchMediaPage(20, 12, 6))
        assertFalse(shouldPrefetchMediaPage(20, -1, 6))
        assertFalse(shouldPrefetchMediaPage(0, 0, 6))
    }

    @Test fun adaptiveLeadImprovesReadinessOverFixedTwoRowsUnderKnownLatency() {
        fun lateRows(adaptive: Boolean): Int {
            val window = MediaPrefetchWindow()
            val readyAt = mutableMapOf<Int, Long>()
            val entered = mutableSetOf<Int>()
            var late = 0
            for (time in 0L..10_000L step 100) {
                val position = time * .004 // Four rows/second; preparing one row takes 900ms.
                val visible = position.toInt()
                window.observe(position, time, 900)
                val ahead = if (adaptive) window.aheadRows else 2
                for (row in visible..visible + ahead) readyAt.putIfAbsent(row, time + 900)
                if (entered.add(visible) && time > 2000 && readyAt.getValue(visible) > time) late++
            }
            return late
        }
        val baseline = lateRows(false)
        val adaptive = lateRows(true)
        assertTrue(baseline > 0)
        assertEquals(0, adaptive)
    }
}
