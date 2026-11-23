package com.nuvio.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import com.nuvio.app.features.history.LocalHistorySource
import nuvio.composeapp.generated.resources.history_title
import nuvio.composeapp.generated.resources.calendar_screen_title
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.alpha
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DesktopMergedTopBar
import com.nuvio.app.features.home.rememberHomeNavigationTabs
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.compose_nav_settings
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource

/**
 * What a detail page tells the merged top bar drawn over it: whether content has scrolled under
 * the bar, and its backdrop's colour (for the Tinted background). Pages that don't report keep the
 * bar as it looks over a page's top.
 */
@androidx.compose.runtime.Stable
internal class DetailTopBarState {
    var scrolled by mutableStateOf(false)
    var tint by mutableStateOf<androidx.compose.ui.graphics.Color?>(null)
}

/** The frame's [DetailTopBarState]; null where no merged top bar is drawn (sidebar layout, mobile). */
internal val LocalDetailTopBarState = androidx.compose.runtime.compositionLocalOf<DetailTopBarState?> { null }

@Composable
internal fun DetailNavigationFrame(
    route: Any,
    selectedTab: AppScreenTab,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    /** True when the page draws the ambient backdrop, so the sidebar matches the main tabs' frosted one. */
    frostedSidebar: Boolean = false,
    /** Two frosted sidebars overlap while entries cross; the one leaving hides so their dims don't stack. */
    sidebarHandedOff: Boolean = false,
    /** False on pages that have their own All / Movies / Shows filter, so the bar doesn't show a second selection. */
    highlightHomeTab: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!isDesktop) {
        content()
        return
    }
    val layout by ThemeSettingsRepository.desktopNavigationLayout.collectAsStateWithLifecycle()
    val style by ThemeSettingsRepository.navBarStyle.collectAsStateWithLifecycle()
    val glow by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val sidebar = layout == DesktopNavigationLayout.Sidebar && screenWidth >= 768.dp
        val hoverSource = remember { MutableInteractionSource() }
        val hovered by hoverSource.collectIsHoveredAsState()
        var profileStackVisible by remember { mutableStateOf(false) }
        var topProfileOpen by remember { mutableStateOf(false) }
        val topBarState = remember(route) { DetailTopBarState() }
        val expanded = when (style) {
            NavBarStyle.EXPANDED -> true
            NavBarStyle.COMPACT -> profileStackVisible
            else -> hovered || profileStackVisible
        }
        val sidebarWidth by animateDpAsState(
            if (expanded) DesktopSidebarExpandedWidth else DesktopSidebarCollapsedWidth,
            animationSpec = tween(200),
        )
        val haze = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
            val sidebarInset = if (sidebar) {
                if (style == NavBarStyle.EXPANDED) DesktopSidebarExpandedWidth else DesktopSidebarCollapsedWidth
            } else 0.dp
            val frosted = sidebar && frostedSidebar
            // A frosted sidebar needs the page's backdrop under it, so the page insets its own content.
            Box(Modifier.fillMaxSize().hazeSource(haze).padding(start = if (frosted) 0.dp else sidebarInset)) {
                CompositionLocalProvider(
                    LocalBackdropContentStartInset provides if (frosted) sidebarInset else 0.dp,
                    LocalDetailTopBarState provides topBarState.takeIf { !sidebar },
                    content = content,
                )
            }
            if (sidebar) {
                DesktopHoverSidebar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                    sidebarExpanded = expanded,
                    sidebarWidth = sidebarWidth,
                    hoverSource = hoverSource,
                    profileStackVisible = profileStackVisible,
                    onProfileStackVisibleChange = { profileStackVisible = it },
                    frosted = frostedSidebar,
                    hazeState = haze,
                    modifier = Modifier.align(Alignment.CenterStart).alpha(if (frosted && sidebarHandedOff) 0f else 1f),
                )
            } else {
                // Always shown, like the tabs' bar: one bar that looks and behaves the same everywhere.
                Box(Modifier.align(Alignment.TopCenter)) {
                    val items = listOf(
                        FloatingNavigationItem(
                            selected = selectedTab == AppScreenTab.Search,
                            onClick = { onTabSelected(AppScreenTab.Search) },
                            drawable = Res.drawable.sidebar_search,
                            label = stringResource(Res.string.compose_nav_search),
                        ),
                        FloatingNavigationItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { onTabSelected(AppScreenTab.Library) },
                            drawable = Res.drawable.sidebar_library,
                            label = stringResource(Res.string.compose_nav_library),
                        ),
                        FloatingNavigationItem(
                            selected = selectedTab == AppScreenTab.Calendar,
                            onClick = { onTabSelected(AppScreenTab.Calendar) },
                            icon = Icons.Outlined.CalendarMonth,
                            label = stringResource(Res.string.calendar_screen_title),
                        ),
                        *if (LocalHistorySource.current != null) arrayOf(FloatingNavigationItem(
                            selected = selectedTab == AppScreenTab.History,
                            onClick = { onTabSelected(AppScreenTab.History) },
                            icon = Icons.Outlined.History,
                            label = stringResource(Res.string.history_title),
                        )) else emptyArray(),
                        FloatingNavigationItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                            label = stringResource(Res.string.compose_nav_settings),
                            content = { onClick ->
                                ProfileSwitcherTab(
                                    selected = selectedTab == AppScreenTab.Settings,
                                    onClick = onClick,
                                    onProfileSelected = onProfileSelected,
                                    onAddProfileRequested = onAddProfileRequested,
                                    hazeState = haze,
                                    onPopupStateChanged = { topProfileOpen = it },
                                    avatarSize = if (screenWidth < 800.dp) 26 else if (screenWidth > 1600.dp) 30 else 28,
                                    popupBelowAnchor = true,
                                )
                            },
                        ),
                    )
                    DesktopMergedTopBar(
                        tabs = rememberHomeNavigationTabs(
                            homeSelected = highlightHomeTab && selectedTab == AppScreenTab.Home,
                            onOpenHome = { onTabSelected(AppScreenTab.Home) },
                        ),
                        items = items,
                        windowWidth = screenWidth,
                        hazeState = haze,
                        navBarStyle = style,
                        profileSwitcherOpen = topProfileOpen,
                        scrolled = topBarState.scrolled,
                        tint = topBarState.tint,
                    )
                }
            }
        }
    }
}
