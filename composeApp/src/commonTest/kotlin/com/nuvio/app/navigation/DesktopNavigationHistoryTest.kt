package com.nuvio.app.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nuvio.app.AppScreenTab
import com.nuvio.app.features.settings.SettingsCategory
import com.nuvio.app.features.settings.SettingsPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNavigationHistoryTest {
    private val history = DesktopNavigationHistory(DesktopNavigationLocation())
    private val navigator = NuvioNavigator(NavBackStack<NavKey>(TabsRoute), desktopHistory = history)
    private val movie = DetailRoute("movie", "one")
    private val actor = PersonDetailRoute(42, "Actor")

    @Test fun tabsHaveChronologicalBackAndForward() {
        navigator.selectDesktopTab(AppScreenTab.Search)
        navigator.selectDesktopTab(AppScreenTab.Library)
        navigator.selectDesktopTab(AppScreenTab.Settings)
        for (tab in listOf(AppScreenTab.Library, AppScreenTab.Search, AppScreenTab.Home)) {
            assertTrue(navigator.popBackStack())
            assertEquals(tab, history.current.tab)
        }
        assertFalse(navigator.popBackStack())
        for (tab in listOf(AppScreenTab.Search, AppScreenTab.Library, AppScreenTab.Settings)) {
            assertTrue(navigator.goForward())
            assertEquals(tab, history.current.tab)
        }
    }

    @Test fun freshNavigationClearsForwardButReselectDoesNot() {
        navigator.selectDesktopTab(AppScreenTab.Search)
        navigator.popBackStack()
        navigator.selectDesktopTab(AppScreenTab.Home)
        assertTrue(navigator.canGoForward)
        navigator.selectDesktopTab(AppScreenTab.Library)
        assertFalse(navigator.canGoForward)
    }

    @Test fun detailMenuEscapeRestoresEntireRoutePathAndTabContext() {
        navigator.selectDesktopTab(AppScreenTab.Search)
        navigator.navigate(movie)
        navigator.navigate(actor)
        navigator.selectDesktopTab(AppScreenTab.Settings, resetToRoot = true)
        assertEquals(listOf(TabsRoute), navigator.routes)
        navigator.popBackStack()
        assertEquals(listOf(TabsRoute, movie, actor), navigator.routes)
        assertEquals(AppScreenTab.Search, history.current.tab)
        navigator.goForward()
        assertEquals(listOf(TabsRoute), navigator.routes)
        assertEquals(SettingsPage.Root, history.current.settingsPage)
    }

    @Test fun settingsBackUsesParentsAndSkipsSiblingVisits() {
        navigator.selectDesktopTab(AppScreenTab.Settings)
        navigator.openDesktopSettings("Appearance")
        navigator.openDesktopSettings("Playback")
        navigator.popBackStack()
        assertEquals(SettingsPage.Root, history.current.settingsPage)
        navigator.goForward()
        assertEquals(SettingsPage.Playback, history.current.settingsPage)
        navigator.popBackStack()
        navigator.popBackStack()
        assertEquals(AppScreenTab.Home, history.current.tab)
        assertFalse(navigator.canPopBackStack())
    }

    @Test fun directSettingsJumpSynthesizesParentChainAndRestoresCategory() {
        navigator.openDesktopSettings("PairedTvs")
        navigator.popBackStack()
        assertEquals(SettingsPage.Advanced, history.current.settingsPage)
        navigator.popBackStack()
        assertEquals(SettingsPage.Root, history.current.settingsPage)
        assertEquals(SettingsCategory.Advanced, history.current.settingsCategory)
        navigator.goForward()
        assertEquals(SettingsPage.Advanced, history.current.settingsPage)
        navigator.goForward()
        assertEquals(SettingsPage.PairedTvs, history.current.settingsPage)
        navigator.openDesktopSettings("Root", "About")
        navigator.selectDesktopTab(AppScreenTab.Library)
        navigator.popBackStack()
        assertEquals(SettingsCategory.About, history.current.settingsCategory)
        assertEquals(SettingsPage.Root, history.current.settingsPage)
    }

    @Test fun switchingTabsRetainsSettingsPage() {
        navigator.openDesktopSettings("DetailNavigation")
        navigator.selectDesktopTab(AppScreenTab.Library)
        navigator.selectDesktopTab(AppScreenTab.Settings)
        assertEquals(SettingsPage.DetailNavigation, history.current.settingsPage)
    }

    @Test fun playbackCannotBeRestoredByForward() {
        navigator.navigate(movie)
        navigator.navigate(PlayerRoute(1))
        navigator.popBackStack()
        assertEquals(movie, navigator.currentRoute)
        assertFalse(navigator.canGoForward)
        navigator.popBackStack()
        assertEquals(TabsRoute, navigator.currentRoute)
        navigator.goForward()
        assertEquals(movie, navigator.currentRoute)
        assertFalse(navigator.canGoForward)
    }

    @Test fun disposedLaunchRoutesAreSkipped() {
        val stream = StreamRoute(1)
        navigator.navigate(movie)
        navigator.navigate(stream)
        navigator.selectDesktopTab(AppScreenTab.Settings, resetToRoot = true)
        history.invalidateRoute(stream)
        navigator.popBackStack()
        assertEquals(movie, navigator.currentRoute)
    }

    @Test fun profileChangeClearsBothDirectionsAndRoutes() {
        navigator.changeDesktopProfile("first")
        navigator.navigate(movie)
        navigator.navigate(actor)
        navigator.popBackStack()
        navigator.changeDesktopProfile("second")
        assertEquals(DesktopNavigationLocation(), history.current)
        assertEquals(listOf(TabsRoute), navigator.routes)
        assertFalse(navigator.canGoForward)
        assertFalse(navigator.canPopBackStack())
        assertEquals(1, history.generation)
    }
}
