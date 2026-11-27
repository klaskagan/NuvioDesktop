package com.nuvio.app.features.home.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.nextEpisodeLine
import com.nuvio.app.features.home.upcomingDate
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeRowStyleLayoutsTest {
    private val today = CurrentDateProvider.todayIsoDate()

    private fun plusDays(days: Int): String = com.nuvio.app.features.trakt.calendarDatePlusDays(today, days)

    private val entries = (1..12).map { index ->
        MetaPreview(
            id = "fixture-$index",
            type = if (index % 2 == 0) "series" else "movie",
            name = "Fixture $index",
            logo = "https://example.com/logo-$index.png",
            description = "A fixture plot for title $index.",
            releaseInfo = "2026",
            imdbRating = "8.$index",
            genres = listOf("Drama"),
            rawReleaseDate = plusDays(index / 4),
            banner = "https://example.com/backdrop-$index.jpg",
            posterShape = PosterShape.Poster,
            catalogRank = if (index == 3) 1 else null,
        )
    }

    private val section = HomeCatalogSection(
        key = "fixture:movie:styled",
        title = "Styled fixture row",
        subtitle = "",
        addonName = "Fixture addon",
        target = CatalogTarget.Addon("https://example.com/manifest.json", "movie", "styled"),
        items = entries,
        availableItemCount = 1240,
    )

    @Test
    fun mosaicPutsTheBestRankedTitleOfEachFiveInTheBigSlot() {
        val groups = mosaicGroups(entries) { it.catalogRank }
        assertEquals(listOf(5, 5, 2), groups.map { it.size })
        assertEquals("fixture-3", groups[0].first().id)
        assertEquals(listOf("fixture-1", "fixture-2", "fixture-4", "fixture-5"), groups[0].drop(1).map { it.id })
        // No ranks: the group's first title.
        assertEquals("fixture-6", groups[1].first().id)
    }

    @Test
    fun airingNextDescriptionsGiveTheNextAirDateAndEpisode() {
        val show = MetaPreview(
            id = "tt1", type = "series", name = "Dexter: Resurrection", rawReleaseDate = "2025-07-11",
            description = "Next Episode: S02E05 — Out Of The Past · Airs ${plusDays(3)}",
        )
        assertEquals(plusDays(3), show.upcomingDate(today))
        assertEquals("S2 E5 · Out Of The Past", show.nextEpisodeLine())
        // A past premiere alone is not upcoming; a passed air date neither.
        assertEquals(null, show.copy(description = null).upcomingDate(today))
        assertEquals(null, show.copy(description = "Next Episode: S02E05 · Airs ${plusDays(-2)}").upcomingDate(today))
        assertEquals("S2 E5", show.copy(description = "Next Episode: S02E05 · Airs 2030-01-01").nextEpisodeLine())
        // Mostly upcoming rows can pick Calendar strip.
        val row = (1..5).map { show.copy(id = "tt$it") }
        assertEquals(true, com.nuvio.app.features.home.HomeRowStyleNeed.AirDates in com.nuvio.app.features.home.homeRowStyleSupplies(row, today))
    }

    @Test
    fun dayHeadersNameTheMonthBeyondTheComingWeek() {
        val weekday = { _: String -> "Day" }
        val month = { date: String -> "M" + date.substring(5, 7) }
        assertEquals("Day 30", calendarDayLabel("2026-11-30", "2026-11-27", weekday, month))
        assertEquals("Day 3", calendarDayLabel("2026-12-03", "2026-11-27", weekday, month))
        assertEquals("Day 4 M12", calendarDayLabel("2026-12-04", "2026-11-27", weekday, month))
        assertEquals("Day 25 M12", calendarDayLabel("2026-12-25", "2026-11-27", weekday, month))
    }

    @Test
    fun airTimeComesFromTheTraktCalendarByIdAndDay() {
        val media = com.nuvio.app.features.trakt.CalendarMedia(id = "tt777", type = "series", title = "Dexter", imdbId = "tt777", tmdbId = 42)
        val card = com.nuvio.app.features.trakt.CalendarCard("c", media, "2026-11-30", releaseEpochMs = 1_000L, daysAway = 3)
        val item = MetaPreview(id = "tmdb:42", type = "series", name = "Dexter")
        assertEquals(1_000L, calendarAirTime(item, "2026-11-30", listOf(card)))
        assertEquals(null, calendarAirTime(item, "2026-12-01", listOf(card)))
        assertEquals(1_000L, calendarAirTime(item.copy(id = "tt777"), "2026-11-30", listOf(card)))
        assertEquals(null, calendarAirTime(item.copy(id = "tt1"), "2026-11-30", listOf(card)))
    }

    @Test
    fun calendarStripGroupsByDayAndSkipsPastAndUndatedTitles() {
        val dated = entries.take(6) + MetaPreview(id = "old", type = "movie", name = "Old", rawReleaseDate = plusDays(-3)) +
            MetaPreview(id = "undated", type = "movie", name = "Undated")
        val days = calendarDays(dated, today)
        assertEquals(listOf(today, plusDays(1)), days.map { it.date })
        assertEquals(listOf("fixture-1", "fixture-2", "fixture-3"), days[0].items.map { it.id })
        assertEquals(listOf("fixture-4", "fixture-5", "fixture-6"), days[1].items.map { it.id })
    }

    @Test
    fun everyCatalogStyleDrawsItsOwnLayoutAndCollageOpensSeeAll() = runDesktopComposeUiTest(width = 1360, height = 850) {
        var style by mutableStateOf(HomeRowStyle.CompactList)
        var seeAll = 0
        var opened: String? = null
        setContent {
            AppEnvironment {
                HomeCatalogRowSection(
                    section = section,
                    rowStyle = style,
                    eyebrow = section.addonName,
                    onViewAllClick = { seeAll++ },
                    onPosterClick = { opened = it.id },
                )
            }
        }
        val report = File("build/reports/home-row-styles").apply { mkdirs() }
        val expected = mapOf(
            HomeRowStyle.CompactList to "home_compact_row",
            HomeRowStyle.Mosaic to "home_mosaic_group",
            HomeRowStyle.Spotlight to "home_row_spotlight",
            HomeRowStyle.LogoWall to "home_row_styled_logo-wall",
            HomeRowStyle.CalendarStrip to "home_calendar_chip",
            HomeRowStyle.InfoCards to "home_info_card",
            HomeRowStyle.EditorialBand to "home_editorial_band",
            HomeRowStyle.CollageBanner to "home_collage_banner",
        )
        expected.forEach { (rowStyle, tag) ->
            runOnIdle { style = rowStyle }
            onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().assertExists()
            onNodeWithTag("home_row_styled_${rowStyle.id}").assertExists()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", report.resolve("${rowStyle.id}.png"))
        }
        // Calendar strip: the rule under each day header spans its cards.
        runOnIdle { style = HomeRowStyle.CalendarStrip }
        val rule = onAllNodesWithTag("home_calendar_day_rule", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        val dayBox = onAllNodesWithTag("home_calendar_day", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(rule.width > 200f && kotlin.math.abs(rule.width - dayBox.width) < 2f, "rule $rule should span $dayBox")
        // Compact list: columns of three.
        runOnIdle { style = HomeRowStyle.CompactList }
        onAllNodesWithTag("home_row_styled_compact-list").assertCountEquals(1)
        onAllNodesWithTag("home_compact_row", useUnmergedTree = true).onFirst().performClick()
        runOnIdle { assertEquals("fixture-1", opened) }

        runOnIdle { style = HomeRowStyle.CollageBanner }
        onNodeWithTag("home_collage_open").performClick()
        runOnIdle { assertEquals(1, seeAll) }
        onNodeWithText("Styled fixture row").assertIsDisplayed()
    }

    @Test
    fun infoCardLibraryButtonTogglesTheTitle() = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        com.nuvio.app.features.library.LibraryRepository.ensureLoaded()
        val titles = entries.mapIndexed { i, item -> item.copy(id = "tt90000${i + 10}") }
        val first = titles.first()
        // The button shows (and flips) the list its toggle writes to.
        fun saved() = com.nuvio.app.features.library.LibraryRepository.isSavedByDefault(first.id, first.type)
        val before = saved()
        setContent {
            AppEnvironment { HomeCatalogRowSection(section = section.copy(items = titles), entries = titles, rowStyle = HomeRowStyle.InfoCards, onPosterClick = {}) }
        }
        val button = hasTestTag("home_info_card_library") and hasContentDescription(first.name)
        onNode(button, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) { saved() != before }
        onNode(button, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) { saved() == before }
    }

    @Test
    fun editorialBandShowsTheRowDescriptionWhileItIsTyped() = runDesktopComposeUiTest(width = 1360, height = 850) {
        setContent {
            AppEnvironment {
                HomeCatalogRowSection(section = section, rowStyle = HomeRowStyle.EditorialBand, onViewAllClick = {})
            }
        }
        onNodeWithTag("home_editorial_description").assertDoesNotExist()
        try {
            runOnIdle { RowDescriptionDrafts.update(section.key, "Big ideas and practical effects") }
            onNodeWithText("Big ideas and practical effects").assertIsDisplayed()
        } finally {
            RowDescriptionDrafts.clear(section.key)
        }
    }

    @Test
    fun collectionRowsDrawFolderTilesInTheirStyles() = runDesktopComposeUiTest(width = 1360, height = 850) {
        val folders = (1..7).map { CollectionFolder(id = "f$it", title = "Folder $it", titleLogoUrl = "https://example.com/l$it.png") }
        val collection = Collection(id = "styled-collection", title = "Studios", folders = folders)
        var style by mutableStateOf(HomeRowStyle.Mosaic)
        var opened: String? = null
        setContent {
            AppEnvironment {
                HomeCollectionRowSection(
                    collection = collection,
                    rowStyleKey = "collection_styled-collection",
                    rowStyle = style,
                    onFolderClick = { _, folderId -> opened = folderId },
                )
            }
        }
        listOf(HomeRowStyle.Mosaic, HomeRowStyle.CompactList, HomeRowStyle.LogoWall, HomeRowStyle.EditorialBand).forEach { rowStyle ->
            runOnIdle { style = rowStyle }
            onNodeWithTag("home_row_styled_${rowStyle.id}").assertExists()
            onAllNodesWithTag("home_styled_folder_tile", useUnmergedTree = true).onFirst().assertExists()
        }
        onAllNodesWithTag("home_styled_folder_tile", useUnmergedTree = true).onFirst().performClick()
        runOnIdle { assertEquals("f1", opened) }
    }
}
