package com.nuvio.app.features.details

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nuvio.app.features.details.components.BoardPopoverPlacement
import com.nuvio.app.features.details.components.boardPopoverPosition
import androidx.compose.ui.geometry.Offset
import com.nuvio.app.features.details.components.PopoverNubEdge
import com.nuvio.app.features.details.components.popoverNubFor
import com.nuvio.app.features.details.components.listGroups
import com.nuvio.app.features.details.components.tvPickerSelection
import com.nuvio.app.features.handoff.TvHandoffPlaybackView
import com.nuvio.app.features.player.PlayerControlTvDevice
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Watch it your way's popovers: where they open, which TV is preselected, the "from" time and the list groups. */
class WatchItYourWayPopoverTest {
    private val window = IntSize(1360, 850)
    // The side column of a 1360 × 850 window: 352 wide, 30 from the right edge.
    private val tile = Rect(978f, 520f, 1330f, 664f)
    private val lists = Rect(1218f, 610f, 1318f, 656f)

    @Test
    fun besideOpensJustLeftOfTheActionCentredOnIt() {
        // Lists sits in the tile's third column: the popover ends just left of Lists, over the other cells.
        val at = boardPopoverPosition(BoardPopoverPlacement.Beside, lists, tile, IntSize(400, 300), window, gap = 10f, margin = 16f)
        assertEquals(IntOffset(1218 - 10 - 400, (633 - 150)), at)
    }

    @Test
    fun besideStaysInsideTheWindowWhenTheActionSitsLow() {
        val at = boardPopoverPosition(BoardPopoverPlacement.Beside, lists, tile, IntSize(400, 700), window, gap = 10f, margin = 16f)
        assertEquals(850 - 700 - 16, at.y)
    }

    @Test
    fun belowLinesUpWithTheTilesRightEdgeAndFlipsAboveWithoutRoom() {
        val onTv = Rect(990f, 540f, 1100f, 586f)
        assertEquals(IntOffset(1330 - 420, 596), boardPopoverPosition(BoardPopoverPlacement.Below, onTv, tile, IntSize(420, 220), window, 10f, 16f))
        val flipped = boardPopoverPosition(BoardPopoverPlacement.Below, onTv, tile, IntSize(420, 300), window, 10f, 16f)
        assertEquals(540 - 10 - 300, flipped.y)
    }

    @Test
    fun theArrowPointsAtTheActionFromTheFacingEdge() {
        // Beside the column: on the right edge, level with the action's centre.
        assertEquals(PopoverNubEdge.Right to 150f, popoverNubFor(BoardPopoverPlacement.Beside, lists, IntOffset(568, 483), IntSize(400, 300), 24f))
        // Under the tile: on the top edge, under the action; opened above it: on the bottom edge.
        val onTv = Rect(990f, 540f, 1100f, 586f)
        assertEquals(PopoverNubEdge.Top to 135f, popoverNubFor(BoardPopoverPlacement.Below, onTv, IntOffset(910, 596), IntSize(420, 220), 24f))
        assertEquals(PopoverNubEdge.Bottom to 135f, popoverNubFor(BoardPopoverPlacement.Below, onTv, IntOffset(910, 230), IntSize(420, 300), 24f))
        // Never into a rounded corner.
        assertEquals(PopoverNubEdge.Top to 24f, popoverNubFor(BoardPopoverPlacement.Under, Rect(0f, 0f, 20f, 40f), IntOffset(16, 50), IntSize(400, 300), 24f))
    }

    @Test
    fun quickPickPointsAtTheButtonThatOpenedIt() {
        // Opened from Sources (second column of the tile): left-aligned under it, then pushed in from the
        // window edge; the arrow stays under Sources.
        val sources = Rect(1100f, 600f, 1210f, 646f)
        val placed = quickPickPlacement(sources, Offset.Zero, window, IntSize(1000, 160), 16f, 12f, 24f)
        assertEquals(IntOffset(1360 - 1000 - 16, 658), placed.offset)
        assertEquals(PopoverNubEdge.Top, placed.edge)
        assertEquals(1155f - 344f, placed.along)
    }

    @Test
    fun theTvPickerPreselectsThePickedThenTheLastUsedThenTheFirst() {
        val tvs = listOf(PlayerControlTvDevice("Living room", "sony"), PlayerControlTvDevice("Bedroom", "lg"))
        assertEquals(0, tvPickerSelection(-1, TvHandoffPlaybackView(tvs)))
        assertEquals(1, tvPickerSelection(-1, TvHandoffPlaybackView(tvs, lastUsed = 1)))
        assertEquals(0, tvPickerSelection(0, TvHandoffPlaybackView(tvs, lastUsed = 1)))
        assertEquals(-1, tvPickerSelection(-1, TvHandoffPlaybackView(emptyList())))
    }

    @Test
    fun aTraktPercentageResumesFromItsShareOfTheRuntime() {
        assertEquals(1_102_000L, yourWayResumeMs(1_102_000L, 0.3f, 53))
        // Trakt keeps only a percentage: 40% of 50 minutes is 20:00.
        assertEquals(20 * 60_000L, yourWayResumeMs(0L, 0.4f, 50))
        assertNull(yourWayResumeMs(0L, 0f, 50))
        assertNull(yourWayResumeMs(null, 0.4f, null))
    }

    @Test
    fun listsGroupByWhereTheyLiveWithNuviosOwnFirst() {
        fun tab(key: String, provider: TrackingProviderId?) = TrackingLibraryTab(key, key, provider, TrackingLibraryTabKind.PERSONAL)
        val trakt = TrackingProviderId.entries.first()
        val groups = listGroups(listOf(tab("watchlist", trakt), tab("local", null), tab("mine", trakt)))
        assertEquals(listOf("Nuvio", trakt.displayName), groups.map { it.first })
        assertEquals(listOf("watchlist", "mine"), groups[1].second.map { it.key })
    }
}
