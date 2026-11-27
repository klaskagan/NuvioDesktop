package com.nuvio.app.features.home.components

import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import com.nuvio.app.core.ui.GrabbingPointerIcon

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignAction
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignMenuSurface
import com.nuvio.app.core.ui.RedesignOverflowButton
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRowStyle
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.home_row_drag
import nuvio.composeapp.generated.resources.home_row_hidden
import nuvio.composeapp.generated.resources.home_row_hide
import nuvio.composeapp.generated.resources.home_row_hint_cancel
import nuvio.composeapp.generated.resources.home_row_hint_done
import nuvio.composeapp.generated.resources.home_row_hint_move
import nuvio.composeapp.generated.resources.home_row_more
import nuvio.composeapp.generated.resources.home_row_move_down
import nuvio.composeapp.generated.resources.home_row_move_up
import nuvio.composeapp.generated.resources.home_row_reorder
import nuvio.composeapp.generated.resources.home_row_style
import nuvio.composeapp.generated.resources.home_row_undo
import org.jetbrains.compose.resources.stringResource

/**
 * What a Home row puts in its shelf header (Home design "Row header · ⋯" and "Row · reorder"): the
 * ⋯ row menu, or, while the row is picked up, a grip, ↑ ↓ and Done, with the header as drag handle.
 */
@Immutable
internal class HomeRowHeader(
    val modifier: Modifier = Modifier,
    val leading: (@Composable () -> Unit)? = null,
    val actions: (@Composable RowScope.() -> Unit)? = null,
    val showViewAll: Boolean = true,
    /** Over the row's posters while it is being moved: a drag surface, so nothing under it opens. */
    val rowOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
)

internal val LocalHomeRowHeader = compositionLocalOf<HomeRowHeader?> { null }

/**
 * One row picked up and moved among the rows the current Home tab shows. Moves only change what is
 * drawn; [finish] saves the order once (or drops it), so a cancelled move never touches settings.
 */
@Stable
internal class HomeRowReorderState {
    var movingKey by mutableStateOf<String?>(null)
        private set
    private var working by mutableStateOf<List<String>>(emptyList())
    private var original: List<String> = emptyList()

    // The saved order stays drawn until the settings carry it, so rows don't flash back for a frame.
    private var settling by mutableStateOf<List<String>?>(null)

    /** Bumped by each ↑ / ↓ step; Home keeps the row centred on it. */
    var keyboardSteps by mutableIntStateOf(0)
        private set

    /** The row the last ↑ / ↓ step passed, and which way (-1 up, 1 down). */
    var lastPassedKey: String? = null
        private set
    var lastDirection: Int = 0
        private set

    val active: Boolean get() = movingKey != null

    /** The order Home draws the reorderable rows in; empty when it should use the settings order. */
    val order: List<String> get() = if (active) working else settling.orEmpty()

    fun start(key: String, visibleKeys: List<String>) {
        if (key !in visibleKeys || visibleKeys.size < 2) return
        original = visibleKeys
        working = visibleKeys
        settling = null
        movingKey = key
    }

    fun canStep(direction: Int): Boolean {
        val index = working.indexOf(movingKey ?: return false)
        return index >= 0 && (index + direction) in working.indices
    }

    fun step(direction: Int): Boolean {
        if (!canStep(direction)) return false
        val index = working.indexOf(movingKey)
        lastPassedKey = working[index + direction]
        lastDirection = direction
        working = working.toMutableList().apply { add(index + direction, removeAt(index)) }
        keyboardSteps++
        return true
    }

    /** A drag over [targetKey]: the moving row takes that row's place. */
    fun moveOnto(targetKey: String): Boolean {
        val key = movingKey ?: return false
        val from = working.indexOf(key)
        val to = working.indexOf(targetKey)
        if (from < 0 || to < 0 || from == to) return false
        working = working.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    fun finish(save: Boolean) {
        if (!active) return
        val result = working
        movingKey = null
        working = emptyList()
        if (save && result != original) {
            settling = result
            HomeCatalogSettingsRepository.applyVisibleOrder(result)
        }
        original = emptyList()
    }

    /** Home calls this once the settings have changed after a save. */
    fun settled() {
        if (!active) settling = null
    }

    /** [items] with the reorderable rows, wherever they sit, drawn in [order]; others keep their slots. */
    fun <T> arrange(items: List<T>, keyOf: (T) -> String): List<T> {
        val order = order
        if (order.isEmpty()) return items
        val inOrder = order.toHashSet()
        val slots = items.filter { keyOf(it) in inOrder }
        val byKey = slots.associateBy(keyOf)
        if (byKey.size != slots.size) return items
        val queue = ArrayDeque(order.filter { it in byKey })
        if (queue.size != slots.size) return items
        return items.map { if (keyOf(it) in inOrder) byKey.getValue(queue.removeFirst()) else it }
    }
}

/** Hide from Home with a 5 s Undo (no confirmation dialog, Home design "Row header · ⋯"). */
@Stable
internal class HomeRowHideState {
    class Entry(val key: String, val title: String, val id: Long)

