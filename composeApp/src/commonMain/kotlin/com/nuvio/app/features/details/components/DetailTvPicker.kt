package com.nuvio.app.features.details.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignMenuSurface
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.DetailTvHandoff
import com.nuvio.app.features.handoff.observeTvHandoffPlayback
import com.nuvio.app.features.handoff.selectTvHandoffDevice
import com.nuvio.app.features.handoff.stopObservingTvHandoffPlayback
import com.nuvio.app.features.handoff.tvHandoffPlaybackView
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_board_tv_account_failed
import nuvio.composeapp.generated.resources.detail_board_tv_manage
import nuvio.composeapp.generated.resources.detail_board_tv_none
import nuvio.composeapp.generated.resources.detail_board_tv_picker_title
import nuvio.composeapp.generated.resources.detail_board_tv_preparing
import nuvio.composeapp.generated.resources.detail_board_tv_send
import org.jetbrains.compose.resources.stringResource

/**
 * On TV's device picker, under its Your-way cell: paired TVs as "Send to …" rows, the transfer
 * status, and Manage TVs (Settings → Advanced → Paired TVs). Leaving it cancels a pending transfer.
 */
@Composable
internal fun DetailTvPicker(handoff: DetailTvHandoff, dismiss: () -> Unit) {
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(280.dp).clip(shape).background(RedesignMenuSurface).border(1.dp, RedesignColors.hairline, shape)
            .animateContentSize(tween(220)).padding(6.dp).testTag("detail_tv_picker"),
    ) {
        Text(
            stringResource(Res.string.detail_board_tv_picker_title).uppercase(),
            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        )
        when {
            handoff.accountFailed -> PickerNote(stringResource(Res.string.detail_board_tv_account_failed))
            view.devices.isEmpty() -> PickerNote(stringResource(Res.string.detail_board_tv_none))
            else -> view.devices.forEachIndexed { index, tv ->
                PickerRow(
                    label = stringResource(Res.string.detail_board_tv_send, tv.name),
                    icon = Icons.Rounded.Tv,
                    enabled = view.ready && !view.busy,
                    tag = "detail_tv_send_$index",
                    onClick = { selectTvHandoffDevice(index) },
                )
            }
        }
        val status = when {
            handoff.accountFailed || view.devices.isEmpty() -> null
            !view.ready -> stringResource(Res.string.detail_board_tv_preparing)
            else -> view.status.takeIf { it.isNotBlank() }
        }
        if (status != null) PickerNote(status)
        Spacer(Modifier.height(4.dp))
        PickerRow(
            label = stringResource(Res.string.detail_board_tv_manage),
            icon = Icons.Rounded.Settings,
            enabled = true,
            tag = "detail_tv_manage",
            onClick = {
                dismiss()
                handoff.onManageDevices()
            },
        )
    }
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text,
        style = redesignText(12.5.sp, color = RedesignColors.textSecondary, lineHeight = 17.sp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PickerRow(label: String, icon: ImageVector, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered && enabled) Color.White.copy(alpha = 0.08f) else Color.Transparent, tween(140), label = "tv_row_fill")
    val ink = when {
        !enabled -> RedesignColors.textMuted
        hovered -> RedesignColors.textPrimary
        else -> RedesignColors.textSecondary
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(label, style = redesignText(13.sp, FontWeight.Medium, ink))
    }
}
