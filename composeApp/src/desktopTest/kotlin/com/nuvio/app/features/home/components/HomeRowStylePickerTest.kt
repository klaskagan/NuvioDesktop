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
        assertEquals(HomeRowStyleChoice.entries, homeRowStyleChoices(key))
        assertTrue(HomeRowStyleChoice.entries.all { it.needs.isEmpty() })
        assertEquals(HomeRowStyleChoice.entries, homeRowStyleChoices(key, setOf(HomeRowStyleNeed.Logos)))
        assertTrue(HomeRowStyleChoice.Landscape.matches(HomeRowStyle.LandscapeSmall))
        assertTrue(!HomeRowStyleChoice.Landscape.matches(HomeRowStyle.BannerWithTitle))
    }

    @Test
    fun opensUnderItsAnchorWithSketchesAndEscClosesWithoutSaving() = runDesktopComposeUiTest(width = 1360, height = 850) {
        withFixture {
            var open by mutableStateOf(true)
            // A tile at the window's right edge: the panel end-aligns to it instead of overflowing.
            val anchor = Rect(left = 1180f, top = 200f, right = 1330f, bottom = 420f)
            setContent {
                AppEnvironment {
                    if (open) HomeRowStylePicker(key, onDismiss = { open = false }, anchor = anchor)
                }
            }
            val panel = onNodeWithTag("home_row_style_picker").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.top >= anchor.bottom, "panel $panel should open under $anchor")
            assertTrue(panel.right <= anchor.right + 1f, "panel $panel should end-align to $anchor")
            HomeRowStyleChoice.entries.forEach { onNodeWithTag("home_row_style_option_${it.id}").assertIsDisplayed() }
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
