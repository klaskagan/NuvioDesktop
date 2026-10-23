package com.nuvio.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nuvio.app.AppScreenTab
import com.nuvio.app.RootTabHost
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.settings.SettingsPage
import com.nuvio.app.features.settings.SettingsScreen
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopTabHistoryUiTest {

    @Test fun compactSettingsRestoresPageThroughTabHistory() = verifySettings(900)
    @Test fun wideSettingsRestoresPageThroughTabHistory() = verifySettings(1200)

    private fun verifySettings(width: Int) = runDesktopComposeUiTest(width = width, height = 900) {
        val compose = this
        val history = DesktopNavigationHistory(DesktopNavigationLocation())
        val navigator = NuvioNavigator(NavBackStack<NavKey>(TabsRoute), desktopHistory = history)
        val instances = mutableMapOf<AppScreenTab, Any>()
        compose.setContent {
            NuvioTheme {
                Box(Modifier.requiredSize(width.dp, 900.dp)) {
                    RootTabHost(selectedTab = history.current.tab) { tab ->
                        val instance = remember { Any() }
                        instances[tab] = instance
                        if (tab == AppScreenTab.Settings) {
                            SettingsScreen(
                                desktopPageName = history.current.settingsPage.name,
                                desktopCategoryName = history.current.settingsCategory.name,
                                onDesktopPageChange = { navigator.openDesktopSettings(it) },
                                onDesktopCategoryChange = { navigator.openDesktopSettings("Root", it) },
                                onDesktopBack = { navigator.popBackStack() },
                                isSelectedTab = history.current.tab == AppScreenTab.Settings,
                                rootActionsEnabled = history.current.tab == AppScreenTab.Settings,
                            )
                        } else Text(tab.name)
                    }
                }
            }
        }
        compose.runOnIdle {
            navigator.selectDesktopTab(AppScreenTab.Search)
        }
        compose.onNodeWithText("Search").assertExists()
        val searchInstance = instances[AppScreenTab.Search]
        compose.runOnIdle { navigator.openDesktopSettings("DetailNavigation") }
        compose.onNodeWithText("Auto-hide detail top bar").assertExists()
        val directory = File("build/reports/tab-history").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(directory, "settings-$width.png"))
        compose.runOnIdle { navigator.selectDesktopTab(AppScreenTab.Library) }
        compose.onNodeWithText("Library").assertExists()
        compose.runOnIdle { navigator.popBackStack() }
        compose.onNodeWithText("Auto-hide detail top bar").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(SettingsPage.Appearance, history.current.settingsPage) }
        compose.runOnIdle { navigator.goForward() }
        compose.onNodeWithText("Auto-hide detail top bar").assertExists()
        compose.runOnIdle {
            navigator.popBackStack()
            navigator.popBackStack()
            navigator.popBackStack()
        }
        compose.onNodeWithText("Search").assertExists()
        compose.runOnIdle { assertEquals(searchInstance, instances[AppScreenTab.Search]) }
    }
}
