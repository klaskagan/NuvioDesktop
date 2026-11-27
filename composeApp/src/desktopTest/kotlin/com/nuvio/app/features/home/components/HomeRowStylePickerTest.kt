package com.nuvio.app.features.home.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.collection.SeasonalCollection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.HomeRowStyleChoice
import com.nuvio.app.features.home.HomeRowStyleNeed
import com.nuvio.app.features.home.homeRowStyleChoices
import com.nuvio.app.features.home.homeRowStyleSupplies
import com.nuvio.app.features.home.collectionRowStyleSupplies
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.trakt.TRAKT_CALENDAR_KEY
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeRowStylePickerTest {
    private val key = "collection_row-style-picker-fixture"

    private fun withFixture(block: () -> Unit) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val repository = HomeCatalogSettingsRepository
        repository.onProfileChanged()
        val collection = Collection(
            id = "row-style-picker-fixture",
            title = "Picker shelf",
            folders = listOf(CollectionFolder(id = "folder", title = "Folder")),
        )
        runBlocking { repository.syncCollections(listOf(collection), addons = emptyList()) }
        repository.setRowStyle(key, HomeRowStyle.Automatic)
        try {
            block()
        } finally {
            repository.setRowStyle(key, HomeRowStyle.Automatic)
            AppKeyboardShortcuts.clearOverlayEscapes()
            repository.onProfileChanged()
        }
    }

    @Test
    fun choicesLeaveOutRowsThatCantChangeStyleAndStylesTheRowCantFill() {
        assertEquals(emptyList(), homeRowStyleChoices(TRAKT_CALENDAR_KEY))
        assertEquals(emptyList(), homeRowStyleChoices(SeasonalCollection.HOME_KEY))
        // A catalog row that can fill everything: all fourteen.
        assertEquals(HomeRowStyleChoice.entries, homeRowStyleChoices(key, HomeRowStyleNeed.entries.toSet()))
        assertEquals(14, HomeRowStyleChoice.entries.size)
        // A collection row (folder tiles) with title logos: the classic six, Compact list, Mosaic,
        // Logo wall and Editorial band; without logos, Logo wall leaves too.
        val folderStyles = listOf(
            HomeRowStyleChoice.Automatic, HomeRowStyleChoice.Posters, HomeRowStyleChoice.Landscape,
            HomeRowStyleChoice.BannerWithTitle, HomeRowStyleChoice.NumberedRanking, HomeRowStyleChoice.Round,
            HomeRowStyleChoice.CompactList, HomeRowStyleChoice.Mosaic, HomeRowStyleChoice.LogoWall, HomeRowStyleChoice.EditorialBand,
        )
        assertEquals(folderStyles, homeRowStyleChoices(key, setOf(HomeRowStyleNeed.Logos)))
        assertEquals(folderStyles - HomeRowStyleChoice.LogoWall, homeRowStyleChoices(key))
        // Each need hides only its own styles.
        val titles = setOf(HomeRowStyleNeed.Titles)
        assertEquals(
            listOf(HomeRowStyleChoice.Spotlight, HomeRowStyleChoice.CollageBanner),
            homeRowStyleChoices(key, titles) - homeRowStyleChoices(key),
        )
        assertTrue(HomeRowStyleChoice.CalendarStrip in homeRowStyleChoices(key, titles + HomeRowStyleNeed.AirDates))
        assertTrue(HomeRowStyleChoice.InfoCards in homeRowStyleChoices(key, titles + HomeRowStyleNeed.Overviews))
        assertTrue(HomeRowStyleChoice.InfoCards !in homeRowStyleChoices(key, titles + HomeRowStyleNeed.AirDates))
        // Editorial band needs nothing: its line is written in the picker.
        assertTrue(HomeRowStyleChoice.EditorialBand.needs.isEmpty())
        assertTrue(HomeRowStyleChoice.Landscape.matches(HomeRowStyle.LandscapeSmall))
        assertTrue(!HomeRowStyleChoice.Landscape.matches(HomeRowStyle.BannerWithTitle))
        HomeRowStyle.entries.filter { it.hasOwnLayout }.forEach {
            assertTrue(!it.isLandscapeSize, "$it is not a Landscape size")
            assertTrue(!HomeRowStyleChoice.Landscape.matches(it))
        }
    }

    @Test
    fun suppliesFollowWhatMostOfTheRowHas() {
        val today = "2026-11-27"
        fun item(i: Int, logo: Boolean = false, overview: Boolean = false, date: String? = null) =
            MetaPreview(id = "t$i", type = "movie", name = "T$i", logo = "logo".takeIf { logo }, description = "plot".takeIf { overview }, rawReleaseDate = date)
        val plain = (1..5).map { item(it) }
        assertEquals(setOf(HomeRowStyleNeed.Titles), homeRowStyleSupplies(plain, today))
        // Three of five (60 %) is enough; two is not.
        val logos = (1..5).map { item(it, logo = it <= 3) }
        assertTrue(HomeRowStyleNeed.Logos in homeRowStyleSupplies(logos, today))
        assertTrue(HomeRowStyleNeed.Logos !in homeRowStyleSupplies((1..5).map { item(it, logo = it <= 2) }, today))
        assertTrue(HomeRowStyleNeed.Overviews in homeRowStyleSupplies((1..5).map { item(it, overview = true) }, today))
        // Upcoming dates: today or later only.
        val upcoming = (1..5).map { item(it, date = if (it <= 3) "2026-11-2${it + 6}" else "2025-01-01") }
        assertTrue(HomeRowStyleNeed.AirDates in homeRowStyleSupplies(upcoming, today))
        val past = (1..5).map { item(it, date = "2026-11-2${it}") }
        assertTrue(HomeRowStyleNeed.AirDates !in homeRowStyleSupplies(past, today))
        // Folder tiles: logos only, from the folders' title logos.
        val folders = (1..3).map { CollectionFolder(id = "f$it", title = "F$it", titleLogoUrl = "logo".takeIf { _ -> it <= 2 }) }
        assertEquals(setOf(HomeRowStyleNeed.Logos), collectionRowStyleSupplies(folders))
        assertEquals(emptySet(), collectionRowStyleSupplies(folders.map { it.copy(titleLogoUrl = null) }))
    }

    @Test
    fun editorialBandOpensItsDescriptionFieldAndEnterSavesTheLine() = runDesktopComposeUiTest(width = 1360, height = 850) {
        withFixture {
            var open by mutableStateOf(true)
            setContent {
                AppEnvironment {
                    if (open) HomeRowStylePicker(key, onDismiss = { open = false }, anchor = Rect(100f, 40f, 250f, 120f))
                }
            }
            onNodeWithTag("home_row_description_field").assertDoesNotExist()
            onNodeWithTag("home_row_style_option_editorial-band").performClick()
            // The panel stays open with the field under the grid.
            onNodeWithTag("home_row_description_field").assertIsDisplayed()
            runOnIdle { assertEquals(HomeRowStyle.EditorialBand, HomeCatalogSettingsRepository.uiState.value.rowStyles[key]) }
            onNodeWithTag("home_row_description_field").performTextInput("Hand-picked folders")
            // Typed live into the row before it is saved.
            runOnIdle { assertEquals("Hand-picked folders", RowDescriptionDrafts.of(key)) }
            onNodeWithTag("home_row_description_field").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
            waitUntil(timeoutMillis = 5_000) { !open }
            runOnIdle {
                assertEquals("Hand-picked folders", HomeCatalogSettingsRepository.uiState.value.rowDescriptions[key])
                assertEquals(null, RowDescriptionDrafts.of(key))
            }
            HomeCatalogSettingsRepository.setRowDescription(key, "")
            assertEquals(null, HomeCatalogSettingsRepository.uiState.value.rowDescriptions[key])
        }
    }

    @Test
    fun opensUnderItsAnchorWithSketchesAndEscClosesWithoutSaving() = runDesktopComposeUiTest(width = 1360, height = 850) {
        withFixture {
            var open by mutableStateOf(true)
            // A tile at the window's right edge: the panel end-aligns to it instead of overflowing.
            val anchor = Rect(left = 1180f, top = 30f, right = 1330f, bottom = 110f)
            setContent {
                AppEnvironment {
                    if (open) HomeRowStylePicker(key, onDismiss = { open = false }, anchor = anchor)
                }
            }
            val panel = onNodeWithTag("home_row_style_picker").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.top >= anchor.bottom, "panel $panel should open under $anchor")
            assertTrue(panel.right <= anchor.right + 1f, "panel $panel should end-align to $anchor")
            // A collection row not on screen: the styles folder tiles can fill without logos.
            homeRowStyleChoices(key).forEach { onNodeWithTag("home_row_style_option_${it.id}").assertIsDisplayed() }
            onNodeWithTag("home_row_style_option_spotlight").assertDoesNotExist()
            onNodeWithText("Picker shelf").assertIsDisplayed()
            onNodeWithText("Automatic").assertIsSelected()
            onNodeWithTag("home_row_style_size_landscape-medium").assertDoesNotExist()

            runOnIdle { assertTrue(AppKeyboardShortcuts.overlayEscape?.invoke() == true) }
            waitUntil(timeoutMillis = 5_000) { !open }
            onNodeWithTag("home_row_style_picker").assertDoesNotExist()
            assertEquals(HomeRowStyle.Automatic, HomeCatalogSettingsRepository.uiState.value.rowStyles[key] ?: HomeRowStyle.Automatic)
        }
    }

    @Test
    fun clickingTheDimmedPageCloses() = runDesktopComposeUiTest(width = 1360, height = 850) {
        withFixture {
            var open by mutableStateOf(true)
            setContent {
                AppEnvironment {
                    if (open) HomeRowStylePicker(key, onDismiss = { open = false }, anchor = Rect(100f, 100f, 250f, 320f))
                }
            }
            onNodeWithTag("home_row_style_picker").assertIsDisplayed()
            onAllNodes(hasClickAction()).onFirst().performMouseInput { click(Offset(1300f, 800f)) }
            waitUntil(timeoutMillis = 5_000) { !open }
        }
    }

    @Test
    fun unsupportedRowsDismissStraightAway() = runDesktopComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            AppEnvironment {
                if (open) HomeRowStylePicker(TRAKT_CALENDAR_KEY, onDismiss = { open = false })
            }
        }
        waitUntil(timeoutMillis = 5_000) { !open }
        onNodeWithTag("home_row_style_picker").assertDoesNotExist()
    }
}
