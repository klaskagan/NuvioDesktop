package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The redesign's text tabs: one underline slides between the labels and colours ease on hover
 * and selection. Search's scope tabs and the Person timeline's department tabs share it.
 */
@Composable
internal fun <T : Any> RedesignTabs(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    count: (T) -> Int? = { null },
    selectedColor: Color = RedesignColors.textPrimary,
    hoverColor: Color = RedesignColors.textSecondary,
    mutedColor: Color = RedesignColors.textMuted,
) {
    val density = LocalDensity.current
    // Each tab reports where it sits so one underline can slide between them.
    val bounds = remember { mutableStateMapOf<T, Pair<Dp, Dp>>() }
    val target = bounds[selected]
    val indicatorX by animateDpAsState(target?.first ?: 0.dp, tween(260, easing = FastOutSlowInEasing), label = "tab_x")
    val indicatorW by animateDpAsState(target?.second ?: 0.dp, tween(260, easing = FastOutSlowInEasing), label = "tab_w")
    Box(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val isSelected = item == selected
                val labelColor by animateColorAsState(
                    when {
                        isSelected -> selectedColor
                        hovered -> hoverColor
                        else -> mutedColor
                    },
                    tween(160),
                    label = "tab_label",
                )
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            with(density) {
                                bounds[item] = coords.positionInParent().x.toDp() to coords.size.width.toDp()
                            }
                        }
                        .hoverable(interaction)
                        .semantics { this.selected = isSelected }
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(item) }
                        .padding(top = 4.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(label(item), style = redesignText(14.sp, FontWeight.SemiBold, labelColor))
                    count(item)?.let {
                        Spacer(Modifier.width(5.dp))
                        Text(it.toString(), style = redesignText(14.sp, FontWeight.Medium, labelColor.copy(alpha = 0.6f)))
                    }
                }
            }
        }
        if (target != null) {
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = indicatorX).width(indicatorW).height(2.dp)
                    .clip(RoundedCornerShape(2.dp)).background(selectedColor),
            )
        }
    }
}
