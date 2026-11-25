package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailBoardModelTest {
    private val all = MetaScreenSectionKey.entries

    @Test
    fun tabsFollowTheConfiguredSectionOrder() {
        val order = listOf(
            MetaScreenSectionKey.MORE_LIKE_THIS,
            MetaScreenSectionKey.CAST,
            MetaScreenSectionKey.EPISODES,
            MetaScreenSectionKey.DETAILS,
        )
        val tabs = detailBoardTabs(order, hasContent = { true }, hasStats = false)
        assertEquals(listOf(DetailBoardTab.More, DetailBoardTab.Cast, DetailBoardTab.Episodes, DetailBoardTab.Details), tabs)
    }

    @Test
    fun aTabNeedsAnEnabledSectionWithContent() {
        val tabs = detailBoardTabs(
            enabledSections = all - MetaScreenSectionKey.TRAILERS,
            hasContent = { it != MetaScreenSectionKey.COMMENTS && it != MetaScreenSectionKey.EPISODES },
            hasStats = false,
        )
        assertEquals(false, DetailBoardTab.Trailers in tabs)
        assertEquals(false, DetailBoardTab.Reviews in tabs)
        assertEquals(false, DetailBoardTab.Episodes in tabs)
        assertEquals(true, DetailBoardTab.Cast in tabs)
    }

    @Test
    fun castTabShowsWhenOnlyProductionHasContent() {
        val tabs = detailBoardTabs(all, hasContent = { it == MetaScreenSectionKey.PRODUCTION }, hasStats = false)
        assertEquals(listOf(DetailBoardTab.Cast), tabs)
    }

    @Test
    fun castTabPutsNetworksAboveTheCastWhateverTheConfiguredOrder() {
        val items = listOf(MetaScreenSectionKey.CAST, MetaScreenSectionKey.PRODUCTION, MetaScreenSectionKey.DETAILS)
            .mapIndexed { i, key -> MetaScreenSectionItem(key = key, title = key.name, description = "", enabled = true, order = i) }
        val sections = detailBoardTabSections(DetailBoardTab.Cast, items, hasContent = { true })
        assertEquals(listOf(MetaScreenSectionKey.PRODUCTION, MetaScreenSectionKey.CAST), sections.map { it.key })
    }

    @Test
    fun statsFollowsEpisodesAndHidesWithoutIt() {
        val withEpisodes = detailBoardTabs(
            listOf(MetaScreenSectionKey.CAST, MetaScreenSectionKey.EPISODES, MetaScreenSectionKey.DETAILS),
            hasContent = { true },
            hasStats = true,
        )
        assertEquals(listOf(DetailBoardTab.Cast, DetailBoardTab.Episodes, DetailBoardTab.Stats, DetailBoardTab.Details), withEpisodes)
        val without = detailBoardTabs(listOf(MetaScreenSectionKey.CAST), hasContent = { true }, hasStats = true)
        assertEquals(listOf(DetailBoardTab.Cast), without)
    }

    @Test
    fun progressTotalsSkipSpecialsAndKeepTheCurrentSeasonListed() {
        val counts = listOf(SeasonWatchedCount(0, 1, 3)) + (1..6).map { SeasonWatchedCount(it, it, 10) }
        val progress = detailBoardProgress(counts, currentSeason = 6)!!
        assertEquals(21, progress.watched)
        assertEquals(60, progress.aired)
        assertEquals(listOf(4, 5, 6), progress.seasons.map { it.season })
        assertEquals(listOf(1, 2, 3), detailBoardProgress(counts, currentSeason = 1)!!.seasons.map { it.season })
        assertEquals(listOf(2, 3, 4), detailBoardProgress(counts, currentSeason = 3)!!.seasons.map { it.season })
    }

    @Test
    fun progressFallsBackToSpecialsAndIsNullWithNothingAired() {
        assertEquals(3, detailBoardProgress(listOf(SeasonWatchedCount(0, 1, 3)), currentSeason = null)!!.aired)
        assertNull(detailBoardProgress(listOf(SeasonWatchedCount(1, 0, 0)), currentSeason = 1))
        assertNull(detailBoardProgress(emptyList(), currentSeason = 1))
    }

    @Test
    fun remainingTimeIsRuntimeLessResumePosition() {
        assertEquals(167 * 60_000L, detailBoardRemainingMs("167 min", null))
        assertEquals(107 * 60_000L, detailBoardRemainingMs("2h 47m", 60 * 60_000L))
        assertEquals(0L, detailBoardRemainingMs("10 min", 20 * 60_000L))
        assertNull(detailBoardRemainingMs(null, null))
    }
}

class DetailBoardFillTest {
    private fun item(index: Int, size: Int, fill: Boolean = false) = DetailBoardVisibleItem(index, size, fill)

    @Test
    fun shortTabGetsEnoughFillToKeepTheTabsPinned() {
        // tabs (1), gap (2), one section (3), the filler (4) and the bottom spacer (5) are on screen.
        val visible = listOf(item(1, 56), item(2, 20), item(3, 300), item(4, 0, fill = true), item(5, 32))
        assertEquals(800 - 56 - (20 + 300 + 32), detailBoardFillPx(visible, viewportPx = 800, tabsPx = 56, firstContentIndex = 2))
    }

    @Test
    fun theFillerDoesNotCountItself() {
        val visible = listOf(item(1, 56), item(2, 20), item(3, 300), item(4, 392, fill = true), item(5, 32))
        assertEquals(392, detailBoardFillPx(visible, viewportPx = 800, tabsPx = 56, firstContentIndex = 2))
    }

    @Test
    fun longOrPartlyShownTabsGetNone() {
        // The filler is still below the window.
        assertEquals(0, detailBoardFillPx(listOf(item(0, 440), item(1, 56), item(2, 20), item(3, 600)), 800, 56, 2))
        // The tab's first section has scrolled away, so its height is unknown: it is taller than the window.
        assertEquals(0, detailBoardFillPx(listOf(item(1, 56), item(7, 500), item(8, 0, fill = true)), 800, 56, 2))
        // Content taller than the window never goes negative.
        assertEquals(0, detailBoardFillPx(listOf(item(2, 20), item(3, 900), item(4, 0, fill = true)), 800, 56, 2))
    }

    @Test
    fun settlingTabKeepsAWindowOfRoomForThePinnedRow() {
        // A tab picked from the pinned row: its first frame still holds the row at the top.
        assertEquals(800 - 56, detailBoardSettlingFillPx(measuredPx = 0, viewportPx = 800, tabsPx = 56, settling = true))
        assertEquals(744, detailBoardSettlingFillPx(measuredPx = 300, viewportPx = 800, tabsPx = 56, settling = true))
        // Settled: the measured filler, which still keeps the row pinned.
        assertEquals(300, detailBoardSettlingFillPx(measuredPx = 300, viewportPx = 800, tabsPx = 56, settling = false))
        assertEquals(0, detailBoardSettlingFillPx(measuredPx = 0, viewportPx = 800, tabsPx = 56, settling = false))
    }
}
