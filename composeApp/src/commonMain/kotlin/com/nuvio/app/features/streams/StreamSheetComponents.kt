package com.nuvio.app.features.streams

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.DeviceLanguagePreferences
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.resolvePreferredAudioLanguageTargets
import nuvio.composeapp.generated.resources.streams_status_download
import nuvio.composeapp.generated.resources.streams_status_download_service
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.secondaryClickAt
import com.nuvio.app.features.debrid.DebridStreamResolution
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.streams_quality_unknown
import nuvio.composeapp.generated.resources.streams_status_checking
import nuvio.composeapp.generated.resources.streams_status_checking_service
import nuvio.composeapp.generated.resources.streams_status_direct
import nuvio.composeapp.generated.resources.streams_status_instant
import nuvio.composeapp.generated.resources.streams_status_instant_service
import nuvio.composeapp.generated.resources.streams_status_server
import nuvio.composeapp.generated.resources.streams_status_torrent
import nuvio.composeapp.generated.resources.streams_status_torrent_seeds
import org.jetbrains.compose.resources.stringResource

private val InstantGreen = Color(0xFF7BE0B0)
private val ServerBlue = Color(0xFFA9C6FF)
private val CheckingAmber = Color(0xFFF0B35E)

/** "4K" for 2160p, the resolution's own label otherwise, "Other" when the release doesn't say. */
@Composable
internal fun streamQualityLabel(resolution: DebridStreamResolution): String = when (resolution) {
    DebridStreamResolution.P2160 -> "4K"
    DebridStreamResolution.UNKNOWN -> stringResource(Res.string.streams_quality_unknown)
    else -> resolution.label
}

@Composable
internal fun StreamKicker(text: String, color: Color = RedesignColors.textMuted, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = redesignText(11.sp, FontWeight.Medium, color, letterSpacing = 0.1.em),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The white resolution badge that opens a row or a Quick pick card. */
@Composable
internal fun StreamQualityBadge(resolution: DebridStreamResolution, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(7.dp)).background(RedesignColors.textPrimary).padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(streamQualityLabel(resolution), style = redesignText(12.sp, FontWeight.ExtraBold, RedesignOnLight), maxLines = 1)
    }
}

