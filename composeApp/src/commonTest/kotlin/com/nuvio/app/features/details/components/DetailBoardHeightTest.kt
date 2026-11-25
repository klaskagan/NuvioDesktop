package com.nuvio.app.features.details.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailBoardHeightTest {
    @Test
    fun collapsedBoardKeepsItsHeightWhateverTheWindow() {
        assertEquals(DetailBoardHeight, detailBoardHeight(1000.dp, expanded = false))
    }

    @Test
    fun expandedBoardGrowsToHomesExpandedHeight() {
        assertEquals(850.dp, detailBoardHeight(1000.dp, expanded = true))
    }

    @Test
    fun clipTimesReadAsMinutesAndSeconds() {
        assertEquals("0:41", trailerClockTime(41_900))
        assertEquals("2:24", trailerClockTime(144_000))
        assertEquals("1:02:05", trailerClockTime(3_725_000))
    }

    @Test
    fun expandedBoardNeverShrinksBelowTheCollapsedHeight() {
        assertEquals(DetailBoardHeight, detailBoardHeight(500.dp, expanded = true))
        assertEquals(DetailBoardHeight, detailBoardHeight(Dp.Unspecified, expanded = true))
    }
}
