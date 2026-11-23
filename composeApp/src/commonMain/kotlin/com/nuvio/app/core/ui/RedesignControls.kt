package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
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
                        .semantics { this.selected = isSelected }
                        .then(optionTag?.let { Modifier.testTag(it(option)) } ?: Modifier)
                        .padding(horizontal = if (fill) 8.dp else 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = redesignText(13.sp, FontWeight.SemiBold, color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val enabled = onClick != null
    val fill by animateColorAsState(
        if (hovered && enabled) RedesignColors.fillStrong else RedesignColors.fill,
        tween(PillMotionMillis),
        label = "icon_button_fill",
    )
    val tint by animateColorAsState(
        if (hovered && enabled) RedesignColors.textPrimary else RedesignColors.textSecondary,
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
        when {
            vector != null -> Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            painter != null -> Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Text pill button (Today) in the same track style. */
@Composable
internal fun RedesignPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else RedesignColors.fill, tween(PillMotionMillis), label = "pill_button_fill")
    Row(
        modifier.height(PillHeight).clip(CircleShape).background(fill)
            .border(1.dp, RedesignColors.hairline, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = redesignText(13.sp, FontWeight.SemiBold), maxLines = 1)
        if (trailing != null) Icon(trailing, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
    }
}

/** Frosted board tile shape shared with Home's board. */
internal val RedesignTileShape = RoundedCornerShape(18.dp)
