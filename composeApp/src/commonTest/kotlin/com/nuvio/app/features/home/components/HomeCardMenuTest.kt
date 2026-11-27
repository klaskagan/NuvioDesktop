package com.nuvio.app.features.home.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCardMenuTest {
    private val window = IntSize(1360, 850)
    private val menu = IntSize(280, 260)

    @Test
    fun `menu opens under the card when it fits and above it near the window foot`() {
        assertEquals(IntOffset(40, 318), homeCardMenuPosition(Rect(40f, 100f, 340f, 310f), window, menu, margin = 16, gap = 8))
        assertEquals(IntOffset(40, 332), homeCardMenuPosition(Rect(40f, 600f, 340f, 780f), window, menu, margin = 16, gap = 8))
    }

    @Test
    fun `menu stays inside the window's right margin`() {
        assertEquals(1064, homeCardMenuPosition(Rect(1200f, 100f, 1340f, 200f), window, menu, margin = 16, gap = 8).x)
    }

    @Test
    fun `continue watching row keeps only titles partway through`() {
        fun item(progress: Float, nextUp: Boolean = false, alert: Boolean = false) = ContinueWatchingItem(
            parentMetaId = "s", parentMetaType = "series", videoId = "v", title = "Show", subtitle = "", imageUrl = null,
            isNextUp = nextUp, resumePositionMs = 0L, durationMs = 0L, progressFraction = progress, isReleaseAlert = alert,
        )
        assertTrue(item(0.4f).isInProgressForContinueWatchingRow())
        assertFalse(item(0f, nextUp = true).isInProgressForContinueWatchingRow())
        assertFalse(item(0f, alert = true).isInProgressForContinueWatchingRow())
    }
}
