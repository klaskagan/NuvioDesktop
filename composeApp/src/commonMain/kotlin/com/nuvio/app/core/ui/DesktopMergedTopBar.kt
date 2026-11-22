package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.settings.NavBarStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.painterResource

/**
 * The Home tabs the top bar leads with (For you, Movies, Shows, My lists). [selectedIndex] is null
 * while another screen is showing; picking a tab from there opens Home on it.
 */
internal class DesktopNavigationTabs(
    val labels: List<String>,
    val selectedIndex: Int?,
    val onSelect: (Int) -> Unit,
)

/** True while the desktop top bar hosts Home's tabs, so Home doesn't draw a second strip of them. */
val LocalHomeTabsHostedInNavigation = staticCompositionLocalOf { false }

/** The bar's height below the status bar. Screens start their content under it. */
internal val DesktopMergedTopBarHeight = 56.dp

private val BarRowHeight = 44.dp
private val BarTopGap = 8.dp
private val IndicatorHeight = 2.dp

/** Narrower than this, destinations stay icons so the tabs and the icons never collide. */
private val MergedTopBarLabelsMinWidth = 900.dp

/**
 * The desktop top-bar layout's one bar: Home's tabs on the left and the other destinations on the
 * right, sharing a single sliding underline. It replaces the floating pill plus a separate tab
 * strip on Home, and it is the same bar on every screen, so a Home tab is one click away from
 * Search or a detail page. It is clear over the top of a page and turns solid once content scrolls
 * beneath it; over detail pages it is frosted.
 *
 * [items] are the destinations other than Home. Their labels follow the Navigation Bar Style
 * setting through [desktopNavigationLabelFraction], as the pill's did.
 */
@Composable
internal fun DesktopMergedTopBar(
    tabs: DesktopNavigationTabs,
    items: List<FloatingNavigationItem>,
    windowWidth: Dp,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    navBarStyle: NavBarStyle = NavBarStyle.ADAPTIVE,
    isHeroEnabled: Boolean = false,
    profileSwitcherOpen: Boolean = false,
    isDetailScreen: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val homeSelected = tabs.selectedIndex != null
    val settingsSelected = items.lastOrNull()?.selected == true
    val scrolled = (scrollState?.totalScrollOffset ?: 0f) > if (settingsSelected) 70f else 35f
    val showLabels = windowWidth >= MergedTopBarLabelsMinWidth && desktopNavigationLabelFraction(
        style = navBarStyle,
        isHomeSelected = homeSelected,
        isHeroEnabled = isHeroEnabled,
        isHovered = hovered,
        profileSwitcherOpen = profileSwitcherOpen,
        isScrolledAwayFromTop = scrolled,
        isDetailScreen = isDetailScreen,
    ) > 0.5f
    val surface by animateColorAsState(
        targetValue = when {
            isDetailScreen -> BackdropSurface.stickyHeader
            scrolled -> BackdropSurface.stickyHeaderOpaque
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "merged_top_bar_surface",
    )
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gutter = desktopPageHorizontalPaddingForWidth(windowWidth.value)
    val density = LocalDensity.current
    // Every entry reports where it sits so one underline can slide between tabs and destinations.
    val bounds = remember { mutableStateMapOf<String, Pair<Dp, Dp>>() }
    val selectedKey = tabs.selectedIndex?.let { "tab:$it" }
        ?: items.firstOrNull { it.selected }?.let { "nav:${it.label}" }
    val target = selectedKey?.let(bounds::get)
    val indicatorX by animateDpAsState(target?.first ?: 0.dp, tween(280, easing = FastOutSlowInEasing), label = "merged_top_bar_x")
    val indicatorW by animateDpAsState(target?.second ?: 0.dp, tween(280, easing = FastOutSlowInEasing), label = "merged_top_bar_w")
    val indicatorAlpha by animateFloatAsState(if (target != null) 1f else 0f, tween(160), label = "merged_top_bar_indicator")

    // Entries sit in two groups, so each is measured against the row that holds the underline.
    var row by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun Modifier.entry(key: String): Modifier = onGloballyPositioned { coords ->
        val host = row?.takeIf { it.isAttached } ?: return@onGloballyPositioned
        val x = host.localPositionOf(coords, Offset.Zero).x
        with(density) { bounds[key] = x.toDp() to coords.size.width.toDp() }
    }

    RedesignTypography {
        Box(
            modifier
                .fillMaxWidth()
                .nuvioTrackTopBarFocus()
                .hoverable(interaction)
                .then(
                    if (isDetailScreen && hazeState != null) Modifier.hazeEffect(state = hazeState) { blurRadius = 24.dp } else Modifier,
                )
                .background(surface)
                .testTag("desktop_merged_top_bar"),
        ) {
            if (surface.alpha > 0.5f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
            }
            Box(
                Modifier
                    .padding(top = statusBarTop + BarTopGap)
                    .padding(horizontal = gutter)
                    .fillMaxWidth()
                    .height(DesktopMergedTopBarHeight - BarTopGap)
                    .onGloballyPositioned { row = it },
            ) {
                Row(Modifier.fillMaxWidth().height(BarRowHeight), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        tabs.labels.forEachIndexed { index, label ->
                            TopBarEntry(
                                label = label,
                                selected = tabs.selectedIndex == index,
                                showLabel = true,
                                onClick = { tabs.onSelect(index) },
                                modifier = Modifier.entry("tab:$index"),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        items.forEach { item ->
                            TopBarEntry(
                                label = item.label,
                                selected = item.selected,
                                showLabel = showLabels,
                                onClick = item.onClick,
                                modifier = Modifier.entry("nav:${item.label}"),
                                leading = { tint ->
                                    val custom = item.content
                                    val vector = item.icon
                                    val drawable = item.drawable
                                    when {
                                        custom != null -> custom(item.onClick)
                                        vector != null -> Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                                        drawable != null -> Icon(painterResource(drawable), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = indicatorX, y = BarRowHeight)
                        .width(indicatorW)
                        .height(IndicatorHeight)
                        .graphicsLayer { alpha = indicatorAlpha }
                        .clip(RoundedCornerShape(2.dp))
                        .background(RedesignColors.textPrimary),
                )
            }
        }
    }
}

/** One tab or destination: an optional icon and a label that eases between muted, hovered and selected. */
@Composable
private fun TopBarEntry(
    label: String,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (Color) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(
        targetValue = when {
            selected -> RedesignColors.textPrimary
            hovered -> RedesignColors.textSecondary
            else -> RedesignColors.textMuted
        },
        animationSpec = tween(160),
        label = "merged_top_bar_entry",
    )
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .nuvioFocusBorder(shape)
            .clip(shape)
            .hoverable(interaction)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading(color)
        if (leading == null) {
            Text(label, style = redesignText(14.sp, FontWeight.SemiBold, color), maxLines = 1)
        } else {
            AnimatedVisibility(
                visible = showLabel,
                enter = fadeIn(tween(200)) + expandHorizontally(tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Text(
                    text = label,
                    style = redesignText(14.sp, FontWeight.SemiBold, color),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