    var entry by mutableStateOf<Entry?>(null)
        private set
    private var nextId = 0L

    fun hide(key: String, title: String) {
        HomeCatalogSettingsRepository.setEnabled(key, false)
        entry = Entry(key, title, ++nextId)
    }

    fun undo() {
        val hidden = entry ?: return
        entry = null
        HomeCatalogSettingsRepository.setEnabled(hidden.key, true)
    }

    fun dismiss(id: Long) {
        if (entry?.id == id) entry = null
    }
}

private val RowButtonSize = 32.dp


/** The header a row shows: ⋯ normally, ↑ ↓ Done with a grip while it is the row being moved. */
@Composable
internal fun homeRowHeader(
    key: String,
    title: String,
    reorder: HomeRowReorderState,
    hide: HomeRowHideState,
    canReorder: Boolean,
    rowStyle: HomeRowStyle?,
    onReorder: () -> Unit,
    dragHandle: Modifier,
): HomeRowHeader {
    if (reorder.movingKey == key) {
        val moveUp = stringResource(Res.string.home_row_move_up)
        val moveDown = stringResource(Res.string.home_row_move_down)
        val done = stringResource(Res.string.action_done)
        val drag = stringResource(Res.string.home_row_drag)
        // The whole row is the handle: a hand over it, a grabbing hand while the button is down.
        var grabbing by remember(key) { mutableStateOf(false) }
        val grab = Modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        grabbing = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerHoverIcon(if (grabbing) GrabbingPointerIcon else PointerIcon.Hand)
            .then(dragHandle)
        return HomeRowHeader(
            modifier = grab.semantics { contentDescription = drag },
            leading = {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = null,
                    tint = RedesignColors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp).size(20.dp),
                )
            },
            actions = {
                RedesignIconButton(
                    moveUp,
                    onClick = { reorder.step(-1) },
                    modifier = Modifier.testTag("home_row_move_up").graphicsLayer { alpha = if (reorder.canStep(-1)) 1f else 0.35f },
                    vector = Icons.Rounded.KeyboardArrowUp,
                    size = RowButtonSize,
                )
                RedesignIconButton(
                    moveDown,
                    onClick = { reorder.step(1) },
                    modifier = Modifier.testTag("home_row_move_down").graphicsLayer { alpha = if (reorder.canStep(1)) 1f else 0.35f },
                    vector = Icons.Rounded.KeyboardArrowDown,
                    size = RowButtonSize,
                )
                RedesignPillButton(
                    label = done,
                    onClick = { reorder.finish(save = true) },
                    modifier = Modifier.padding(start = 4.dp).testTag("home_row_reorder_done"),
                    primary = true,
                )
            },
            showViewAll = false,
            rowOverlay = { Box(Modifier.matchParentSize().then(grab)) },
        )
    }
    val reorderLabel = stringResource(Res.string.home_row_reorder)
    val styleLabel = stringResource(Res.string.home_row_style)
    val hideLabel = stringResource(Res.string.home_row_hide)
    val moreLabel = stringResource(Res.string.home_row_more, title)
    val currentStyle = rowStyle?.let { homeRowStyleLabel(it) }
    return HomeRowHeader(
        actions = {
            var pickerOpen by remember(key) { mutableStateOf(false) }
            var anchor by remember(key) { mutableStateOf<Rect?>(null) }
            val actions = buildList {
                if (canReorder) add(RedesignAction(reorderLabel, Icons.Rounded.SwapVert, tag = "home_row_reorder", onClick = onReorder))
                if (currentStyle != null) {
                    add(RedesignAction(styleLabel, Icons.AutoMirrored.Rounded.ViewList, tag = "home_row_style", trailing = currentStyle) { pickerOpen = true })
                }
                add(
                    RedesignAction(hideLabel, Icons.Rounded.VisibilityOff, tag = "home_row_hide", separatorBefore = isNotEmpty()) {
                        hide.hide(key, title)
                    },
                )
            }
            Box(Modifier.onGloballyPositioned { anchor = it.boundsInRoot() }) {
                RedesignOverflowButton(
                    contentDescription = moreLabel,
                    actions = actions,
                    modifier = Modifier.testTag("home_row_more"),
                    size = RowButtonSize,
                    title = title,
                )
                // Inside the button's box: as a sibling in the actions row the popup took a slot, and the
                // row's 8 dp spacing pushed ⋯ left while the picker was open.
                if (pickerOpen) {
                    HomeRowStylePicker(key, onDismiss = { pickerOpen = false }, anchor = anchor, title = title)
                }
            }
        },
    )
}

