package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignDanger
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.executeTrackingMembershipOperation
import com.nuvio.app.features.library.showTrackingMembershipRewriteFeedback
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.library.trackingRemovalText
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingMembershipRemovalConfirmation
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.toggleTrackingLibraryMembership
import com.nuvio.app.features.tracking.trackingMembershipDestinations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_remove_anyway
import nuvio.composeapp.generated.resources.compose_tracking_list_picker_loading
import nuvio.composeapp.generated.resources.detail_board_library
import nuvio.composeapp.generated.resources.detail_board_lists_create
import nuvio.composeapp.generated.resources.detail_board_lists_empty
import nuvio.composeapp.generated.resources.detail_board_lists_footer
import nuvio.composeapp.generated.resources.detail_board_lists_in
import nuvio.composeapp.generated.resources.detail_board_lists_in_one
import nuvio.composeapp.generated.resources.detail_board_lists_keep
import nuvio.composeapp.generated.resources.detail_board_lists_new
import nuvio.composeapp.generated.resources.detail_board_lists_new_hint
import nuvio.composeapp.generated.resources.detail_board_lists_none
import nuvio.composeapp.generated.resources.detail_board_lists_title
import nuvio.composeapp.generated.resources.trakt_lists_load_failed
import nuvio.composeapp.generated.resources.tracking_lists_update_failed
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** A removal a tracker wants confirmed (it clears history or a rating there), shown inside the popover. */
private class PendingListRemoval(
    val key: String,
    val previous: Map<String, Boolean>,
    val desired: Map<String, Boolean>,
    val confirmations: List<TrackingMembershipRemovalConfirmation>,
)

/**
 * Save to list, beside the Lists action: every list the title can be saved to, grouped by where it
 * lives (Nuvio, then each connected tracker). A tick saves at once and a second removes it; a
 * removal that would clear a tracker's history asks inside the popover. New list makes a list on
 * the library's tracker and ticks it.
 */
