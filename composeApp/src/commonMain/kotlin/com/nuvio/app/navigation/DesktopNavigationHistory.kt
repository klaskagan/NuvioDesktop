package com.nuvio.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.AppScreenTab
import com.nuvio.app.features.settings.SettingsCategory
import com.nuvio.app.features.settings.SettingsPage

internal data class DesktopNavigationLocation(
    val tab: AppScreenTab = AppScreenTab.Home,
    val settingsPage: SettingsPage = SettingsPage.Root,
    val settingsCategory: SettingsCategory = SettingsCategory.General,
    val routes: List<AppRoute> = listOf(TabsRoute),
) {
    val isSettingsRootRoute: Boolean get() = tab == AppScreenTab.Settings && routes.lastOrNull() == TabsRoute
}

/** One desktop history for routes, tabs and inline Settings. Screen models stay in their owners. */
internal class DesktopNavigationHistory(
    initial: DesktopNavigationLocation,
    private val routeAvailable: (AppRoute) -> Boolean = { true },
) {
    var current by mutableStateOf(initial)
        private set
    private val past = mutableStateListOf<DesktopNavigationLocation>()
    private val future = mutableStateListOf<DesktopNavigationLocation>()
    private val disposed = mutableStateListOf<AppRoute>()
    var generation by mutableIntStateOf(0)
        private set
    private var profileInitialized = false
    private var profileIdentity: Any? = null

    private fun restorable(location: DesktopNavigationLocation): Boolean =
        location.routes.isNotEmpty() && location.routes.none {
            it is PlayerRoute || it in disposed || !routeAvailable(it)
        }

    private fun previousIndex(): Int = past.indexOfLast {
        it != current && restorable(it) && (!current.isSettingsRootRoute || !it.isSettingsRootRoute)
    }

    private fun parentLocation(): DesktopNavigationLocation? {
        if (!current.isSettingsRootRoute) return null
        val parent = current.settingsPage.parentPage ?: return null
        return current.copy(
            settingsPage = parent,
            settingsCategory = if (parent == SettingsPage.Root) current.settingsCategory else parent.category,
        )
    }

    val canGoBack: Boolean get() = parentLocation() != null || previousIndex() >= 0 ||
        current.routes.size > 1 && restorable(current.copy(routes = current.routes.dropLast(1)))
    val canGoForward: Boolean get() = future.any { it != current && restorable(it) }

    fun clearForward() = future.clear()

    fun visit(location: DesktopNavigationLocation) {
        if (location == current) return
        if (restorable(current)) past.add(current)
        current = location.copy(routes = location.routes.toList())
        future.clear()
    }

    fun visitRoutes(routes: List<AppRoute>) = visit(current.copy(routes = routes))

    fun back(): DesktopNavigationLocation? {
        val departed = current
        val parent = parentLocation()
        val target = if (parent != null) {
            // Settings' parent chain wins over sibling visits and search jumps.
            val ancestors = generateSequence(parent.settingsPage) { it.parentPage }.toSet()
            while (past.lastOrNull()?.let { it.isSettingsRootRoute && it.settingsPage !in ancestors } == true) {
                past.removeAt(past.lastIndex)
            }
            if (past.lastOrNull()?.let { it.isSettingsRootRoute && it.settingsPage == parent.settingsPage } == true) {
                past.removeAt(past.lastIndex)
            }
            parent
        } else {
            val index = previousIndex()
            if (index >= 0) {
                val previous = past[index]
                past.subList(index, past.size).clear()
                previous
            } else if (current.routes.size > 1) {
                current.copy(routes = current.routes.dropLast(1)).takeIf(::restorable)
            } else null
        } ?: return null
        if (restorable(departed)) future.add(departed)
        current = target
        return target
    }

    fun forward(): DesktopNavigationLocation? {
        while (future.isNotEmpty()) {
            val next = future.removeAt(future.lastIndex)
            if (next == current || !restorable(next)) continue
            if (restorable(current)) past.add(current)
            current = next
            return next
        }
        return null
    }

    fun finishPlayback(routes: List<AppRoute>) {
        current = current.copy(routes = routes.toList())
        while (past.lastOrNull() == current) past.removeAt(past.lastIndex)
    }

    fun invalidateRoute(route: AppRoute) {
        if ((route is PlayerRoute || route is StreamRoute || route is CatalogRoute) && route !in disposed) disposed.add(route)
    }

    /** Returns a fresh root only when the owning account/profile actually changes. */
    fun changeProfile(identity: Any?): DesktopNavigationLocation? {
        if (!profileInitialized) {
            profileInitialized = true
            profileIdentity = identity
            return null
        }
        if (identity == profileIdentity) return null
        profileIdentity = identity
        past.clear()
        future.clear()
        disposed.clear()
        current = DesktopNavigationLocation()
        generation++
        return current
    }
}
