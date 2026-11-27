package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.LocalRedesignMenuSurface
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.redesignText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

/** Where a board popover sits relative to the action that opened it. */
enum class BoardPopoverPlacement {
    /** Under the tile, its right edge on the tile's (On TV). */
    Below,

    /** Just left of the action, centred on it (Shuffle, Save to list), as the design's frames place them. */
    Beside,

    /** Under the action, its left edge on the action's (Library in the action row). */
    Under,
}

/** The lightly dimmed board behind a popover, as in the design's frames. */
private val BoardPopoverDim = Color(0x59050607)
private val BoardPopoverGap = 10.dp
private val BoardPopoverMargin = 16.dp

/**
 * A frosted popover over the lightly dimmed board, placed next to [anchor] (the action, in window
 * pixels) by [placement]; [tile] is the tile holding the action, so popovers line up with the side
 * column rather than with one cell. A click on the dim or Esc closes it. Both fade; the panel grows
 * from the side facing the action.
 */
@Composable
internal fun BoardAnchoredPopover(
    state: MutableTransitionState<Boolean>,
    anchor: Rect,
    tile: Rect,
    placement: BoardPopoverPlacement,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!state.currentState && !state.targetState) return
    OverlayEscapeHandler { onDismiss() }
    val density = LocalDensity.current
    val gap = with(density) { BoardPopoverGap.toPx() }
    val margin = with(density) { BoardPopoverMargin.toPx() }
    Popup(
        popupPositionProvider = WindowOriginPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(state, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxSize().background(BoardPopoverDim)
                        .clickable(interactionSource = interaction, indication = null, onClick = onDismiss)
                        .testTag("detail_board_popover_dim"),
                )
            }
            val origin = when (placement) {
                BoardPopoverPlacement.Below -> TransformOrigin(0.8f, 0f)
                BoardPopoverPlacement.Under -> TransformOrigin(0.1f, 0f)
                BoardPopoverPlacement.Beside -> TransformOrigin(1f, 0.5f)
            }
            val nub = remember { PopoverNub() }
            Layout(
                content = {
                    AnimatedVisibility(
                        visibleState = state,
                        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.96f, transformOrigin = origin),
                        exit = fadeOut(tween(130)) + scaleOut(tween(150), targetScale = 0.97f, transformOrigin = origin),
                    ) { CompositionLocalProvider(LocalPopoverNub provides nub) { content() } }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val panel = measurables.firstOrNull()?.measure(loose)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (panel == null) return@layout
                    val position = boardPopoverPosition(
                        placement = placement,
                        anchor = anchor,
                        tile = tile,
                        panel = IntSize(panel.width, panel.height),
                        window = IntSize(constraints.maxWidth, constraints.maxHeight),
                        gap = gap,
                        margin = margin,
                    )
                    nub.set(placement, anchor, position, IntSize(panel.width, panel.height), inset = 24.dp.toPx())
                    panel.place(position)
                }
            }
        }
    }
}

/** Where the panel goes: under the tile, right-aligned to it, or left of it centred on the action; always inside the window. */
internal fun boardPopoverPosition(
    placement: BoardPopoverPlacement,
    anchor: Rect,
    tile: Rect,
    panel: IntSize,
    window: IntSize,
    gap: Float,
    margin: Float,
): IntOffset {
    val maxX = (window.width - panel.width - margin).coerceAtLeast(margin)
    val maxY = (window.height - panel.height - margin).coerceAtLeast(margin)
    return when (placement) {
        BoardPopoverPlacement.Below -> {
            val x = (tile.right - panel.width).coerceIn(margin, maxX)
            val below = anchor.bottom + gap
            // No room under the action: open above it instead.
            val y = if (below + panel.height + margin <= window.height) below else (anchor.top - gap - panel.height)
            IntOffset(x.roundToInt(), y.coerceIn(margin, maxY).roundToInt())
        }
        BoardPopoverPlacement.Under -> {
            val x = anchor.left.coerceIn(margin, maxX)
            val y = (anchor.bottom + gap).coerceIn(margin, maxY)
            IntOffset(x.roundToInt(), y.roundToInt())
        }
        BoardPopoverPlacement.Beside -> {
            // Right edge just left of the action itself, so Lists (third column) opens beside Lists, over
            // the tile's other cells, as in the frame; anchoring to the column's edge left it far away.
            val x = (anchor.left - gap - panel.width).coerceIn(margin, maxX)
            val y = (anchor.center.y - panel.height / 2f).coerceIn(margin, maxY)
            IntOffset(x.roundToInt(), y.roundToInt())
        }
    }
}

/** Which edge of the panel the arrow sits on, and how far along it (px), pointing at the action. */
internal enum class PopoverNubEdge { Top, Bottom, Right }

