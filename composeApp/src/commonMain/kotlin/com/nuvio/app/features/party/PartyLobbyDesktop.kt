package com.nuvio.app.features.party

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import com.nuvio.app.core.ui.DesktopTopBarContentGap
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.LocalDetailTopBarState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignSwitch
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.player.formatPlaybackTime
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.action_remove
import nuvio.composeapp.generated.resources.party_behind
import nuvio.composeapp.generated.resources.party_code_label
import nuvio.composeapp.generated.resources.party_copy_invite
import nuvio.composeapp.generated.resources.party_end
import nuvio.composeapp.generated.resources.party_end_message
import nuvio.composeapp.generated.resources.party_end_title
import nuvio.composeapp.generated.resources.party_host
import nuvio.composeapp.generated.resources.party_host_only_description
import nuvio.composeapp.generated.resources.party_host_only_note
import nuvio.composeapp.generated.resources.party_host_only_title
import nuvio.composeapp.generated.resources.party_invite_copied
import nuvio.composeapp.generated.resources.party_invite_message
import nuvio.composeapp.generated.resources.party_leave
import nuvio.composeapp.generated.resources.party_lobby_eyebrow
import nuvio.composeapp.generated.resources.party_lobby_joins_host
import nuvio.composeapp.generated.resources.party_lobby_members
import nuvio.composeapp.generated.resources.party_lobby_starts_beginning
import nuvio.composeapp.generated.resources.party_lobby_starts_from
import nuvio.composeapp.generated.resources.party_lobby_waiting
import nuvio.composeapp.generated.resources.party_reconnecting
import nuvio.composeapp.generated.resources.party_remove_message
import nuvio.composeapp.generated.resources.party_remove_title
import nuvio.composeapp.generated.resources.party_share_description
import nuvio.composeapp.generated.resources.party_share_title
import nuvio.composeapp.generated.resources.party_tab_chat
import nuvio.composeapp.generated.resources.party_tab_party
import nuvio.composeapp.generated.resources.party_watch_with_party
import nuvio.composeapp.generated.resources.party_you
import org.jetbrains.compose.resources.stringResource

private val LobbyCardShape = RoundedCornerShape(18.dp)
private val LobbyCardFill = Color.White.copy(alpha = 0.06f)

private fun Modifier.lobbyCard(): Modifier =
    clip(LobbyCardShape).background(LobbyCardFill).border(1.dp, RedesignColors.hairline, LobbyCardShape)

/**
 * The party on desktop, as in the Details design's Party lobby: the episode, the code with Copy
 * invite, who is in, the host's settings, then Watch with the party and End for everyone, as
 * frosted cards in one 560 column under a round back button. Chat stays one pill away.
 */
@Composable
internal fun PartyLobbyDesktop(
    onBack: () -> Unit,
    onWatch: (PartyMedia) -> Unit,
) {
    val state by PartyRepository.state.collectAsStateWithLifecycle()
    val unread by PartyChat.unread.collectAsStateWithLifecycle()
    val party = state.party ?: return
    PartyLobbyDesktopContent(party, unread, onBack, onWatch)
}

