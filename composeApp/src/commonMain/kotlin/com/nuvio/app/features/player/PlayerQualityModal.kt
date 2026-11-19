package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_quality_playing
import nuvio.composeapp.generated.resources.player_quality_title
import org.jetbrains.compose.resources.stringResource

internal fun PlayerScreenRuntime.openQualityModal() {
    openPlayerQualityMenu()
    showQualityModal = true
}

/** Quality label used by the desktop control: 2160 is shown as 4K. */
internal fun playerQualityLabel(height: Int): String = if (height == 2160) "4K" else "${height}p"

/**
 * Compose counterpart of the desktop Quality rail (controls.js). It lists cached alternative
 * resolutions from Sources; selecting one runs the shared [selectPlayerQuality] transaction.
 */
@Composable
internal fun PlayerQualityModal(
    visible: Boolean,
    choices: List<PlayerQualityChoice>,
    playingHeight: Int,
    loadingHeight: Int?,
    status: String,
    onSelect: (Int) -> Unit,
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
                text = stringResource(Res.string.player_quality_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (status.isNotBlank()) {
                Text(
                    text = status,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(choices, key = { it.height }) { choice ->
                    QualityRow(
                        choice = choice,
                        playing = choice.height == playingHeight,
                        loading = choice.height == loadingHeight,
                        onClick = { onSelect(choice.height) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityRow(
    choice: PlayerQualityChoice,
    playing: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val enabled = choice.disabledReason == null
    val primaryColor = when {
        playing -> tokens.colors.onAccent
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.45f)
    }
    val secondaryColor = if (playing) tokens.colors.onAccent.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (playing) tokens.colors.accent else Color.Transparent)
            .clickable(enabled = enabled && !playing, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = playerQualityLabel(choice.height),
                color = primaryColor,
                style = MaterialTheme.typography.titleMedium,
            )
            val detail = when {
                // The desktop controls fill {quality} in JavaScript; the string has no format argument.
                playing -> stringResource(Res.string.player_quality_playing)
                    .replace("{quality}", playerQualityLabel(choice.height))
                else -> choice.disabledReason
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = secondaryColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