@Composable
internal fun BoardSaveToListPopover(meta: MetaDetails, dismiss: () -> Unit) {
    val item = remember(meta) { meta.toLibraryItem(savedAtEpochMs = 0L) }
    val scope = rememberCoroutineScope()
    var tabs by remember(item.id) { mutableStateOf(LibraryRepository.libraryListTabs(item)) }
    var membership by remember(item.id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var busyKey by remember(item.id) { mutableStateOf<String?>(null) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    var pendingRemoval by remember(item.id) { mutableStateOf<PendingListRemoval?>(null) }
    var creating by remember(item.id) { mutableStateOf(false) }
    val canCreate = remember(item.id) { LibraryRepository.listManagementContext() != null }
    val updateFailed = stringResource(Res.string.tracking_lists_update_failed)

    LaunchedEffect(item.id) {
        try {
            val snapshot = LibraryRepository.getMembershipSnapshot(item)
            tabs = LibraryRepository.libraryListTabs(item)
            membership = tabs.associate { it.key to (snapshot[it.key] == true) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            error = failure.message ?: getString(Res.string.trakt_lists_load_failed)
        }
        loading = false
    }

    fun apply(key: String, previous: Map<String, Boolean>, desired: Map<String, Boolean>, confirmed: Set<TrackingProviderId>) {
        busyKey = key
        scope.launch {
            executeTrackingMembershipOperation(
                operation = { LibraryRepository.applyMembershipChanges(item, desired, confirmed) },
                onSuccess = { result ->
                    if (result.requiresRemovalConfirmation) {
                        pendingRemoval = PendingListRemoval(key, previous, desired, result.requiredRemovalConfirmations)
                    } else {
                        pendingRemoval = null
                        showTrackingMembershipRewriteFeedback(result)
                    }
                },
                onFailure = { failure ->
                    membership = previous
                    pendingRemoval = null
                    error = failure.message ?: updateFailed
                },
            )
            busyKey = null
        }
    }

    fun toggle(key: String) {
        if (busyKey != null || pendingRemoval != null || loading) return
        val previous = membership
        val desired = toggleTrackingLibraryMembership(tabs, membership, key)
        membership = desired
        error = null
        apply(key, previous, desired, emptySet())
    }

    val destinations = trackingMembershipDestinations(tabs)
    val saved = destinations.count { membership[it.key] == true }
    BoardPopoverPanel(
        title = stringResource(Res.string.detail_board_lists_title),
        subtitle = when (saved) {
            0 -> stringResource(Res.string.detail_board_lists_none, meta.name)
            1 -> stringResource(Res.string.detail_board_lists_in_one, meta.name)
            else -> stringResource(Res.string.detail_board_lists_in, meta.name, saved)
        },
        onClose = dismiss,
        width = 400.dp,
        tag = "detail_lists_popover",
        modifier = Modifier.animateContentSize(tween(220)),
    ) {
        if (loading && destinations.isEmpty()) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NuvioLoadingIndicator(modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.compose_tracking_list_picker_loading), style = redesignText(13.sp, color = RedesignColors.textSecondary))
            }
        } else if (destinations.isEmpty()) {
            Text(stringResource(Res.string.detail_board_lists_empty), style = redesignText(13.sp, color = RedesignColors.textSecondary), modifier = Modifier.padding(vertical = 10.dp))
        } else {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listGroups(destinations).forEach { (caption, group) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(caption, style = redesignText(13.sp, FontWeight.SemiBold, RedesignColors.textMuted), modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                        group.forEach { tab ->
                            ListRow(
                                tab = tab,
                                checked = membership[tab.key] == true,
                                pending = pendingRemoval?.key == tab.key || busyKey == tab.key,
                                enabled = !loading && busyKey == null && pendingRemoval == null,
                                onClick = { toggle(tab.key) },
                            )
                        }
                    }
                }
            }
        }
        error?.let {
            Text(it, style = redesignText(12.5.sp, color = RedesignDanger, lineHeight = 17.sp), modifier = Modifier.padding(top = 10.dp).testTag("detail_lists_error"))
        }
        val removal = pendingRemoval
        AnimatedVisibility(removal != null, enter = fadeIn(tween(180)) + expandVertically(tween(220)), exit = fadeOut(tween(140)) + shrinkVertically(tween(180))) {
            if (removal != null) {
                RemovalConfirm(
                    text = trackingRemovalText(meta.name, removal.confirmations),
                    busy = busyKey != null,
                    onKeep = {
                        membership = removal.previous
                        pendingRemoval = null
                    },
                    onRemove = {
                        if (busyKey == null) apply(removal.key, removal.previous, removal.desired, removal.confirmations.mapTo(linkedSetOf()) { it.providerId })
                    },
                )
            }
        }
        AnimatedVisibility(removal == null, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Column {
                AnimatedVisibility(creating, enter = fadeIn(tween(180)) + expandVertically(tween(220)), exit = fadeOut(tween(140)) + shrinkVertically(tween(180))) {
                    NewListField(
                        onCreate = { name ->
                            scope.launch {
                                val before = tabs.map { it.key }.toSet()
                                try {
                                    val context = LibraryRepository.listManagementContext() ?: return@launch
                                    val manager = LibraryRepository.listManager(context)
                                    manager.createList(name, null, manager.capabilities.privacyOptions.first())
                                    creating = false
                                    // The tracker's lists refresh after a write; wait briefly for the new one, then tick it.
                                    repeat(12) {
                                        val fresh = LibraryRepository.libraryListTabs(item)
                                        val made = fresh.firstOrNull { it.key !in before && it.title.equals(name, ignoreCase = true) }
                                        if (made != null) {
                                            tabs = fresh
                                            toggle(made.key)
                                            return@launch
                                        }
                                        delay(250)
                                    }
                                    tabs = LibraryRepository.libraryListTabs(item)
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (failure: Throwable) {
                                    error = failure.message ?: updateFailed
                                }
                            }
                        },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.detail_board_lists_footer), style = redesignText(12.8.sp, color = RedesignColors.textMuted), modifier = Modifier.weight(1f))
                    if (canCreate && !creating) {
                        RedesignPillButton(
                            label = stringResource(Res.string.detail_board_lists_new),
                            onClick = { creating = true },
                            leading = Icons.Rounded.Add,
                            modifier = Modifier.testTag("detail_lists_new"),
                        )
                    }
                }
            }
        }
    }
}

