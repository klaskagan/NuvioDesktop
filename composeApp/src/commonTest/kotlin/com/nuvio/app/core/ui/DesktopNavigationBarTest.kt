package com.nuvio.app.core.ui

import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.NavBarStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNavigationBarTest {
    @Test
    fun desktopLayoutPreferencesKeepTopBarAndSidebar() {
        assertEquals(DesktopNavigationLayout.TopBar, DesktopNavigationLayout.fromName("TopBar"))
        assertEquals(DesktopNavigationLayout.TopBar, DesktopNavigationLayout.fromName("topbar"))
        assertEquals(DesktopNavigationLayout.Sidebar, DesktopNavigationLayout.fromName("Sidebar"))
        assertEquals(DesktopNavigationLayout.Default, DesktopNavigationLayout.fromName(null))
    }

    @Test
    fun adaptiveShowsIconsUntilHoveredOnEveryScreen() {
        assertEquals(0f, labels())
        assertEquals(1f, labels(hovered = true))
        assertEquals(1f, labels(popup = true))
    }

    @Test
    fun explicitStylesOverrideHover() {
        assertEquals(0f, labels(style = NavBarStyle.COMPACT, hovered = true, popup = true))
        assertEquals(1f, labels(style = NavBarStyle.EXPANDED))
    }

    private fun labels(
        style: NavBarStyle = NavBarStyle.ADAPTIVE,
        hovered: Boolean = false,
        popup: Boolean = false,
    ): Float = desktopNavigationLabelFraction(style, hovered, popup)
}
