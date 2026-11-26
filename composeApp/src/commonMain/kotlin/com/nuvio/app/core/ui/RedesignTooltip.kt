package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

private const val TooltipDelayMillis = 350L
private const val TooltipFadeMillis = 140

/**
 * The redesign's tooltip: a small white pill above its anchor. Call it inside the anchor's
 * layout; it shows [label] after a short delay while [visible] (usually the anchor's hover).
 */
@Composable
internal fun RedesignTooltip(label: String, visible: Boolean) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(TooltipDelayMillis)
            state.targetState = true
        } else {
            state.targetState = false
        }
    }
    if (!state.currentState && !state.targetState) return
    Popup(popupPositionProvider = AboveAnchorPositionProvider, properties = PopupProperties(focusable = false)) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(TooltipFadeMillis)) + slideInVertically(tween(TooltipFadeMillis)) { it / 4 },
            exit = fadeOut(tween(TooltipFadeMillis)) + slideOutVertically(tween(TooltipFadeMillis)) { it / 4 },
        ) {
            Box(
                Modifier.padding(bottom = 6.dp)
                    .background(RedesignColors.textPrimary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(label, style = redesignText(12.sp, FontWeight.SemiBold, RedesignOnLight), maxLines = 1)
            }
        }
    }
}

private object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val above = anchorBounds.top - popupContentSize.height
        // Flip under the anchor when there is no room above (a poster at the top of the window).
        val y = if (above >= 0) above else anchorBounds.bottom
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y)
    }
}
