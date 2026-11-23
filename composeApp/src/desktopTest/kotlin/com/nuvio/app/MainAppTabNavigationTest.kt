package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.ThemeSettingsRepository
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real app integration: run with -I scripts/desktop-ui-tests.init.gradle to isolate profile storage. */
@OptIn(ExperimentalTestApi::class)
class MainAppTabNavigationTest {
    @Test fun topBarReturnsHomeAfterVisitingAllTabs() = verifyTabs(DesktopNavigationLayout.TopBar)
    @Test fun sidebarReturnsHomeAfterVisitingAllTabs() = verifyTabs(DesktopNavigationLayout.Sidebar)
    @Test fun returningToInitiallySelectedSearchAlsoWorks() = verifyTabs(
        DesktopNavigationLayout.TopBar,
        initialTab = AppScreenTab.Search,
        sequence = listOf("Library", "Settings", "Home", "Search"),
    )
    @Test fun collectionsKeepTopBar() = verifyCollections(DesktopNavigationLayout.TopBar)
    @Test fun collectionsKeepSidebar() = verifyCollections(DesktopNavigationLayout.Sidebar)

    private fun verifyCollections(layout: DesktopNavigationLayout) = runDesktopComposeUiTest(width = 1200, height = 900) {
        assumeTrue("Full app tests require isolated profile storage; use scripts/desktop-ui-tests.init.gradle",
            System.getProperty("nuvio.test.isolatedHome") == "true")
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.setDesktopNavigationLayout(layout)
        setContent {
            AppEnvironment {
                MainAppContent(initialTab = AppScreenTab.Settings, ownsAppRuntime = false, showLaunchOverlay = false)
            }
        }
        onNodeWithText("Layout").performClick()
        onNodeWithText("Collections").assertIsDisplayed()
        mainClock.autoAdvance = false
        onNodeWithText("Collections").performClick()
        mainClock.advanceTimeBy(500)
        onNodeWithText("New Collection").assertIsDisplayed()
        onNodeWithContentDescription("Search").assertIsDisplayed()
        // The navigation stays on every page: it no longer auto-hides or switches off.
        mainClock.advanceTimeBy(10_000)
        onNodeWithContentDescription("Search").assertIsDisplayed()
        val directory = File("build/reports/tab-history").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "collections-${layout.name}.png"))
        onNodeWithText("New Collection").performClick()
        mainClock.advanceTimeBy(500)
        onNodeWithText("Collection name").assertIsDisplayed()
        onNodeWithContentDescription("Search").performClick()
        mainClock.advanceTimeBy(500)
        onNodeWithText("Search movies, shows, and people").assertIsDisplayed()
    }

    private fun verifyTabs(
        layout: DesktopNavigationLayout,
        initialTab: AppScreenTab = AppScreenTab.Home,
        sequence: List<String> = listOf("Search", "Library", "Calendar", "Settings", "Calendar", "Library", "Search", "Home"),
    ) = runDesktopComposeUiTest(width = 1200, height = 900) {
        assumeTrue("Full app tests require isolated profile storage; use scripts/desktop-ui-tests.init.gradle",
            System.getProperty("nuvio.test.isolatedHome") == "true")
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.setDesktopNavigationLayout(layout)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        setContent {
            AppEnvironment {
                MainAppContent(initialTab = initialTab, ownsAppRuntime = false, showLaunchOverlay = false)
            }
        }
        repeat(2) {
            sequence.forEach { tab ->
                // The top bar has no Home item: Home's tabs lead the bar, and For you is Home's default.
                onNodeWithContentDescription(if (tab == "Home" && layout == DesktopNavigationLayout.TopBar) "For you" else tab).performClick()
                waitForIdle()
                if (tab == "Calendar") onNodeWithTag("calendar-screen").assertExists()
                if (tab == "Search") onNodeWithText("Search movies, shows, and people").assertExists()
                else onNodeWithText("Search movies, shows, and people").assertDoesNotExist()
            }
        }
        val directory = File("build/reports/tab-history").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "real-${layout.name}-${initialTab.name}.png"))
    }
}
