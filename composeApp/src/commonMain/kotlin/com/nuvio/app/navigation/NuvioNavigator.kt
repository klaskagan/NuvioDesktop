package com.nuvio.app.navigation

import androidx.compose.runtime.mutableStateListOf
import com.nuvio.app.AppScreenTab
import com.nuvio.app.features.settings.SettingsCategory
import com.nuvio.app.features.settings.SettingsPage
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

internal class NuvioNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val onExternalNavigate: ((AppRoute, launchSingleTop: Boolean) -> Unit)? = null,
    private val onExternalBack: (() -> Unit)? = null,
    private val onExternalReplace: ((AppRoute) -> Unit)? = null,
    private val onLocalNavigate: ((AppRoute?, AppRoute) -> Unit)? = null,
    private val onLocalPop: (() -> Unit)? = null,
    internal val desktopHistory: DesktopNavigationHistory? = null,
) {
    private val forwardHistory = mutableStateListOf<AppRoute>()
    val canGoForward: Boolean get() = desktopHistory?.canGoForward ?: (onExternalNavigate == null && forwardHistory.isNotEmpty())

    fun clearForwardHistory() {
        desktopHistory?.clearForward() ?: forwardHistory.clear()
    }

    fun goForward(): Boolean {
        if (desktopHistory != null) return restoreDesktop(desktopHistory.forward())
        if (onExternalNavigate != null || forwardHistory.isEmpty()) return false
        val route = forwardHistory.removeAt(forwardHistory.lastIndex)
        onLocalNavigate?.invoke(currentRoute, route)
        backStack.add(route)
        return true
    }

    val currentRoute: AppRoute?
        get() = backStack.lastOrNull() as? AppRoute

    val routes: List<AppRoute>
        get() = backStack.filterIsInstance<AppRoute>()

    fun navigate(route: AppRoute, options: NuvioNavigateOptions.() -> Unit = {}) {
        val resolvedOptions = NuvioNavigateOptions().apply(options)

        if (resolvedOptions.launchSingleTop && currentRoute == route) return
        clearForwardHistory()

        val popUpToRoute = resolvedOptions.popUpToRoute
        if (popUpToRoute != null) {
            val targetIndex = backStack.indexOfLast { popUpToRoute.isInstance(it) }
            if (targetIndex >= 0) {
                if (
                    onExternalReplace != null &&
                    resolvedOptions.popUpToInclusive &&
                    targetIndex == backStack.lastIndex
                ) {
                    onExternalReplace(route)
                    return
                }

                val firstRemovedIndex = if (resolvedOptions.popUpToInclusive) targetIndex else targetIndex + 1
                if (firstRemovedIndex <= backStack.lastIndex) {
                    backStack.subList(firstRemovedIndex, backStack.size).clear()
                }
            }
        }

        if (resolvedOptions.launchSingleTop && currentRoute == route) {
            desktopHistory?.visitRoutes(routes)
            return
        }
        if (onExternalNavigate != null) {
            onExternalNavigate.invoke(route, resolvedOptions.launchSingleTop)
        } else {
            onLocalNavigate?.invoke(currentRoute, route)
            backStack.add(route)
            desktopHistory?.visitRoutes(routes)
        }
    }

    fun canPopBackStack(expectedRoute: AppRoute? = null): Boolean {
        if (expectedRoute != null && currentRoute != expectedRoute) return false
        return if (desktopHistory != null && currentRoute !is PlayerRoute) desktopHistory.canGoBack
        else backStack.size > 1 || onExternalBack != null
    }

    fun popBackStack(expectedRoute: AppRoute? = null): Boolean {
        if (expectedRoute != null && currentRoute != expectedRoute) return false
        if (desktopHistory != null && currentRoute !is PlayerRoute) return restoreDesktop(desktopHistory.back())
        if (backStack.size > 1) {
            onLocalPop?.invoke()
            val popped = currentRoute
            // Never restart playback through browser-style forward navigation.
            if (popped is PlayerRoute) clearForwardHistory()
            else if (popped != null) forwardHistory.add(popped)
            backStack.removeAt(backStack.lastIndex)
            desktopHistory?.finishPlayback(routes)
            return true
        }
        onExternalBack?.invoke()
        return onExternalBack != null
    }

    fun selectDesktopTab(tab: AppScreenTab, resetToRoot: Boolean = false) {
        val history = desktopHistory ?: return
        val location = history.current.copy(
            tab = tab,
            settingsPage = if (resetToRoot && tab == AppScreenTab.Settings) SettingsPage.Root else history.current.settingsPage,
            routes = if (resetToRoot) listOf(TabsRoute) else routes,
        )
        history.visit(location)
        restoreDesktop(location)
    }

    fun openDesktopSettings(pageName: String, categoryName: String? = null) {
        val history = desktopHistory ?: return
        val page = SettingsPage.entries.firstOrNull { it.name == pageName } ?: return
        val category = if (page != SettingsPage.Root) page.category else
            SettingsCategory.entries.firstOrNull { it.name == categoryName } ?: history.current.settingsCategory
        history.visit(history.current.copy(tab = AppScreenTab.Settings, settingsPage = page, settingsCategory = category))
    }

    fun changeDesktopProfile(identity: Any?): Boolean = restoreDesktop(desktopHistory?.changeProfile(identity))

    private fun restoreDesktop(location: DesktopNavigationLocation?): Boolean {
        if (location == null) return false
        val old = currentRoute
        val target = location.routes
        var common = 0
        while (common < backStack.size && common < target.size && backStack[common] == target[common]) common++
        if (common < backStack.size) {
            onLocalPop?.invoke()
            backStack.subList(common, backStack.size).clear()
        }
        if (common < target.size) {
            onLocalNavigate?.invoke(old, target.last())
            backStack.addAll(target.drop(common))
        }
        return true
    }

}

internal class NuvioNavigateOptions {
    var launchSingleTop: Boolean = false
    internal var popUpToRoute: KClass<out AppRoute>? = null
    internal var popUpToInclusive: Boolean = false

    inline fun <reified T : AppRoute> popUpTo(noinline options: NuvioPopUpToOptions.() -> Unit = {}) {
        val resolved = NuvioPopUpToOptions().apply(options)
        popUpToRoute = T::class
        popUpToInclusive = resolved.inclusive
    }
}

internal class NuvioPopUpToOptions {
    var inclusive: Boolean = false
}
