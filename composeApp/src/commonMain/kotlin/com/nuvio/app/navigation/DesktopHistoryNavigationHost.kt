package com.nuvio.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop

internal class HistoryGestureBlockers {
    var count by mutableIntStateOf(0)
    var generation by mutableIntStateOf(0)
}

private val LocalHistoryGestureBlockers = staticCompositionLocalOf<HistoryGestureBlockers?> { null }

/** A modal owns input until dismissal, and cancels any gesture already in progress. */
@Composable
internal fun BlockDesktopHistoryGestures() {
    val blockers = LocalHistoryGestureBlockers.current
    val active = com.nuvio.app.core.ui.LocalScreenActive.current
    DisposableEffect(blockers, active) {
        val owner = blockers.takeIf { active }
        owner?.let { it.count++; it.generation++ }
        onDispose { owner?.let { it.count--; it.generation++ } }
    }
}

@Composable
internal fun DesktopHistoryNavigationHost(
    enabled: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    gestureResetKey: Any? = null,
    content: @Composable () -> Unit,
) {
    if (!isDesktop) {
        content()
        return
    }
    val exclusions = remember { HistoryGestureExclusions() }
    val blockers = remember { HistoryGestureBlockers() }
    val inputEnabled = enabled && blockers.count == 0 && LocalWindowInfo.current.isWindowFocused
    var preview by remember { mutableStateOf<HistorySwipePreview?>(null) }
    var lastPreview by remember { mutableStateOf(HistorySwipePreview(HistorySwipe.Back, 0f)) }
    val availableBack = canGoBack
    val availableForward = canGoForward
    Box(Modifier.fillMaxSize().clipToBounds().desktopHistoryGestures(
        enabled = inputEnabled,
        gestureResetKey = gestureResetKey to blockers.generation,
        exclusions = exclusions,
        onBack = { if (availableBack) onBack() },
        onForward = { if (availableForward) onForward() },
        onPreview = {
            preview = it
            if (it != null) lastPreview = it
        },
    )) {
        CompositionLocalProvider(LocalHistoryGestureExclusions provides exclusions, LocalHistoryGestureBlockers provides blockers) { content() }
        val direction = preview?.direction ?: lastPreview.direction
        val back = direction == HistorySwipe.Back
        val available = if (back) canGoBack else canGoForward
        val progress by animateFloatAsState(
            targetValue = preview?.progress ?: 0f,
            animationSpec = tween(90),
            label = "history_swipe_progress",
        )
        if (available) {
            AnimatedVisibility(
                visible = inputEnabled && preview != null,
                modifier = Modifier.align(if (back) Alignment.CenterStart else Alignment.CenterEnd),
                enter = fadeIn(tween(90)),
                exit = fadeOut(tween(200)),
            ) {
                // Half of the circular surface sits outside the window, like browser history arrows.
                Box(
                    Modifier.size(104.dp)
                        .offset(x = ((52f - progress * 20f) * if (back) -1f else 1f).dp)
                        .background(Color.Black.copy(alpha = 0.88f), CircleShape)
                        .testTag(if (back) "history_back_overlay" else "history_forward_overlay"),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.offset(x = if (back) 18.dp else (-18).dp).size(44.dp)) {
                        val color = Color.White.copy(alpha = if (progress >= 0.95f) 1f else 0.45f)
                        val tip = if (back) size.width * 0.18f else size.width * 0.82f
                        val tail = if (back) size.width * 0.82f else size.width * 0.18f
                        val neck = size.width * 0.50f
                        drawLine(color, Offset(tip, center.y), Offset(tail, center.y), 5.dp.toPx(), StrokeCap.Round)
                        drawPath(
                            Path().apply {
                                moveTo(neck, size.height * 0.18f)
                                lineTo(tip, center.y)
                                lineTo(neck, size.height * 0.82f)
                            },
                            color,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
        }
    }
}
