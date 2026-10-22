package com.nuvio.app.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NuvioNavigatorHistoryTest {
    private val movie = DetailRoute("movie", "one")
    private val actor = PersonDetailRoute(42, "Actor")

    @Test fun backAndForwardRestoreNestedPageOrder() {
        val navigator = NuvioNavigator(NavBackStack<NavKey>(TabsRoute, movie, actor))
        assertTrue(navigator.popBackStack())
        assertTrue(navigator.popBackStack())
        assertEquals(TabsRoute, navigator.currentRoute)
        assertFalse(navigator.popBackStack())
        assertTrue(navigator.goForward())
        assertEquals(movie, navigator.currentRoute)
        assertTrue(navigator.goForward())
        assertEquals(actor, navigator.currentRoute)
        assertFalse(navigator.goForward())
    }

    @Test fun newNavigationAndRootNavigationDiscardForwardHistory() {
        val navigator = NuvioNavigator(NavBackStack<NavKey>(TabsRoute, movie, actor))
        navigator.popBackStack()
        navigator.navigate(DetailRoute("movie", "another"))
        assertFalse(navigator.goForward())
        navigator.popBackStack()
        navigator.navigate(TabsRoute) { popUpTo<TabsRoute>(); launchSingleTop = true }
        assertFalse(navigator.goForward())
        assertEquals(listOf(TabsRoute), navigator.routes)
    }

    @Test fun tabSwitchCanDiscardForwardHistoryWithoutPushingARoute() {
        val navigator = NuvioNavigator(NavBackStack<NavKey>(TabsRoute, movie))
        navigator.popBackStack()
        navigator.clearForwardHistory()
        assertFalse(navigator.goForward())
    }

    @Test fun forwardDoesNotRestartPlayback() {
        val navigator = NuvioNavigator(NavBackStack<NavKey>(TabsRoute, movie, PlayerRoute(1)))
        navigator.popBackStack()
        assertEquals(movie, navigator.currentRoute)
        assertFalse(navigator.goForward())
    }
}