/** [PartyLobbyDesktop] for a given [party], so it can be shown without a live one. */
@Composable
internal fun PartyLobbyDesktopContent(
    party: ActiveParty,
    unread: Int,
    onBack: () -> Unit,
    onWatch: (PartyMedia) -> Unit,
) {
    var showChat by rememberSaveable { mutableStateOf(unread > 0) }
    // The media's artwork as the ambient backdrop, as on Details and the Sources sheet.
    val backdropMode = LocalShellBackdropMode.current
    val artwork = party.media.background?.takeIf { it.isNotBlank() } ?: party.media.poster
    val backdrop = remember(backdropMode == ShellBackdropMode.Solid) {
        if (backdropMode != ShellBackdropMode.Solid) BackdropFocusController(artwork) else null
    }
    LaunchedEffect(backdrop, artwork) { backdrop?.base = artwork }
    val topBar = LocalDetailTopBarState.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        if (topBar != null) DesktopMergedTopBarHeight + DesktopTopBarContentGap else 24.dp
    Box(Modifier.fillMaxSize().testTag("party_lobby")) {
        if (backdrop != null) ShellBackdrop(backdrop, showArtwork = backdropMode == ShellBackdropMode.Ambient)
        Box(Modifier.fillMaxSize().padding(start = LocalBackdropContentStartInset.current)) {
        RedesignIconButton(
            contentDescription = stringResource(Res.string.action_back),
            onClick = onBack,
            vector = Icons.AutoMirrored.Rounded.ArrowBack,
            size = 44.dp,
            modifier = Modifier.padding(start = 30.dp, top = top).testTag("party_lobby_back"),
        )
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = top + 8.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxSize(),
        ) {
            RedesignPillGroup(
                options = listOf(false, true),
                selected = showChat,
                label = { stringResource(if (it) Res.string.party_tab_chat else Res.string.party_tab_party) },
                trailing = { if (it && unread > 0) unread.toString() else null },
                onSelect = { showChat = it },
                optionTag = { if (it) "party_lobby_chat" else "party_lobby_party" },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = showChat,
                transitionSpec = { fadeIn(tween(200, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                modifier = Modifier.fillMaxSize(),
                label = "party_lobby_tab",
            ) { chat ->
                if (chat) {
                    PartyChatContent(members = party.members, modifier = Modifier.fillMaxSize().padding(bottom = 24.dp))
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) { LobbyContent(party, onWatch, onBack) }
                }
            }
        }
        }
    }
}

@Composable
private fun ColumnScope.LobbyContent(party: ActiveParty, onWatch: (PartyMedia) -> Unit, onBack: () -> Unit) {
    // Ending or leaving goes back to where the party was opened from (Details), not to the Join page.
    val endParty = { PartyRepository.end(); onBack() }
    val leaveParty = { PartyRepository.leave(); onBack() }
    var confirmEnd by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<PartyMember?>(null) }
    val clipboard = LocalClipboardManager.current
    val invite = stringResource(Res.string.party_invite_message, formatPartyCode(party.code), partyInviteLink(party.code))
    val copiedLabel = stringResource(Res.string.party_invite_copied)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    val copyInvite = {
        clipboard.setText(AnnotatedString(invite))
        copied = true
        NuvioToastController.show(copiedLabel)
    }

    MediaCard(party)
    CodeCard(party, copied, copyInvite)
    MembersCard(party, onRemove = { memberToRemove = it }, onInvite = copyInvite)
    if (party.isHost) {
        SettingCard(
            title = stringResource(Res.string.party_host_only_title),
            line = stringResource(Res.string.party_host_only_description),
            checked = party.hostOnly,
            tag = "party_lobby_host_only",
            onChange = PartyRepository::setHostOnly,
        )
        SettingCard(
            title = stringResource(Res.string.party_share_title),
            line = stringResource(Res.string.party_share_description),
            checked = party.sharing,
            tag = "party_lobby_sharing",
            onChange = PartyRepository::setSharing,
        )
    } else if (party.hostOnly) {
        Row(Modifier.fillMaxWidth().lobbyCard().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.size(16.dp))
            Text(stringResource(Res.string.party_host_only_note), style = redesignText(13.sp, color = RedesignColors.textSecondary))
        }
    }
    RedesignPillButton(
        label = stringResource(Res.string.party_watch_with_party),
        onClick = { onWatch(party.media) },
        primary = true,
        leading = Icons.Rounded.PlayArrow,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("party_lobby_watch"),
    )
    if (party.isHost) {
        RedesignPillButton(
            label = stringResource(Res.string.party_end),
            // Alone in the party there is nobody to warn.
            onClick = { if (party.members.size > 1) confirmEnd = true else endParty() },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("party_lobby_end"),
        )
    } else {
        RedesignPillButton(
            label = stringResource(Res.string.party_leave),
            onClick = leaveParty,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("party_lobby_leave"),
        )
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = stringResource(Res.string.party_end_title),
            message = stringResource(Res.string.party_end_message),
            confirmLabel = stringResource(Res.string.party_end),
            onConfirm = { confirmEnd = false; endParty() },
            onDismiss = { confirmEnd = false },
        )
    }
    memberToRemove?.let { member ->
        ConfirmDialog(
            title = stringResource(Res.string.party_remove_title, member.displayName),
            message = stringResource(Res.string.party_remove_message),
            confirmLabel = stringResource(Res.string.action_remove),
            onConfirm = { memberToRemove = null; PartyRepository.removeMember(member.id) },
            onDismiss = { memberToRemove = null },
        )
    }
}

