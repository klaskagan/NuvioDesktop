package com.nuvio.app.features.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.PosterZoomAnchor
import com.nuvio.app.core.ui.drawLiftedPoster
import com.nuvio.app.features.details.components.BoardMenuAction
import com.nuvio.app.features.details.components.BoardMenuPanel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Black 38 %: the page dims a little behind a Home card menu. */
private const val HomeCardMenuDimAlpha = 0.38f
private val HomeCardMenuGap = 8.dp
private val HomeCardMenuMargin = 16.dp

/**
 * Home's card menu (Continue Watching card, Resume tile, Up next row): the page dims, the card stays
 * where it is, lifted above the dim with a white ring, and the Details board's frosted menu opens
 * beside it, under the card when there's room and above it otherwise. Esc or a click on the dim
 * closes it; picking a row closes it and then runs the row.
 */
@Composable
internal fun HomeCardContextMenu(
    title: String,
    anchor: PosterZoomAnchor?,
    actions: List<BoardMenuAction>,
    onDismiss: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // A menu shows the moment it was opened; repository updates must not relabel its rows mid-fade.
    val frozenActions = remember { actions }
    val source = anchor?.source
    DisposableEffect(source) {
        val lifted = source?.lift() == true
        onDispose { if (lifted) source?.land() }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    val close: (after: (() -> Unit)?) -> Unit = { after ->
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(140))
                currentOnDismiss()
                after?.invoke()
            }
        }
    }
    OverlayEscapeHandler { close(null) }
    val fullWindow = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
        }
    }
    val density = LocalDensity.current
    val bounds = anchor?.boundsInRoot
    val ringRadius = with(density) { (anchor?.cornerRadius ?: 12.dp).toPx() }
    // usePlatformInsets = false: by default the popup lays its content out under the title-bar inset
    // (28 dp on macOS), so the lifted card and its ring, drawn at root coordinates, sat 28 dp low.
    Popup(
        popupPositionProvider = fullWindow,
        onDismissRequest = { close(null) },
        properties = PopupProperties(focusable = true, usePlatformInsets = false),
    ) {
        Box(Modifier.fillMaxSize().testTag("home_card_menu")) {
            Canvas(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close(null) },
            ) {
                drawRect(Color.Black.copy(alpha = HomeCardMenuDimAlpha * progress.value))
                if (bounds != null) {
                    // The card itself, drawn above the dim from its own layer, then the ring around it.
                    if (source != null && source.isLifted) {
                        translate(bounds.left, bounds.top) {
                            inset(0f, 0f, size.width - bounds.width, size.height - bounds.height) {
                                drawLiftedPoster(source)
                            }
                        }
                    }
                    val ring = 2.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f * progress.value),
                        topLeft = Offset(bounds.left - ring / 2f, bounds.top - ring / 2f),
                        size = Size(bounds.width + ring, bounds.height + ring),
                        cornerRadius = CornerRadius(ringRadius + ring / 2f),
                        style = Stroke(ring),
                    )
                }
            }
            HomeCardMenuPlacement(bounds) {
                BoardMenuPanel(
                    title = title,
                    actions = frozenActions,
                    onPick = { action -> close(action.onClick) },
                    modifier = Modifier.graphicsLayer {
                        alpha = progress.value
                        val s = 0.96f + 0.04f * progress.value
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    },
                )
            }
        }
    }
}

/** Places the menu at the card's left edge, under it when it fits and above it otherwise. */
@Composable
private fun HomeCardMenuPlacement(anchor: Rect?, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val margin = with(density) { HomeCardMenuMargin.roundToPx() }
        val gap = with(density) { HomeCardMenuGap.roundToPx() }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val position = homeCardMenuPosition(
                anchor = anchor,
                window = IntSize(constraints.maxWidth, constraints.maxHeight),
                menu = IntSize(placeable.width, placeable.height),
                margin = margin,
                gap = gap,
            )
            placeable.place(position)
        }
    }
}

/** Under the card when the menu fits there, above it otherwise; kept inside the window margins. */
internal fun homeCardMenuPosition(anchor: Rect?, window: IntSize, menu: IntSize, margin: Int, gap: Int): IntOffset {
    if (anchor == null) return IntOffset((window.width - menu.width) / 2, window.height / 4)
    val maxX = (window.width - menu.width - margin).coerceAtLeast(margin)
    val x = anchor.left.roundToInt().coerceIn(margin, maxX)
    val below = anchor.bottom.roundToInt() + gap
    val y = if (below + menu.height <= window.height - margin) below
    else (anchor.top.roundToInt() - gap - menu.height).coerceAtLeast(margin)
    return IntOffset(x, y)
}
