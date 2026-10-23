package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.ThemeSettingsRepository
import java.io.File
import javax.imageio.ImageIO
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

    private fun verifyTabs(
        layout: DesktopNavigationLayout,
        initialTab: AppScreenTab = AppScreenTab.Home,
        sequence: List<String> = listOf("Search", "Library", "Settings", "Library", "Search", "Home"),
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
                onNodeWithContentDescription(tab).performClick()
                waitForIdle()
                if (tab == "Search") onNodeWithText("Search movies, shows...").assertExists()
                else onNodeWithText("Search movies, shows...").assertDoesNotExist()
            }
        }
        val directory = File("build/reports/tab-history").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "real-${layout.name}-${initialTab.name}.png"))
    }
}
