package com.nuvio.app.features.streams

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.debrid.DebridSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.streams_quick_all
import nuvio.composeapp.generated.resources.streams_quick_all_count
import nuvio.composeapp.generated.resources.streams_quick_found
import nuvio.composeapp.generated.resources.streams_quick_none
import nuvio.composeapp.generated.resources.streams_quick_title
import nuvio.composeapp.generated.resources.streams_sheet_best_match
import nuvio.composeapp.generated.resources.streams_sheet_faster_start
import nuvio.composeapp.generated.resources.streams_sheet_on_server
import nuvio.composeapp.generated.resources.streams_sheet_searching
import org.jetbrains.compose.resources.stringResource

/** What Quick pick is choosing a source for. */
internal data class StreamQuickPickRequest(
    val type: String,
    val videoId: String,
    val parentMetaId: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /** "S2 E3 · Who Is Alive?" or the film's name. */
    val label: String,
    val resumeLine: String?,
)

internal val StreamQuickPickWidth = 760.dp
private val QuickPickCardHeight = 156.dp

/**
 * Quick pick: up to three ways to play, chosen from every source found (Best match, a faster
 * start, the user's own server), with the full Sources sheet one click away. It loads the same
 * request the sheet does, so opening the sheet afterwards starts with these results.
 */
@Composable
internal fun StreamQuickPickPanel(
    request: StreamQuickPickRequest,
    onPick: (StreamItem) -> Unit,
    onAllSources: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by StreamsRepository.uiState.collectAsStateWithLifecycle()
    val debridSettings by remember {
        DebridSettingsRepository.ensureLoaded()
        DebridSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        StreamsRepository.load(
            type = request.type,
            videoId = request.videoId,
            parentMetaId = request.parentMetaId,
            season = request.seasonNumber,
            episode = request.episodeNumber,
            manualSelection = true,
        )
    }
    val token = StreamsRepository.requestToken(request.type, request.videoId, request.seasonNumber, request.episodeNumber, manualSelection = true)
    // Results of an earlier title must never be offered for this one.
    val current = uiState.takeIf { it.requestToken == token } ?: StreamsUiState()
    val debridEnabled = debridSettings.canResolvePlayableLinks
    val rows = remember(current.groups) { current.groups.flatMap { it.streams }.map(::streamRowInfo).let(::mergeDuplicateStreams) }
    val preferredLanguages = rememberStreamPreferredLanguages()
    val picks = remember(rows, debridEnabled, preferredLanguages) {
        streamQuickPicks(rows, preferredLanguages) { it.isSelectableForPlayback(debridEnabled) }
    }
    val loading = current.requestToken == null || current.isAnyLoading || current.groups.any { it.isLoading }
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier.width(StreamQuickPickWidth).clip(shape).background(Color(0xF216171B)).border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(18.dp).testTag("stream_quick_pick"),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.streams_quick_title, request.label),
                    style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        request.resumeLine,
                        when {
                            rows.isNotEmpty() -> stringResource(Res.string.streams_quick_found, rows.size)
                            loading -> stringResource(Res.string.streams_sheet_searching)
                            else -> stringResource(Res.string.streams_quick_none)
                        },
                    ).joinToString(" · "),
                    style = redesignText(12.5.sp, color = RedesignColors.textMuted),
                    maxLines = 1,
                )
            }
            RedesignIconButton(
                contentDescription = stringResource(Res.string.action_close),
                onClick = onDismiss,
                vector = Icons.Rounded.Close,
                size = 32.dp,
                modifier = Modifier.testTag("stream_quick_pick_close"),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(QuickPickCardHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val cards = listOf(
                Res.string.streams_sheet_best_match to picks.best,
                Res.string.streams_sheet_faster_start to picks.faster,
                Res.string.streams_sheet_on_server to picks.server,
            ).filter { it.second != null }
            // The cards there are share the whole width: a slot kept for a missing third choice left
            // a third of the panel blank.
            cards.forEachIndexed { index, (label, row) ->
                val pick = row ?: return@forEachIndexed
                QuickPickCard(stringResource(label), pick, preferredLanguages, primary = index == 0, onClick = { onPick(pick.stream) }, modifier = Modifier.weight(1f))
            }
            if (cards.isEmpty() && loading) {
                repeat(2) { Box(Modifier.weight(1f).height(QuickPickCardHeight).clip(RoundedCornerShape(16.dp)).background(RedesignColors.fill)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            RedesignPillButton(
                label = if (rows.isNotEmpty()) stringResource(Res.string.streams_quick_all_count, rows.size) else stringResource(Res.string.streams_quick_all),
                onClick = onAllSources,
                trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                modifier = Modifier.testTag("stream_quick_pick_all"),
            )
        }
    }
}

@Composable
private fun QuickPickCard(label: String, row: StreamRowInfo, preferredLanguages: List<String>, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(
        if (hovered || primary) RedesignColors.hairlineFocused else RedesignColors.hairline,
        tween(180),
        label = "quick_pick_border",
    )
    val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else if (primary) Color.White.copy(alpha = 0.09f) else RedesignColors.fill, tween(180), label = "quick_pick_fill")
    Column(
        modifier.height(QuickPickCardHeight).clip(shape).background(fill).border(1.dp, border, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(14.dp)
            .testTag("stream_quick_pick_card"),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StreamKicker(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StreamQualityBadge(row.resolution)
            row.labels.take(2).forEach { StreamTag(it) }
        }
        if (row.languages.isNotEmpty() || row.audioTags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StreamLanguageChips(row, preferredLanguages, max = 3)
                row.audioTags.take(2).forEach { StreamAudioTag(it) }
            }
        }
        Text(row.rawName, style = redesignText(11.5.sp, color = RedesignColors.textMuted, lineHeight = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamStatusChip(row)
            Text(
                listOfNotNull(row.sizeBytes?.let(::formatStreamSize), row.stream.addonName).joinToString(" · "),
                style = redesignText(12.sp, color = RedesignColors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(32.dp).clip(CircleShape).background(RedesignColors.textPrimary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = RedesignOnLight, modifier = Modifier.size(18.dp))
            }
        }
    }
}
