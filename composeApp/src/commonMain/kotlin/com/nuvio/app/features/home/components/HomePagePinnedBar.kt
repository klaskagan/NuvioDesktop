package com.nuvio.app.features.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import com.nuvio.app.core.ui.PinnedBarBackground
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.features.details.components.keepListStillWhilePinned
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.redesignText

/** Height of the app's top bar; the pinned bar sits flush under it. */
internal val HomePageTopBarHeight = 78.dp
internal val HomePagePinnedBarHeight = 64.dp
/** #0E0D0C at 95 %: the design asks 88 % over a 20 dp blur; without the blur (a haze source per page row is too costly) 88 % let the hero show through. */
private val HomePagePinnedBarFill = Color(0xF20E0D0C)

/**
 * The one pinned bar for every page a Home row opens (Home design, "Pinned bar · spec"): Back, the
 * logo when there is one, a hairline, the name and its count, a hairline, then [menus]; [trailing]
 * (Clear, Grid / Rows) sits on the right. Hidden while the page's hero shows, it slides in under
 * the top bar once the hero scrolls away; pages with no hero pass [visible] = true from the start.
 */
@Composable
internal fun HomePagePinnedBar(
    visible: Boolean,
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
    logoUrl: String? = null,
    count: String? = null,
    menus: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(top = HomePageTopBarHeight),
        enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 3 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 3 },
    ) {
        Box(Modifier.fillMaxWidth().height(HomePagePinnedBarHeight).background(HomePagePinnedBarFill).testTag("home_page_pinned_bar")) {
            Row(
                Modifier.fillMaxWidth().height(HomePagePinnedBarHeight).padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RedesignIconButton("Back", onBack, modifier = Modifier.testTag("page_back"), vector = Icons.AutoMirrored.Rounded.ArrowBack, size = 40.dp)
                if (!logoUrl.isNullOrBlank()) {
                    NuvioAsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        modifier = Modifier.height(26.dp).widthIn(max = 120.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                PinnedBarHairline()
                Text(
                    name,
                    style = redesignText(16.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = if (horizontalPadding < 28.dp) 140.dp else 260.dp).testTag("home_page_pinned_name"),
                )
                if (count != null) {
                    Text(count, style = redesignText(13.sp, color = RedesignColors.textMuted), maxLines = 1)
                }
                if (menus != null) {
                    PinnedBarHairline()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { menus() }
                }
                Spacer(Modifier.weight(1f))
                if (trailing != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
                }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
        }
    }
}

@Composable
private fun PinnedBarHairline() {
    Box(Modifier.width(1.dp).heightIn(min = 24.dp).height(24.dp).background(RedesignColors.hairline))
}

/** The filter row's height: Details' tabs row. */
internal val HomePageFilterRowHeight = 56.dp
private val HomePageNameBandHeight = 64.dp
private const val HomePageRowMotionMillis = 360

/**
 * The page's name top-left above the hero (Home design, "Folder · grid", "See all · catalog grid"),
 * like the streaming service page's logo: its small logo when it has one, then the name, centred in
 * the 64 dp band under the top bar.
 */
@Composable
internal fun HomePageNameBand(name: String, logoUrl: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(HomePageNameBandHeight).testTag("home_page_name"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomePageLogo(logoUrl, 40.dp)
        Text(name, style = redesignText(26.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomePageLogo(logoUrl: String?, size: Dp) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    val logo = logoUrl?.takeIf { it.isNotBlank() && !failed } ?: return
    NuvioAsyncImage(
        model = logo,
        contentDescription = null,
        modifier = Modifier.height(size).widthIn(max = size * 3),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        onError = { failed = true },
    )
}

/**
 * The filter row under the hero on folders and See all, pinned like Details' tabs row and the
 * service page's All / Movies / Shows: in the page it lies on the page with a hairline under it;
 * [pinned] under the top bar it takes the shared pinned-bar surface, and Back with the page's logo
 * and name slide in from the left. [menus] stay on screen either way.
 */
@Composable
internal fun HomePageFilterRow(
    pinned: Boolean,
    name: String,
    logoUrl: String?,
    onBack: () -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    menus: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val pinnedNow by rememberUpdatedState(pinned)
    Box(
        modifier.fillMaxWidth().height(HomePageFilterRowHeight)
            .keepListStillWhilePinned { pinnedNow }
            .testTag("home_page_filter_row"),
    ) {
        PinnedBarBackground(scrolled = pinned, hazeState = null)
        val hairline by animateColorAsState(if (pinned) Color.Transparent else RedesignColors.hairline, tween(220), label = "page_row_hairline")
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = horizontalPadding).fillMaxWidth().height(1.dp).background(hairline))
        Row(Modifier.fillMaxSize().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = pinned,
                enter = fadeIn(tween(220)) + expandHorizontally(tween(HomePageRowMotionMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
                exit = fadeOut(tween(140)) + shrinkHorizontally(tween(HomePageRowMotionMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RedesignIconButton("Back", onBack, modifier = Modifier.testTag("page_back"), vector = Icons.AutoMirrored.Rounded.ArrowBack, size = 40.dp)
                    Spacer(Modifier.width(16.dp))
                    HomePageLogo(logoUrl, 26.dp)
                    if (!logoUrl.isNullOrBlank()) Spacer(Modifier.width(10.dp))
                    Text(
                        name,
                        style = redesignText(16.sp, FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = if (horizontalPadding < 28.dp) 140.dp else 260.dp).testTag("home_page_pinned_name"),
                    )
                    Box(Modifier.padding(horizontal = 18.dp).width(1.dp).height(24.dp).background(RedesignColors.hairline))
                }
            }
            // In a narrow window the pills scroll sideways inside the row, so Clear and Grid / Rows stay put.
            Box(Modifier.weight(1f)) {
                if (menus != null) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { menus() }
                }
            }
            if (trailing != null) {
                Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        }
    }
}

/** Lets a row placed inside a grid's horizontal content padding span the whole page width. */
internal fun Modifier.bleedHorizontally(padding: Dp): Modifier = layout { measurable, constraints ->
    val bleed = padding.roundToPx()
    val width = constraints.maxWidth + 2 * bleed
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

/** True once the grid's sticky filter row (key [key]) has reached the top and pins. */
@Composable
internal fun rememberFilterRowPinned(state: androidx.compose.foundation.lazy.grid.LazyGridState, key: Any): State<Boolean> =
    remember(state, key) {
        derivedStateOf {
            val row = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            row != null && state.firstVisibleItemIndex >= row.index
        }
    }
