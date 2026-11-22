package com.nuvio.app.features.home.components

import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeBoardTest {
    private fun resume(id: String) = ContinueWatchingItem(
        parentMetaId = id,
        parentMetaType = "movie",
        videoId = id,
        title = id,
        subtitle = "",
        imageUrl = null,
        resumePositionMs = 0L,
        durationMs = 1L,
        progressFraction = 0.5f,
    )

    @Test
    fun boardTakesTheFirstFourResumeItemsAndLeavesTheRestToTheRow() {
        val items = List(6) { resume("m$it") }
        val board = homeBoardResumeItems(items, hasSideColumn = true)
        assertEquals(listOf("m0", "m1", "m2", "m3"), board.map { it.parentMetaId })
        assertEquals(listOf("m4", "m5"), items.drop(board.size).map { it.parentMetaId })
    }

    @Test
    fun withoutTheSideColumnEveryResumeItemStaysInTheRow() {
        assertTrue(homeBoardResumeItems(List(3) { resume("m$it") }, hasSideColumn = false).isEmpty())
    }
}
