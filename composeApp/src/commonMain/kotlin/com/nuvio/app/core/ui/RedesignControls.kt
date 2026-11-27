package com.nuvio.app.core.ui

import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.OverlayEscapeHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark label on the selected pill (the light thumb). */
internal val RedesignOnLight = Color(0xFF0B0C0E)

private val PillHeight = 36.dp
private const val PillMotionMillis = 220

/**
 * Single-choice pill group for the redesign: a frosted track with a light thumb that slides to
 * the selected option. Used for filters under the merged top bar, where underlined text tabs
 * would read as a second copy of the bar.
 */
@Composable
internal fun <T> RedesignPillGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTag: ((T) -> String)? = null,
    fill: Boolean = false,
    /** A muted note after an option's label ("3/8" after "Season 2"). */
    trailing: ((T) -> String?)? = null,
    /** Right-click or press-and-hold on an option (a season's actions). */
    onOptionContextMenu: ((T) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val selectedIndex = options.indexOf(selected)
    val target = bounds[selectedIndex]
    val thumbX by animateDpAsState(target?.first ?: 0.dp, tween(PillMotionMillis, easing = FastOutSlowInEasing), label = "pill_x")
    val thumbW by animateDpAsState(target?.second ?: 0.dp, tween(PillMotionMillis, easing = FastOutSlowInEasing), label = "pill_w")
    Box(
        modifier.height(PillHeight).clip(CircleShape).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, CircleShape).padding(3.dp),
    ) {
        if (target != null) {
            Box(
                Modifier.offset(x = thumbX).width(thumbW).fillMaxHeight().clip(CircleShape)
                    .background(RedesignColors.textPrimary),
            )
        }
        Row(
            Modifier.fillMaxHeight().then(if (fill) Modifier.fillMaxWidth() else Modifier),
            horizontalArrangement = if (fill) Arrangement.SpaceAround else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val color by animateColorAsState(
                    when {
                        isSelected -> RedesignOnLight
                        hovered -> RedesignColors.textPrimary
                        else -> RedesignColors.textSecondary
                    },
                    tween(PillMotionMillis),
                    label = "pill_label",
                )
                Box(
                    Modifier.fillMaxHeight().clip(CircleShape)
                        .onGloballyPositioned { coordinates ->
                            bounds[index] = with(density) {
                                coordinates.positionInParent().x.toDp() to coordinates.size.width.toDp()
                            }
                        }
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(option) }
                        .then(onOptionContextMenu?.let { menu -> Modifier.redesignContextMenuTrigger { _ -> menu(option) } } ?: Modifier)
                        .semantics { this.selected = isSelected }
                        .then(optionTag?.let { Modifier.testTag(it(option)) } ?: Modifier)
                        .padding(horizontal = if (fill) 8.dp else 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label(option),
                            style = redesignText(13.sp, FontWeight.SemiBold, color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        trailing?.invoke(option)?.let { note ->
                            Text("  $note", style = redesignText(12.sp, FontWeight.Medium, color.copy(alpha = 0.6f)), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Round frosted icon button matching [RedesignPillGroup]'s track. */
@Composable
internal fun RedesignIconButton(
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    vector: ImageVector? = null,
    painter: Painter? = null,
    size: Dp = PillHeight,
) {
    RedesignIconButton(contentDescription, onClick, modifier, size) { tint ->
        when {
            vector != null -> Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            painter != null -> Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** [RedesignIconButton] with custom content (an animated icon); [content] gets the eased tint. */
@Composable
internal fun RedesignIconButton(
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = PillHeight,
    active: Boolean = false,
    content: @Composable (tint: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val enabled = onClick != null
    val fill by animateColorAsState(
        if ((hovered || active) && enabled) RedesignColors.fillStrong else RedesignColors.fill,
        tween(PillMotionMillis),
        label = "icon_button_fill",
    )
    val tint by animateColorAsState(
        if ((hovered || active) && enabled) RedesignColors.textPrimary else RedesignColors.textSecondary,
        tween(PillMotionMillis),
        label = "icon_button_tint",
    )
    Box(
        modifier.size(size).graphicsLayer { alpha = if (enabled) 1f else 0f }
            .clip(CircleShape).background(fill).border(1.dp, RedesignColors.hairline, CircleShape)
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            // A hidden reserved slot (no action) exposes nothing to accessibility.
            .then(if (enabled) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

/**
 * Text pill button (Today) in the same track style. [prefix] is a muted lead-in ("Genre"); an
 * [active] pill (a filter that is set) reads brighter, with a stronger edge.
 */
@Composable
internal fun RedesignPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    prefix: String? = null,
    active: Boolean = false,
    primary: Boolean = false,
    leading: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            primary -> if (hovered) Color.White else RedesignColors.textPrimary
            active -> if (hovered) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.14f)
            hovered -> RedesignColors.fillStrong
            else -> RedesignColors.fill
        },
        tween(PillMotionMillis),
        label = "pill_button_fill",
    )
    val edge by animateColorAsState(
        when {
            primary -> Color.Transparent
            active -> Color.White.copy(alpha = 0.30f)
            else -> RedesignColors.hairline
        },
        tween(PillMotionMillis),
        label = "pill_button_edge",
    )
    val ink = if (primary) RedesignOnLight else RedesignColors.textPrimary
    val iconInk = if (primary) RedesignOnLight else RedesignColors.textSecondary
    Row(
        modifier.height(PillHeight).clip(CircleShape).background(fill)
            .border(1.dp, edge, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = if (leading != null) 14.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centred when a caller stretches the pill (a popover's main button).
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) Icon(leading, contentDescription = null, tint = iconInk, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        if (prefix != null) {
            Text(prefix, style = redesignText(13.sp, FontWeight.Medium, if (active) RedesignColors.textSecondary else RedesignColors.textMuted), maxLines = 1)
            Box(Modifier.width(6.dp))
        }
        Text(label, style = redesignText(13.sp, FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, contentDescription = null, tint = iconInk, modifier = Modifier.padding(start = 4.dp).size(18.dp))
    }
}

/** The green of a switch that is on, as in the design's toggles. */
internal val RedesignSwitchOn = Color(0xFF7BE0B0)

/**
 * A filter that is on or off ("Text-free only"): a pill in the [RedesignPillButton] track style
 * with a small switch after its label, green when on.
 */
@Composable
internal fun RedesignSwitchPill(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else RedesignColors.fill, tween(PillMotionMillis), label = "switch_pill_fill")
    val track by animateColorAsState(if (checked) RedesignSwitchOn else Color.White.copy(alpha = 0.18f), tween(PillMotionMillis), label = "switch_pill_track")
    val knob by animateDpAsState(if (checked) 15.dp else 3.dp, tween(PillMotionMillis, easing = FastOutSlowInEasing), label = "switch_pill_knob")
    Row(
        modifier.height(PillHeight).clip(CircleShape).background(fill)
            .border(1.dp, RedesignColors.hairline, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = redesignText(13.sp, FontWeight.SemiBold), maxLines = 1)
        Box(Modifier.size(width = 28.dp, height = 16.dp).clip(CircleShape).background(track)) {
            Box(Modifier.padding(start = knob, top = 3.dp).size(10.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** The design's switch (`.tgl`): green with a white knob when on, a faint track when off. */
@Composable
internal fun RedesignSwitch(checked: Boolean) {
    val track by animateColorAsState(if (checked) RedesignSwitchOn else Color.White.copy(alpha = 0.18f), tween(180), label = "switch_track")
    val knob by animateDpAsState(if (checked) 17.dp else 3.dp, tween(200, easing = FastOutSlowInEasing), label = "switch_knob")
    Box(Modifier.size(width = 36.dp, height = 22.dp).clip(CircleShape).background(track)) {
        Box(Modifier.padding(start = knob, top = 3.dp).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

/** One row of a [RedesignMenu]. */
internal data class RedesignMenuOption<T>(val value: T, val label: String, val count: Int? = null)

/**
 * A pill that opens a frosted menu of [options] under it (Sort, Genre, More lists). Esc closes the
 * menu, not the page: it claims [AppKeyboardShortcuts.overlayEscape] while open. [onClear] shows a
 * clear button inside an [active] pill instead of the chevron.
 */
@Composable
internal fun <T> RedesignMenuButton(
    label: String,
    options: List<RedesignMenuOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    active: Boolean = false,
    onClear: (() -> Unit)? = null,
    alignEnd: Boolean = false,
    tag: String? = null,
) {
    val menu = remember { MutableTransitionState(false) }
    val clear = onClear?.takeIf { active }
    Box(modifier) {
        RedesignPillButton(
            label = label,
            onClick = { if (clear != null) clear() else menu.targetState = !menu.targetState },
            modifier = (tag?.let { Modifier.testTag(it) } ?: Modifier),
            trailing = if (clear != null) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowDown,
            prefix = prefix,
            active = active || menu.targetState,
        )
        if (menu.currentState || menu.targetState) {
            val dismiss = { menu.targetState = false }
            OverlayEscapeHandler { dismiss() }
            val offsetY = with(LocalDensity.current) { (PillHeight + 6.dp).roundToPx() }
            val origin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
            Popup(
                alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
                offset = IntOffset(0, offsetY),
                onDismissRequest = dismiss,
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = menu,
                    enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = origin),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = origin),
                ) {
                    RedesignMenuPanel(options, selected, onSelect = { onSelect(it); dismiss() }, tag = tag)
                }
            }
        }
    }
}

@Composable
private fun <T> RedesignMenuPanel(
    options: List<RedesignMenuOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    tag: String?,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.widthIn(min = 200.dp, max = 320.dp).heightIn(max = 380.dp)
            .clip(shape).background(RedesignMenuSurface).border(1.dp, RedesignColors.hairline, shape)
            .verticalScroll(rememberScrollState()).padding(6.dp)
            .then(tag?.let { Modifier.testTag("$it-menu") } ?: Modifier),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateColorAsState(
                when {
                    isSelected -> Color.White.copy(alpha = 0.10f)
                    hovered -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                },
                tween(140),
                label = "menu_row_fill",
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onSelect(option.value) }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.label,
                    style = redesignText(13.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium, if (isSelected || hovered) RedesignColors.textPrimary else RedesignColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Only the label shrinks, and only once the panel is at its widest; a weighted spacer
                    // beside it split the width in two and cut "Top Rated Movies" short.
                    modifier = Modifier.weight(1f, fill = false),
                )
                option.count?.let {
                    Text(it.toString(), style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textMuted), modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.weight(0.0001f, fill = true))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = RedesignColors.textPrimary,
                    modifier = Modifier.padding(start = 10.dp).size(16.dp).graphicsLayer { alpha = if (isSelected) 1f else 0f },
                )
            }
        }
    }
}

/** One row of a [RedesignOverflowButton] menu. */
internal class RedesignAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val tag: String? = null,
    val onClick: () -> Unit,
)

/**
 * Round ⋯ button that opens a frosted actions menu under it, end-aligned (a card's Play / Pause /
 * Details / Delete). Esc closes the menu first.
 */
@Composable
internal fun RedesignOverflowButton(
    contentDescription: String,
    actions: List<RedesignAction>,
    modifier: Modifier = Modifier,
    size: Dp = PillHeight,
    vector: ImageVector = Icons.Rounded.MoreHoriz,
    // Hoisted so the surface that owns the button can open the same menu on right-click / hold.
    menu: MutableTransitionState<Boolean> = remember { MutableTransitionState(false) },
) {
    Box(modifier) {
        RedesignIconButton(contentDescription, { menu.targetState = !menu.targetState }, size = size, active = menu.targetState) { tint ->
            Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(if (size < PillHeight) 18.dp else 20.dp))
        }
        val offsetY = with(LocalDensity.current) { (size + 6.dp).roundToPx() }
        RedesignActionMenuPopup(menu, actions, Alignment.TopEnd, IntOffset(0, offsetY), TransformOrigin(1f, 0f))
    }
}

/**
 * The same frosted actions menu at a pointer position inside the caller's box (a row's right-click
 * or press-and-hold). Open it with [redesignContextMenuTrigger] on that box.
 */
@Composable
internal fun RedesignContextMenu(menu: MutableTransitionState<Boolean>, actions: List<RedesignAction>, anchor: Offset) {
    RedesignActionMenuPopup(menu, actions, Alignment.TopStart, IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()), TransformOrigin(0f, 0f))
}

/** Desktop right-click and mouse press-and-hold; place after the surface's clickable. */
internal fun Modifier.redesignContextMenuTrigger(onOpen: ((Offset) -> Unit)?): Modifier =
    secondaryClickAt(onOpen).mousePressAndHoldAt(onOpen)

@Composable
private fun RedesignActionMenuPopup(
    menu: MutableTransitionState<Boolean>,
    actions: List<RedesignAction>,
    alignment: Alignment,
    offset: IntOffset,
    origin: TransformOrigin,
) {
    if (!(menu.currentState || menu.targetState) || actions.isEmpty()) return
    val dismiss = { menu.targetState = false }
    OverlayEscapeHandler { dismiss() }
    Popup(alignment = alignment, offset = offset, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = menu,
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = origin),
        ) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.widthIn(min = 200.dp, max = 300.dp).clip(shape).background(RedesignMenuSurface)
                    .border(1.dp, RedesignColors.hairline, shape).padding(6.dp),
            ) {
                actions.forEach { action ->
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    val fill by animateColorAsState(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent, tween(140), label = "action_row_fill")
                    val ink = when {
                        action.destructive -> RedesignDanger
                        hovered -> RedesignColors.textPrimary
                        else -> RedesignColors.textSecondary
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
                            .hoverable(interaction)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { dismiss(); action.onClick() }
                            .then(action.tag?.let { Modifier.testTag(it) } ?: Modifier)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                        Text(action.label, style = redesignText(13.sp, FontWeight.Medium, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Destructive action ink on the redesign's dark surfaces. */
internal val RedesignDanger = Color(0xFFFF8A80)

/** Opaque enough that menu rows stay legible over posters (no blur source under a popup). */
internal val RedesignMenuSurface = Color(0xFF1C1E22).copy(alpha = 0.97f)

/** Frosted board tile shape shared with Home's board. */
internal val RedesignTileShape = RoundedCornerShape(18.dp)