@Composable
internal fun StreamTag(text: String) {
    Text(
        text,
        style = redesignText(11.5.sp, FontWeight.Bold, Color(0xFFE6E9EC)),
        maxLines = 1,
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** An audio language. The user's preferred one is filled, so it stands out in a long list. */
@Composable
internal fun StreamLanguageChip(code: String, preferred: Boolean) {
    Text(
        code,
        style = redesignText(11.5.sp, if (preferred) FontWeight.Bold else FontWeight.Medium, if (preferred) RedesignOnLight else RedesignColors.textSecondary),
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (preferred) RedesignColors.textPrimary else Color.Transparent)
            .border(1.dp, if (preferred) RedesignColors.textPrimary else Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .testTag("stream_language_$code"),
    )
}

/** A voice-over or subtitle detail from the addon's description: "RU Dub", "MVO", "Subs RU EN". */
@Composable
internal fun StreamAudioTag(text: String) {
    Text(
        text,
        style = redesignText(11.5.sp, FontWeight.Medium, RedesignColors.textSecondary),
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/**
 * The row's language chips. Their order follows Settings alone, so picking a language in the
 * sheet's filter fills its chip without moving any chip.
 */
@Composable
internal fun StreamLanguageChips(row: StreamRowInfo, preferredLanguages: List<String>, max: Int = 4) {
    val order = rememberStreamSettingsLanguages()
    val ordered = remember(row.languages, order) {
        row.languages.sortedBy { code -> order.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it } }
    }
    ordered.take(max).forEach { code -> StreamLanguageChip(code, preferred = code in preferredLanguages) }
}

/** The preferred and secondary audio languages from Settings → Playback, as upper-case codes. */
@Composable
internal fun rememberStreamSettingsLanguages(): List<String> {
    val player by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    return remember(player.preferredAudioLanguage, player.secondaryPreferredAudioLanguage) {
        resolvePreferredAudioLanguageTargets(
            preferredAudioLanguage = player.preferredAudioLanguage,
            secondaryPreferredAudioLanguage = player.secondaryPreferredAudioLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        ).map { it.substringBefore('-').uppercase() }.distinct()
    }
}

/**
 * The languages to favour, as upper-case codes: the sheet's remembered language filter first, then
 * the preferred and secondary audio languages from Settings → Playback.
 */
@Composable
internal fun rememberStreamPreferredLanguages(): List<String> {
    val fromSettings = rememberStreamSettingsLanguages()
    val display by remember {
        StreamBadgeSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    return remember(fromSettings, display.sheetLanguage) {
        (listOfNotNull(display.sheetLanguage) + fromSettings).distinct()
    }
}

/** How soon the source starts: Instant · RD, Direct play, P2P · 412 seeds, Checking RD… */
@Composable
internal fun StreamStatusChip(row: StreamRowInfo, modifier: Modifier = Modifier) {
    val (text, color) = when (row.availability) {
        StreamAvailability.Instant -> (row.service?.let { stringResource(Res.string.streams_status_instant_service, it) }
            ?: stringResource(Res.string.streams_status_instant)) to InstantGreen
        StreamAvailability.Server -> stringResource(Res.string.streams_status_server) to ServerBlue
        StreamAvailability.Direct -> stringResource(Res.string.streams_status_direct) to RedesignColors.textSecondary
        StreamAvailability.Checking -> (row.service?.let { stringResource(Res.string.streams_status_checking_service, it) }
            ?: stringResource(Res.string.streams_status_checking)) to CheckingAmber
        StreamAvailability.Download -> (row.service?.let { stringResource(Res.string.streams_status_download_service, it) }
            ?: stringResource(Res.string.streams_status_download)) to CheckingAmber
        StreamAvailability.Torrent -> (row.seeds?.let { stringResource(Res.string.streams_status_torrent_seeds, it) }
            ?: stringResource(Res.string.streams_status_torrent)) to RedesignColors.textSecondary
    }
    Text(
        text,
        style = redesignText(11.5.sp, FontWeight.Bold, color),
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

/**
 * One source in the sheet's list: resolution, the decoded labels and how soon it starts on the
 * first line, the addon's own release name underneath, then size, addon and languages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StreamSheetRow(
    row: StreamRowInfo,
    enabled: Boolean,
    highlight: String?,
    preferredLanguages: List<String>,
    onClick: () -> Unit,
    onSecondaryClick: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            highlight != null -> RedesignColors.fillStrong
            hovered -> RedesignColors.fill
            else -> Color.Transparent
        },
        tween(160),
        label = "stream_row_fill",
    )
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    val playFill by animateColorAsState(if (hovered && enabled) RedesignColors.textPrimary else RedesignColors.fill, tween(160), label = "stream_row_play")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill)
            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .secondaryClickAt { local -> onSecondaryClick(positionInRoot + local) }
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("stream_sheet_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(58.dp)) { StreamQualityBadge(row.resolution) }
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            // Labels, how soon it starts, the audio languages, then the dub and subtitle details.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.labels.joinToString("  ·  ").ifBlank { row.stream.streamLabel.lineSequence().firstOrNull().orEmpty() },
                    style = redesignText(13.5.sp, FontWeight.SemiBold, if (enabled) RedesignColors.textPrimary else RedesignColors.textMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StreamStatusChip(row)
                if (highlight != null) StreamTag(highlight)
                StreamLanguageChips(row, preferredLanguages)
                row.audioTags.forEach { StreamAudioTag(it) }
            }
            if (row.rawName.isNotBlank()) {
                Text(row.rawName, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(Modifier.widthIn(min = 96.dp), horizontalAlignment = Alignment.End) {
            Text(row.sizeBytes?.let(::formatStreamSize) ?: "–", style = redesignText(13.sp, FontWeight.SemiBold), maxLines = 1)
            Text(
                row.stream.addonName,
                style = redesignText(11.5.sp, color = RedesignColors.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(playFill).border(1.dp, RedesignColors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (hovered && enabled) RedesignOnLight else RedesignColors.textSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** A blank row at the real row height, shown while the first addon is still answering. */
@Composable
internal fun StreamSheetRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(58.dp)) { Box(Modifier.size(width = 44.dp, height = 22.dp).clip(RoundedCornerShape(7.dp)).background(RedesignColors.fill)) }
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.42f).height(12.dp).clip(CircleShape).background(RedesignColors.fill))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(CircleShape).background(RedesignColors.fill))
        }
        Box(Modifier.size(width = 64.dp, height = 12.dp).clip(CircleShape).background(RedesignColors.fill))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(RedesignColors.fill))
    }
}
