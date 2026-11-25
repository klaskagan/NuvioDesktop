package com.nuvio.app.features.details.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.DetailTvHandoff
import com.nuvio.app.features.handoff.TvHandoffPlaybackView
import com.nuvio.app.features.handoff.observeTvHandoffPlayback
import com.nuvio.app.features.handoff.selectTvHandoffDevice
import com.nuvio.app.features.handoff.stopObservingTvHandoffPlayback
import com.nuvio.app.features.handoff.tvHandoffPlaybackView
import com.nuvio.app.features.player.PlayerControlTvDevice
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_tv_account_failed
import nuvio.composeapp.generated.resources.detail_board_tv_android
import nuvio.composeapp.generated.resources.detail_board_tv_last_used
import nuvio.composeapp.generated.resources.detail_board_tv_manage
import nuvio.composeapp.generated.resources.detail_board_tv_none
import nuvio.composeapp.generated.resources.detail_board_tv_own_source
import nuvio.composeapp.generated.resources.detail_board_tv_picker_title
import nuvio.composeapp.generated.resources.detail_board_tv_preparing
import nuvio.composeapp.generated.resources.detail_board_tv_send
import nuvio.composeapp.generated.resources.detail_board_tv_webos
import org.jetbrains.compose.resources.stringResource

/**
 * Continue on TV, opened from On TV: what would play ([line], "S2 E3 · Who Is Alive? · from 18:22"),
 * the paired TVs as radio choices with their kind and which was used last, then Send to the chosen
 * TV and Manage TVs (Settings → Paired TVs). Closing it cancels a transfer that hasn't started.
 */
@Composable
internal fun DetailTvPicker(handoff: DetailTvHandoff, line: String?, dismiss: () -> Unit) {
    var view by remember { mutableStateOf(tvHandoffPlaybackView()) }
    DisposableEffect(handoff) {
        val owner = Any()
        observeTvHandoffPlayback(owner) { view = tvHandoffPlaybackView() }
        view = tvHandoffPlaybackView()
        onDispose {
            stopObservingTvHandoffPlayback(owner)
            handoff.close()
        }
    }
    var chosen by remember { mutableIntStateOf(-1) }
    val selected = tvPickerSelection(chosen, view)
    BoardPopoverPanel(
        title = stringResource(Res.string.detail_board_tv_picker_title),
        subtitle = line,
        onClose = dismiss,
        width = 420.dp,
        tag = "detail_tv_picker",
        modifier = Modifier.animateContentSize(tween(220)),
    ) {
        if (!handoff.accountFailed) {
            view.devices.forEachIndexed { index, tv ->
                TvChoiceRow(
                    tv = tv,
                    lastUsed = index == view.lastUsed,
                    selected = index == selected,
                    tag = "detail_tv_choice_$index",
                    onClick = { chosen = index },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        val note = when {
            handoff.accountFailed -> stringResource(Res.string.detail_board_tv_account_failed)
            view.devices.isEmpty() -> stringResource(Res.string.detail_board_tv_none)
            view.ready && view.status.isNotBlank() -> view.status
            else -> stringResource(Res.string.detail_board_tv_own_source)
        }
        Text(
            note,
            style = redesignText(12.8.sp, color = RedesignColors.textMuted, lineHeight = 17.sp),
            modifier = Modifier.padding(horizontal = 2.dp).padding(top = 2.dp).testTag("detail_tv_note"),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val target = view.devices.getOrNull(selected)
            if (target != null && !handoff.accountFailed) {
                val canSend = view.ready && !view.busy
                RedesignPillButton(
                    label = if (view.ready) stringResource(Res.string.detail_board_tv_send, target.name) else stringResource(Res.string.detail_board_tv_preparing),
                    onClick = { if (canSend) selectTvHandoffDevice(selected) },
                    primary = true,
                    leading = Icons.Rounded.Tv,
                    modifier = Modifier.weight(1f).alpha(if (canSend) 1f else 0.6f).testTag("detail_tv_send"),
                )
            }
            RedesignPillButton(
                label = stringResource(Res.string.detail_board_tv_manage),
                onClick = {
                    dismiss()
                    handoff.onManageDevices()
                },
                leading = Icons.Rounded.Settings,
                trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                modifier = Modifier.testTag("detail_tv_manage"),
            )
        }
    }
}

/** The TV the picker has chosen: the one picked, else the one used last, else the first. */
internal fun tvPickerSelection(chosen: Int, view: TvHandoffPlaybackView): Int = when {
    view.devices.isEmpty() -> -1
    chosen in view.devices.indices -> chosen
    view.lastUsed in view.devices.indices -> view.lastUsed
    else -> 0
}

@Composable
private fun TvChoiceRow(tv: PlayerControlTvDevice, lastUsed: Boolean, selected: Boolean, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.07f)
            hovered -> Color.White.copy(alpha = 0.04f)
            else -> Color.Transparent
        },
        tween(160),
        label = "tv_choice_fill",
    )
    val edge by animateColorAsState(if (selected) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(160), label = "tv_choice_edge")
    val shape = RoundedCornerShape(14.dp)
    val kind = stringResource(if (tv.platform == "lg") Res.string.detail_board_tv_webos else Res.string.detail_board_tv_android)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(fill).border(1.dp, edge, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = RedesignColors.textPrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tv.name, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (lastUsed) stringResource(Res.string.detail_board_tv_last_used, kind) else kind,
                style = redesignText(12.sp, color = RedesignColors.textMuted),
                maxLines = 1,
            )
        }
        RadioDot(selected)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val ring by animateDpAsState(if (selected) 5.dp else 2.dp, tween(160), label = "tv_radio_ring")
    val ink by animateColorAsState(if (selected) RedesignColors.textPrimary else Color.White.copy(alpha = 0.4f), tween(160), label = "tv_radio_ink")
    Box(Modifier.size(18.dp).clip(CircleShape).border(ring, ink, CircleShape))
}
