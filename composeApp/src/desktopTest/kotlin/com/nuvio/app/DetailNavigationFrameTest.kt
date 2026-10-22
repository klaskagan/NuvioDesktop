package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DesktopNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.desktopNavigationContentPadding
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DetailNavigationFrameTest {
    @get:Rule val compose = createComposeRule()
    private var previousStyle = NavBarStyle.ADAPTIVE
    private var previousHideDelay = 3
    private var previousAutoHide = true
    private var previousEnabled = true
    private var previousLayout = DesktopNavigationLayout.Default

    @Before fun rememberSettings() {
        ThemeSettingsRepository.ensureLoaded()
        previousHideDelay = ThemeSettingsRepository.detailNavigationHideDelaySeconds.value
        ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(3)
        previousAutoHide = ThemeSettingsRepository.detailNavigationAutoHide.value
        ThemeSettingsRepository.setDetailNavigationAutoHide(true)
        previousStyle = ThemeSettingsRepository.navBarStyle.value
        ThemeSettingsRepository.setNavBarStyle(NavBarStyle.ADAPTIVE)
        previousEnabled = ThemeSettingsRepository.detailNavigationEnabled.value
        previousLayout = ThemeSettingsRepository.desktopNavigationLayout.value
        ThemeSettingsRepository.setDetailNavigationEnabled(true)
        ThemeSettingsRepository.setDesktopNavigationLayout(DesktopNavigationLayout.TopBar)
    }

    @After fun restoreSettings() {
        ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(previousHideDelay)
        ThemeSettingsRepository.setDetailNavigationAutoHide(previousAutoHide)
        ThemeSettingsRepository.setNavBarStyle(previousStyle)
        ThemeSettingsRepository.setDetailNavigationEnabled(previousEnabled)
        ThemeSettingsRepository.setDesktopNavigationLayout(previousLayout)
    }

    @Test fun topBarHidesAfterInactivityAndMouseMovementRestartsTimer() {
        showFrame()
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.mainClock.advanceTimeBy(3_050)
        // Keep the menu composed while it fades and slides out.
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
        compose.onRoot().performMouseInput { enter(Offset(500f, 400f)) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().performMouseInput { moveTo(Offset(510f, 400f)) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test fun adaptiveDetailsExpandOnHoverAndReturnToCompactAtTheSamePosition() {
        showFrame()
        val home = compose.onNodeWithContentDescription("Home")
        val compact = home.fetchSemanticsNode().boundsInRoot
        home.performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(400)
        val expanded = home.fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.width > compact.width * 1.5f)
        assertEquals(compact.top, expanded.top, 1f)
        compose.onRoot().performMouseInput { moveTo(Offset(500f, 400f)) }
        compose.mainClock.advanceTimeBy(400)
        val restored = home.fetchSemanticsNode().boundsInRoot
        assertEquals(compact.width, restored.width, 1f)
        assertEquals(compact.top, restored.top, 1f)
    }

    @Test fun detailTopBarMatchesRootTopBarPosition() {
        ThemeSettingsRepository.setNavBarStyle(NavBarStyle.COMPACT)
        val reference = mutableStateOf(false)
        showFrame(reference)
        val details = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { reference.value = true }
        compose.mainClock.advanceTimeBy(32)
        val root = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot
        assertEquals(root.top, details.top, 1f)
        assertEquals(root.left, details.left, 1f)
    }

    @Test fun alwaysVisibleTopBarDoesNotHideDuringInactivity() {
        ThemeSettingsRepository.setDetailNavigationAutoHide(false)
        showFrame()
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithContentDescription("Home").assertExists()
    }

    @Test fun disablingAutoHideRevealsHiddenTopBarImmediately() {
        showFrame()
        compose.mainClock.advanceTimeBy(3_400)
        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
        compose.runOnIdle { ThemeSettingsRepository.setDetailNavigationAutoHide(false) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithContentDescription("Home").assertExists()
    }

    @Test fun configuredDelayKeepsTheBarVisibleBeyondTheDefaultThreeSeconds() {
        ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(5)
        showFrame()
        compose.mainClock.advanceTimeBy(3_400)
        compose.onNodeWithContentDescription("Home").assertExists()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    @Test fun sidebarRemainsVisibleWithoutMouseMovement() {
        ThemeSettingsRepository.setDesktopNavigationLayout(DesktopNavigationLayout.Sidebar)
        showFrame()
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithContentDescription("Home").assertExists()
    }

    @Test fun disabledSettingLeavesDetailContentWithoutNavigation() {
        ThemeSettingsRepository.setDetailNavigationEnabled(false)
        showFrame()
        compose.onNodeWithContentDescription("Home").assertDoesNotExist()
    }

    private fun showFrame(reference: State<Boolean>? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NuvioTheme {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    if (reference?.value == true) {
                        DesktopNavigationBar(
                            items = listOf("Home", "Search", "Library", "Settings").mapIndexed { index, label ->
                                FloatingNavigationItem(
                                    label = label,
                                    selected = index == 0,
                                    onClick = {},
                                    icon = Icons.Default.Home,
                                )
                            },
                            modifier = Modifier.align(Alignment.TopCenter),
                            contentPadding = desktopNavigationContentPadding(),
                            navBarStyle = NavBarStyle.COMPACT,
                            windowWidth = 1000.dp,
                        )
                    } else DetailNavigationFrame(
                        route = "movie",
                        selectedTab = AppScreenTab.Home,
                        onTabSelected = {},
                        onProfileSelected = {},
                        onAddProfileRequested = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
    }
}
