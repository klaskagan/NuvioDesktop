package com.nuvio.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.desktopNavigationContentPadding
import com.nuvio.app.core.ui.DesktopNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.compose_nav_settings
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DetailNavigationFrame(
    route: Any,
    selectedTab: AppScreenTab,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    content: @Composable () -> Unit,
) {
    val enabled by ThemeSettingsRepository.detailNavigationEnabled.collectAsStateWithLifecycle()
    if (!isDesktop || !enabled) {
        content()
        return
    }
    val hideDelaySeconds by ThemeSettingsRepository.detailNavigationHideDelaySeconds.collectAsStateWithLifecycle()
    val autoHide by ThemeSettingsRepository.detailNavigationAutoHide.collectAsStateWithLifecycle()
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
        var motion by remember(route) { mutableStateOf(0L) }
        var visible by remember(route) { mutableStateOf(true) }
        LaunchedEffect(route, motion, topProfileOpen, sidebar, autoHide, hideDelaySeconds) {
            visible = true
            if (autoHide && !sidebar && !topProfileOpen) {
                delay(hideDelaySeconds * 1_000L)
                visible = false
            }
        }
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
        Box(Modifier.fillMaxSize().pointerInput(route) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) motion++
                }
            }
        }) {
            Box(Modifier.fillMaxSize().hazeSource(haze).padding(start = if (sidebar) {
                if (style == NavBarStyle.EXPANDED) DesktopSidebarExpandedWidth else DesktopSidebarCollapsedWidth
            } else 0.dp)) { content() }
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
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else {
                AnimatedVisibility(
                    visible = visible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(280, easing = FastOutSlowInEasing)) { -it / 3 },
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 3 },
                ) {
                    val items = listOf(
                        FloatingNavigationItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { onTabSelected(AppScreenTab.Home) },
                            icon = Icons.Filled.Home,
                            label = stringResource(Res.string.compose_nav_home),
                        ),
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
                    DesktopNavigationBar(
                        items = items,
                        contentPadding = desktopNavigationContentPadding(),
                        hazeState = haze,
                        navBarStyle = style,
                        profileSwitcherOpen = topProfileOpen,
                        windowWidth = screenWidth,
                        glowEnabled = glow,
                        isDetailScreen = true,
                    )
                }
            }
        }
    }
}