/** The lists grouped by where they live, Nuvio's own first, each under its source's name. */
internal fun listGroups(destinations: List<TrackingLibraryTab>): List<Pair<String, List<TrackingLibraryTab>>> =
    destinations.groupBy { it.providerId }
        .entries
        .sortedBy { if (it.key == null) 0 else 1 }
        .map { (provider, tabs) -> (provider?.displayName ?: "Nuvio") to tabs }

@Composable
private fun ListRow(tab: TrackingLibraryTab, checked: Boolean, pending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            checked -> Color.White.copy(alpha = 0.10f)
            hovered && enabled -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.05f)
        },
        tween(160),
        label = "list_row_fill",
    )
    val alpha by animateFloatAsState(if (pending) 0.55f else 1f, tween(160), label = "list_row_alpha")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).alpha(alpha).clip(RoundedCornerShape(12.dp)).background(fill)
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Checkbox, onClick = onClick)
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .testTag("detail_lists_row_${tab.key}")
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // Nuvio's own list already sits under "Nuvio", so it reads just "Library".
            val title = if (tab.providerId == null) stringResource(Res.string.detail_board_library) else tab.title
            Text(title, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            tab.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = redesignText(12.sp, color = RedesignColors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val box by animateColorAsState(if (checked) RedesignColors.textPrimary else Color.Transparent, tween(160), label = "list_check_fill")
        val edge by animateColorAsState(if (checked) RedesignColors.textPrimary else Color.White.copy(alpha = 0.35f), tween(160), label = "list_check_edge")
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(box).border(2.dp, edge, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = RedesignOnLight, modifier = Modifier.size(14.dp))
        }
    }
}

private val ConfirmTint = Color(0xFFF06B6B)

@Composable
private fun RemovalConfirm(
    text: com.nuvio.app.features.library.TrackingRemovalText,
    busy: Boolean,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(shape).background(ConfirmTint.copy(alpha = 0.10f))
            .border(1.dp, ConfirmTint.copy(alpha = 0.35f), shape).padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("detail_lists_confirm"),
    ) {
        Text(text.title, style = redesignText(14.sp, FontWeight.SemiBold))
        Text(text.message, style = redesignText(12.8.sp, color = RedesignColors.textSecondary, lineHeight = 18.5.sp), modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RedesignPillButton(
                label = stringResource(Res.string.detail_board_lists_keep),
                onClick = { if (!busy) onKeep() },
                modifier = Modifier.weight(1f).testTag("detail_lists_keep"),
            )
            DangerPill(stringResource(Res.string.action_remove_anyway), busy, onRemove, Modifier.weight(1f).testTag("detail_lists_remove"))
        }
    }
}

@Composable
private fun DangerPill(label: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) ConfirmTint else ConfirmTint.copy(alpha = 0.85f), tween(160), label = "danger_pill")
    Box(
        modifier.height(40.dp).clip(CircleShape).background(fill).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { if (!busy) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) NuvioLoadingIndicator(modifier = Modifier.size(16.dp))
        else Text(label, style = redesignText(13.sp, FontWeight.SemiBold, RedesignOnLight), maxLines = 1)
    }
}

@Composable
private fun NewListField(onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { name.trim().takeIf { it.isNotEmpty() }?.let(onCreate); Unit }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(40.dp).clip(CircleShape).background(RedesignColors.fill)
                .border(1.dp, RedesignColors.hairline, CircleShape).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (name.isEmpty()) Text(stringResource(Res.string.detail_board_lists_new_hint), style = redesignText(13.sp, color = RedesignColors.textMuted))
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                textStyle = redesignText(13.sp, color = RedesignColors.textPrimary),
                cursorBrush = SolidColor(RedesignColors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("detail_lists_new_name"),
            )
        }
        RedesignPillButton(label = stringResource(Res.string.detail_board_lists_create), onClick = submit, primary = true, modifier = Modifier.testTag("detail_lists_create"))
    }
    Spacer(Modifier.height(2.dp))
}