/** The episode (or film) with its still and where playing starts. */
@Composable
private fun MediaCard(party: ActiveParty) {
    val media = party.media
    val resumeMs = remember(media.key) { partyLobbyResumeMs(media) }
    Row(
        Modifier.fillMaxWidth().lobbyCard().padding(14.dp).testTag("party_lobby_media"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(12.dp)).background(RedesignColors.fill)) {
            NuvioAsyncImage(model = media.background ?: media.poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.party_lobby_eyebrow, media.title).uppercase(),
                style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                media.episodeLabel()?.let { label -> listOfNotNull(label, media.episodeTitle?.takeIf { it.isNotBlank() && it !in label }).joinToString(" · ") } ?: media.title,
                style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                when {
                    !party.isHost -> stringResource(Res.string.party_lobby_joins_host)
                    resumeMs != null -> stringResource(Res.string.party_lobby_starts_from, formatPlaybackTime(resumeMs))
                    else -> stringResource(Res.string.party_lobby_starts_beginning)
                },
                style = redesignText(12.8.sp, color = RedesignColors.textSecondary),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Where the host's own progress would start the party: their saved position, else none. */
internal fun partyLobbyResumeMs(media: PartyMedia): Long? =
    WatchProgressRepository.progressForVideo(media.videoId ?: media.metaId, media.metaId, media.season, media.episode)
        ?.takeUnless { it.isEffectivelyCompleted }
        ?.lastPositionMs
        ?.takeIf { it > 0L }

@Composable
private fun CodeCard(party: ActiveParty, copied: Boolean, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().lobbyCard().padding(horizontal = 18.dp, vertical = 16.dp).testTag("party_lobby_code"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (party.connected) Res.string.party_code_label else Res.string.party_reconnecting),
                style = redesignText(12.sp, color = if (party.connected) RedesignColors.textMuted else RedesignColors.textSecondary),
            )
            Text(formatPartyCode(party.code), style = redesignText(30.sp, FontWeight.Bold, letterSpacing = 0.08.em))
        }
        RedesignPillButton(
            label = stringResource(Res.string.party_copy_invite),
            onClick = onCopy,
            leading = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            modifier = Modifier.testTag("party_lobby_copy"),
        )
    }
}

@Composable
private fun MembersCard(party: ActiveParty, onRemove: (PartyMember) -> Unit, onInvite: () -> Unit) {
    val initialIds = remember { party.members.map { it.id }.toSet() }
    Column(Modifier.fillMaxWidth().lobbyCard().padding(horizontal = 18.dp, vertical = 14.dp).testTag("party_lobby_members")) {
        Text(
            stringResource(Res.string.party_lobby_members, party.members.size, party.maxMembers).uppercase(),
            style = redesignText(11.sp, FontWeight.Medium, RedesignColors.textMuted, letterSpacing = 0.1.em),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        party.members.forEach { member ->
            key(member.id) {
                val appear = remember { MutableTransitionState(member.id in initialIds).apply { targetState = true } }
                AnimatedVisibility(appear, enter = fadeIn(tween(240)) + expandVertically(tween(280))) {
                    MemberLine(party, member, onRemove)
                }
            }
        }
        if (party.members.size < party.maxMembers) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onInvite)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OpenSeat(size = 32.dp)
                Text(stringResource(Res.string.party_lobby_waiting), style = redesignText(14.sp, color = RedesignColors.textMuted))
            }
        }
    }
}

@Composable
private fun MemberLine(party: ActiveParty, member: PartyMember, onRemove: (PartyMember) -> Unit) {
    val isSelf = member.id == party.memberId
    val behind = party.behindMs(member)
    val status = if (behind != null) stringResource(Res.string.party_behind, formatPlaybackTime(behind)) else stringResource(party.statusRes(member))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PartyAvatar(member = member, size = 32.dp, presence = party.presenceState(member), ringColor = Color(0xFF1E2024))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isSelf) stringResource(Res.string.party_you) else member.displayName,
                    style = redesignText(14.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.id == party.hostMemberId) {
                    Text(
                        stringResource(Res.string.party_host),
                        style = redesignText(11.sp, FontWeight.SemiBold, RedesignColors.textSecondary),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(RedesignColors.fill).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(status, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1)
        }
        if (party.isHost && !isSelf) {
            RedesignIconButton(stringResource(Res.string.action_remove), { onRemove(member) }, vector = Icons.Rounded.Close, size = 32.dp)
        }
    }
}

@Composable
private fun SettingCard(title: String, line: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().lobbyCard()
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch) { onChange(!checked) }
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .testTag(tag)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = redesignText(14.sp, FontWeight.SemiBold))
            Text(line, style = redesignText(12.5.sp, color = RedesignColors.textMuted, lineHeight = 17.sp))
        }
        RedesignSwitch(checked)
    }
}