/**
 * The picked-up row: an 18 dp panel with a white ring and a deep shadow behind it, scaled a touch,
 * eased in and out. [sidePadding] is the row's own inset; the panel reaches 16 dp past it.
 */
@Composable
internal fun HomeRowLift(
    lifted: Boolean,
    sidePadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(if (lifted) 1f else 0f, tween(220, easing = FastOutSlowInEasing), label = "home_row_lift")
    val topInset by animateDpAsState(if (lifted) 14.dp else 0.dp, tween(220, easing = FastOutSlowInEasing), label = "home_row_lift_top")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.graphicsLayer {
            val scale = 1f + 0.01f * progress
            scaleX = scale
            scaleY = scale
        },
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = (sidePadding - 16.dp).coerceAtLeast(0.dp))
                    .graphicsLayer { alpha = progress }
                    .shadow(26.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(LiftSurface, shape)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), shape),
            )
        }
        // The row scrolls sideways under the panel: clip it inside the ring, or posters past the
        // panel's edge drew over it.
        Box(
            Modifier
                .then(
                    if (progress > 0f) {
                        Modifier.drawWithContent {
                            val inset = ((sidePadding - 16.dp).coerceAtLeast(0.dp) + 2.dp).toPx()
                            val radius = 16.dp.toPx()
                            val path = Path().apply {
                                addRoundRect(RoundRect(inset, 0f, size.width - inset, size.height, CornerRadius(radius)))
                            }
                            clipPath(path) { this@drawWithContent.drawContent() }
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(top = topInset, bottom = topInset * 0.3f),
        ) { content() }
    }
}

private val LiftSurface = Color(0xFF1E1F23).copy(alpha = 0.92f)

/**
 * The pill at Home's foot: the reorder hint while a row is picked up, otherwise the
 * "<row> hidden · Undo" toast for 5 s after Hide from Home.
 */
@Composable
internal fun HomeRowBottomPill(
    reorder: HomeRowReorderState,
    hide: HomeRowHideState,
    modifier: Modifier = Modifier,
) {
    val hidden = hide.entry
    LaunchedEffect(hidden?.id) {
        val shown = hidden ?: return@LaunchedEffect
        delay(5_000)
        hide.dismiss(shown.id)
    }
    // Keep the last toast's text while it slides away.
    var lastHidden by remember { mutableStateOf<HomeRowHideState.Entry?>(null) }
    if (hidden != null) lastHidden = hidden
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = reorder.active,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        ) {
            BottomPill(Modifier.testTag("home_row_reorder_hint")) {
                Keycap("↑")
                Keycap("↓")
                PillText(stringResource(Res.string.home_row_hint_move))
                PillDivider()
                Keycap("Enter")
                PillText(stringResource(Res.string.home_row_hint_done))
                PillDivider()
                Keycap("Esc")
                PillText(stringResource(Res.string.home_row_hint_cancel))
            }
        }
        AnimatedVisibility(
            visible = hidden != null && !reorder.active,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.96f),
            exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.97f),
        ) {
            val shown = lastHidden ?: return@AnimatedVisibility
            BottomPill(Modifier.testTag("home_row_hidden_toast")) {
                Text(
                    stringResource(Res.string.home_row_hidden, shown.title),
                    style = redesignText(13.sp, FontWeight.Medium, RedesignColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                PillDivider()
                Text(
                    stringResource(Res.string.home_row_undo),
                    style = redesignText(13.sp, FontWeight.SemiBold, RedesignColors.textPrimary),
                    modifier = Modifier
                        .clickable(role = Role.Button) { hide.undo() }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("home_row_undo"),
                )
            }
        }
    }
}

@Composable
private fun BottomPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .shadow(18.dp, CircleShape)
            .background(RedesignMenuSurface, CircleShape)
            .border(1.dp, RedesignColors.hairline, CircleShape)
            .height(44.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Keycap(label: String) {
    Box(
        Modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 24.dp)
            .background(RedesignColors.fillStrong, RoundedCornerShape(6.dp))
            .border(1.dp, RedesignColors.hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = redesignText(12.sp, FontWeight.Bold, RedesignColors.textPrimary))
    }
}

@Composable
private fun PillText(text: String) {
    Text(text, style = redesignText(13.sp, FontWeight.Medium, RedesignColors.textSecondary), maxLines = 1)
}

@Composable
private fun PillDivider() {
    Box(Modifier.width(1.dp).height(18.dp).background(RedesignColors.hairline))
}
