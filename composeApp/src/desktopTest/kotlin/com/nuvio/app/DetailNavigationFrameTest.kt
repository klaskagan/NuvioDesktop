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
import com.nuvio.app.core.ui.DesktopMergedTopBar
import com.nuvio.app.core.ui.DesktopNavigationTabs
import com.nuvio.app.core.ui.FloatingNavigationItem
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
    private var previousLayout = DesktopNavigationLayout.Default

    @Before fun rememberSettings() {
        ThemeSettingsRepository.ensureLoaded()
        previousStyle = ThemeSettingsRepository.navBarStyle.value
        ThemeSettingsRepository.setNavBarStyle(NavBarStyle.ADAPTIVE)
        previousLayout = ThemeSettingsRepository.desktopNavigationLayout.value
        ThemeSettingsRepository.setDesktopNavigationLayout(DesktopNavigationLayout.TopBar)
    }

    @After fun restoreSettings() {
        ThemeSettingsRepository.setNavBarStyle(previousStyle)
        ThemeSettingsRepository.setDesktopNavigationLayout(previousLayout)
    }

    /** The bar used to auto-hide on detail pages; it is now always there, as on the tabs. */
    @Test fun topBarStaysVisibleWithoutMouseMovement() {
        showFrame()
        compose.onNodeWithContentDescription("Search").assertExists()
        compose.mainClock.advanceTimeBy(20_000)
        compose.onNodeWithContentDescription("Search").assertExists()
    }

    @Test fun adaptiveDetailsExpandOnHoverAndReturnToCompactAtTheSamePosition() {
        showFrame()
        val home = compose.onNodeWithContentDescription("Search")
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
        val details = compose.onNodeWithContentDescription("For you").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { reference.value = true }
        compose.mainClock.advanceTimeBy(32)
        val root = compose.onNodeWithContentDescription("For you").fetchSemanticsNode().boundsInRoot
        assertEquals(root.top, details.top, 1f)
        assertEquals(root.left, details.left, 1f)
    }

    @Test fun sidebarRemainsVisibleWithoutMouseMovement() {
        ThemeSettingsRepository.setDesktopNavigationLayout(DesktopNavigationLayout.Sidebar)
        showFrame()
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithContentDescription("Search").assertExists()
    }

    private fun showFrame(reference: State<Boolean>? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NuvioTheme {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    if (reference?.value == true) {
                        DesktopMergedTopBar(
                            tabs = DesktopNavigationTabs(
                                labels = listOf("For you", "Movies", "Shows", "My lists"),
                                selectedIndex = 0,
                                onSelect = {},
                            ),
                            items = listOf("Search", "Library", "Calendar", "Settings").map { label ->
                                FloatingNavigationItem(
                                    label = label,
                                    selected = false,
                                    onClick = {},
                                    icon = Icons.Default.Home,
                                )
                            },
                            windowWidth = 1000.dp,
                            modifier = Modifier.align(Alignment.TopCenter),
                            navBarStyle = NavBarStyle.COMPACT,
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
