package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.ThemeSettingsRepository
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The tabs shell owns one ambient backdrop. Real app integration: run with
 * -I scripts/desktop-ui-tests.init.gradle to isolate profile storage.
 */
@OptIn(ExperimentalTestApi::class)
class ShellBackdropUiTest {
    @Test fun backdropRunsUnderTheSidebarOnSearchOnly() = runDesktopComposeUiTest(width = 1200, height = 900) {
        assumeTrue("Full app tests require isolated profile storage; use scripts/desktop-ui-tests.init.gradle",
            System.getProperty("nuvio.test.isolatedHome") == "true")
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.setDesktopNavigationLayout(DesktopNavigationLayout.Sidebar)
        ThemeSettingsRepository.setShellBackdrop(ShellBackdropMode.Ambient)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        setContent {
            AppEnvironment {
                MainAppContent(initialTab = AppScreenTab.Search, ownsAppRuntime = false, showLaunchOverlay = false)
            }
        }
        val searchAmbient = capture("search-ambient")

        onNodeWithContentDescription("Search").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        capture("search-ambient-sidebar-hovered")
        onRoot().performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)

        onNodeWithContentDescription("Library").performClick()
        mainClock.advanceTimeBy(600)
        val library = capture("library")

        onNodeWithContentDescription("Search").performClick()
        mainClock.advanceTimeBy(600)
        runOnIdle { ThemeSettingsRepository.setShellBackdrop(ShellBackdropMode.Solid) }
        mainClock.advanceTimeBy(600)
        val searchSolid = capture("search-solid")
        runOnIdle { ThemeSettingsRepository.setShellBackdrop(ShellBackdropMode.Ambient) }

        // A point in the collapsed sidebar strip, below the navigation items.
        val x = 34
        val y = 860
        val solid = searchSolid.getRGB(x, y)
        assertNotEquals(solid, searchAmbient.getRGB(x, y), "Search's backdrop should show through the frosted sidebar")
        assertEquals(solid, library.getRGB(x, y), "Tabs outside ShellBackdropTabs keep the solid sidebar")
    }

    private fun SkikoComposeUiTest.capture(name: String): BufferedImage {
        waitForIdle()
        val image = onRoot().captureToImage().toAwtImage()
        val directory = File("build/reports/shell-backdrop").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "$name.png"))
        return image
    }
}