@Stable
internal class PopoverNub {
    var edge by mutableStateOf<PopoverNubEdge?>(null)
        private set
    var along by mutableFloatStateOf(0f)
        private set

    fun set(placement: BoardPopoverPlacement, anchor: Rect, position: IntOffset, panel: IntSize, inset: Float) {
        val (newEdge, newAlong) = popoverNubFor(placement, anchor, position, panel, inset)
        edge = newEdge
        along = newAlong
    }
}

/** The arrow's edge and offset: on the edge facing the action, centred on it, kept clear of the corners. */
internal fun popoverNubFor(
    placement: BoardPopoverPlacement,
    anchor: Rect,
    position: IntOffset,
    panel: IntSize,
    inset: Float,
): Pair<PopoverNubEdge, Float> = when (placement) {
    BoardPopoverPlacement.Beside ->
        PopoverNubEdge.Right to (anchor.center.y - position.y).coerceIn(inset, (panel.height - inset).coerceAtLeast(inset))
    else -> {
        val edge = if (position.y >= anchor.bottom) PopoverNubEdge.Top else PopoverNubEdge.Bottom
        edge to (anchor.center.x - position.x).coerceIn(inset, (panel.width - inset).coerceAtLeast(inset))
    }
}

internal val LocalPopoverNub = staticCompositionLocalOf<PopoverNub?> { null }

private val PopoverNubSize = 16.dp
private val PopoverEdge = Color.White.copy(alpha = 0.16f)

/**
 * The design's arrow (`.pop:before`): a 16 dp square turned 45° on the panel's edge, in the panel's
 * colour with the panel's edge on its two outer sides, drawn over the panel's own edge.
 */
private fun DrawScope.drawPopoverNub(edge: PopoverNubEdge, along: Float, fill: Color) {
    val half = PopoverNubSize.toPx() * 0.7071f
    val (center, a, tip, b) = when (edge) {
        PopoverNubEdge.Top -> Offset(along, 0f).let { c -> listOf(c, Offset(c.x - half, c.y), Offset(c.x, c.y - half), Offset(c.x + half, c.y)) }
        PopoverNubEdge.Bottom -> Offset(along, size.height).let { c -> listOf(c, Offset(c.x - half, c.y), Offset(c.x, c.y + half), Offset(c.x + half, c.y)) }
        PopoverNubEdge.Right -> Offset(size.width, along).let { c -> listOf(c, Offset(c.x, c.y - half), Offset(c.x + half, c.y), Offset(c.x, c.y + half)) }
    }
    val inner = when (edge) {
        PopoverNubEdge.Top -> Offset(center.x, center.y + half)
        PopoverNubEdge.Bottom -> Offset(center.x, center.y - half)
        PopoverNubEdge.Right -> Offset(center.x - half, center.y)
    }
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(tip.x, tip.y); lineTo(b.x, b.y); lineTo(inner.x, inner.y); close() }, fill)
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(tip.x, tip.y); lineTo(b.x, b.y) }, PopoverEdge, style = Stroke(width = 1.dp.toPx()))
}

/**
 * The design's arrow on any frosted panel (Quick pick): put it first in the panel's modifier, before
 * its clip. [fill] is the panel's own colour, opaque.
 */
internal fun Modifier.popoverNub(edge: () -> PopoverNubEdge?, along: () -> Float, fill: Color): Modifier =
    drawWithContent {
        drawContent()
        edge()?.let { drawPopoverNub(it, along(), fill) }
    }

/** Puts a popup at the window's origin, so its content can fill the window (the dim) and place itself. */
private object WindowOriginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * The popover's frosted panel: a title with a muted line under it and a close button, then
 * [content]. The same surface as the board's other popovers (Quick pick, Write a review).
 */
@Composable
internal fun BoardPopoverPanel(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    width: Dp,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val nub = LocalPopoverNub.current
    // The backdrop's colour, like every board menu ("Dropdown · spec" in the Home design).
    val surface = LocalRedesignMenuSurface.current
    Column(
        // The arrow is drawn outside the caller's modifier: animateContentSize clips to the panel.
        Modifier
            .drawWithContent {
                drawContent()
                val edge = nub?.edge
                if (edge != null) drawPopoverNub(edge, nub.along, surface)
            }
            .then(modifier)
            .width(width)
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape).background(surface).border(1.dp, PopoverEdge, shape)
            .padding(16.dp).testTag(tag),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(start = 2.dp, top = 2.dp)) {
                Text(title, style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = redesignText(12.5.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RedesignIconButton("close", onClose, vector = Icons.Rounded.Close, size = 32.dp, modifier = Modifier.testTag("${tag}_close"))
        }
        content()
    }
}
