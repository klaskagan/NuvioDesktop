package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignDanger
import com.nuvio.app.core.ui.RedesignMenuSurface
import com.nuvio.app.core.ui.redesignText
import kotlin.math.roundToInt

/** One action row of a [BoardContextMenu]. */
internal class BoardMenuAction(
    val label: String,
    val icon: ImageVector,
    val tag: String,
    /** Red ink (Remove from Continue Watching). */
    val destructive: Boolean = false,
    /** A hairline above this row. */
    val separatorBefore: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The board's frosted context menu for an episode card or a season pill (right-click or
 * press-and-hold), opened beside [anchor] (root bounds; centred near the top when unknown). Its last
 * row can be the Rate strip for that episode. Esc and a click outside close it.
 */
@Composable
internal fun BoardContextMenu(
    title: String,
    anchor: Rect?,
    actions: List<BoardMenuAction>,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    val dismiss = { state.targetState = false }
    LaunchedEffect(state.currentState, state.targetState) {
        if (!state.currentState && !state.targetState && state.isIdle) onDismiss()
    }
    OverlayEscapeHandler { dismiss() }
    val provider = remember(anchor) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val margin = 16
                val rect = anchor ?: return IntOffset((windowSize.width - popupContentSize.width) / 2, windowSize.height / 4)
                val x = rect.left.roundToInt().coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val below = rect.bottom.roundToInt() + 8
                val y = if (below + popupContentSize.height <= windowSize.height - margin) below else (rect.top.roundToInt() - 8 - popupContentSize.height).coerceAtLeast(margin)
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = TransformOrigin(0f, 0f)),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = TransformOrigin(0f, 0f)),
        ) {
            BoardMenuPanel(title, actions, onPick = { dismiss(); it.onClick() }, footer = footer)
        }
    }
}

/**
 * The frosted panel of [BoardContextMenu]: the small-caps title, the rows, then an optional footer.
 * Home's card menus reuse it inside their own dimmed layer.
 */
@Composable
internal fun BoardMenuPanel(
    title: String,
    actions: List<BoardMenuAction>,
    onPick: (BoardMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.widthIn(min = 260.dp, max = 520.dp).clip(shape).background(RedesignMenuSurface)
            .border(1.dp, RedesignColors.hairline, shape).padding(6.dp).testTag("detail_board_context_menu"),
    ) {
        Text(
            title.uppercase(),
            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        )
        actions.forEach { action ->
            if (action.separatorBefore) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
            }
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateColorAsState(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent, tween(140), label = "context_row")
            val ink = when {
                action.destructive -> RedesignDanger
                hovered -> RedesignColors.textPrimary
                else -> RedesignColors.textSecondary
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onPick(action) }
                    .testTag(action.tag)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                Text(action.label, style = redesignText(13.sp, FontWeight.Medium, ink))
            }
        }
        if (footer != null) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
            Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { footer() }
        }
    }
}
