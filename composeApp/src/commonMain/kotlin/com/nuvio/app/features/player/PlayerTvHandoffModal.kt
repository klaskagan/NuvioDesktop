package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.handoff.TvHandoffPlaybackView
import com.nuvio.app.features.handoff.observeTvHandoffPlayback
import com.nuvio.app.features.handoff.stopObservingTvHandoffPlayback
import com.nuvio.app.features.handoff.tvHandoffPlaybackView
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_continue_on_tv
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the Compose counterpart of the desktop TV rail (controls.js `tvHandoff`): the shared
 * handoff picker is prepared after account verification and reports changes through its observer.
 */
internal fun PlayerScreenRuntime.openTvHandoffModal() {
    observeTvHandoffPlayback(this) { tvHandoffView = tvHandoffPlaybackView() }
    tvHandoffView = tvHandoffPlaybackView()
    showTvHandoffModal = true
    continueOnTv()
}

/** Dismissal cancels a pending transfer, like Escape or an outside click on desktop. */
internal fun PlayerScreenRuntime.closeTvHandoffModal(cancel: Boolean) {
    showTvHandoffModal = false
    stopObservingTvHandoffPlayback(this)
    if (cancel && !tvHandoffTransferred) {
        cancelTvHandoff?.invoke()
        cancelTvHandoff = null
    }
}

@Composable
internal fun PlayerTvHandoffModal(
    visible: Boolean,
    view: TvHandoffPlaybackView,
    onSelect: (Int) -> Unit,
    onResumeHere: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().widthIn(max = 444.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = stringResource(Res.string.player_continue_on_tv),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (view.devices.isEmpty()) {
                    item {
                        Text(
                            text = "Add a TV in Settings → Advanced → Paired TVs.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                itemsIndexed(view.devices) { index, device ->
                    HandoffRow(
                        title = device.name,
                        detail = if (device.platform == "sony") "Android TV" else "LG webOS",
                        enabled = view.ready && !view.busy,
                        onClick = { onSelect(index) },
                    )
                }
                if (view.canResume) {
                    item { HandoffRow(title = "Resume here", detail = null, enabled = true, onClick = onResumeHere) }
                }
            }
            if (view.busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (view.status.isNotBlank()) {
                Text(
                    text = view.status,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HandoffRow(title: String, detail: String?, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.titleMedium,
        )
        if (detail != null) {
            Text(text = detail, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
