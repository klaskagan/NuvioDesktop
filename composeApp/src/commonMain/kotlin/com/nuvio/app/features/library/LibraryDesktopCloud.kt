package com.nuvio.app.features.library

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignKicker
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.SkeletonBlock
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.cloud.CloudLibraryItem
import com.nuvio.app.features.cloud.CloudLibraryItemType
import com.nuvio.app.features.cloud.CloudLibraryProviderState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The Cloud pane's filtered view of the loaded items (filtering is local, as on mobile). */
internal class LibraryCloudView(
    val state: Kind,
    val availableTypes: List<CloudLibraryItemType>,
    val selectedType: CloudLibraryItemType?,
    val items: List<CloudLibraryItem>,
    val failedProviders: List<CloudLibraryProviderState>,
    val hasActiveFilter: Boolean,
    val selectedItem: CloudLibraryItem?,
) {
    enum class Kind { Loading, Disabled, NotConnected, Ready }

    val showToolbar: Boolean get() = state == Kind.Ready || state == Kind.Loading
}

@Composable
internal fun rememberCloudView(model: LibraryDesktopModel): LibraryCloudView = remember(
    model.cloud, model.cloudProviderId, model.cloudType, model.cloudQuery, model.cloudItemKey,
) {
    val ui = model.cloud
    val kind = when {
        !ui.isLoaded -> LibraryCloudView.Kind.Loading
        !ui.isEnabled -> LibraryCloudView.Kind.Disabled
        !ui.hasConnectedProvider -> LibraryCloudView.Kind.NotConnected
        else -> LibraryCloudView.Kind.Ready
    }
    val providerItems = ui.items.filter { model.cloudProviderId == null || it.providerId == model.cloudProviderId }
    val types = providerItems.map { it.type }.distinct().sortedBy { it.ordinal }
    val type = model.cloudType?.takeIf { it in types }
    val typed = providerItems.filter { type == null || it.type == type }
    val query = model.cloudQuery.trim()
    // The useful identifier is often in a file name, not the title.
    val filtered = if (query.isEmpty()) typed else typed.filter { item ->
        item.name.contains(query, ignoreCase = true) || item.files.any { it.name.contains(query, ignoreCase = true) }
    }
    val failed = ui.providers
        .filter { model.cloudProviderId == null || it.providerId == model.cloudProviderId }
        .filter { !it.errorMessage.isNullOrBlank() && it.items.isEmpty() }
    LibraryCloudView(
        state = kind,
        availableTypes = types,
        selectedType = type,
        items = filtered,
        failedProviders = failed,
        hasActiveFilter = model.cloudProviderId != null || type != null || query.isNotEmpty(),
        selectedItem = filtered.firstOrNull { it.stableKey == model.cloudItemKey && it.playableFiles.size > 1 },
    )
}

@Composable
internal fun LibraryCloudToolbar(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    view: LibraryCloudView,
    modifier: Modifier,
) {
    val allLabel = stringResource(Res.string.cloud_library_provider_all)
    Row(
        modifier.fillMaxWidth().height(36.dp).testTag("library-cloud-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (view.state == LibraryCloudView.Kind.Ready) {
            val providers = listOf<String?>(null) + model.cloud.providers.map { it.providerId }
            if (providers.size > 2) {
                RedesignPillGroup(
                    options = providers,
                    selected = model.cloudProviderId,
                    label = { id ->
                        id?.let { pid ->
                            val provider = model.cloud.providers.firstOrNull { it.providerId == pid }
                            "${provider?.providerName.orEmpty()}  ${provider?.items?.size ?: 0}"
                        } ?: allLabel
                    },
                    onSelect = actions.cloudSelectProvider,
                    optionTag = { "library-cloud-provider-${it ?: "all"}" },
                )
            }
            if (view.availableTypes.size > 1) {
                val typeAll = stringResource(Res.string.cloud_library_type_all)
                RedesignPillGroup(
                    options = listOf<CloudLibraryItemType?>(null) + view.availableTypes,
                    selected = view.selectedType,
                    label = { type -> type?.let { cloudLibraryTypeLabel(it) } ?: typeAll },
                    onSelect = actions.cloudSelectType,
                    optionTag = { "library-cloud-type-${it?.name ?: "all"}" },
                )
            }
        }
        LibraryCloudSearchField(model.cloudQuery, actions.cloudQueryChange, Modifier.weight(1f))
    }
}

@Composable
private fun LibraryCloudSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.height(36.dp).clip(CircleShape).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, CircleShape)
            .padding(start = 14.dp, end = 6.dp)
            .testTag("library-cloud-search"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = RedesignColors.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(Res.string.cloud_library_search_label), style = redesignText(13.sp, color = RedesignColors.textMuted), maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = redesignText(13.sp, FontWeight.Medium),
                cursorBrush = SolidColor(RedesignColors.textPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Reserved, so typing the first character doesn't move the text.
        RedesignIconButton(
            contentDescription = stringResource(Res.string.compose_search_clear),
            onClick = if (query.isNotEmpty()) ({ onQueryChange("") }) else null,
            vector = Icons.Rounded.Close,
            size = 26.dp,
        )
    }
}

internal fun LazyListScope.libraryCloudItems(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    view: LibraryCloudView,
) {
    when (view.state) {
        LibraryCloudView.Kind.Loading -> libraryCloudPlaceholders()
        LibraryCloudView.Kind.Disabled -> item(key = "cloud-disabled") {
            LibraryMessageTile(
                title = stringResource(Res.string.cloud_library_disabled_title),
                message = stringResource(Res.string.cloud_library_disabled_message),
                actionLabel = stringResource(Res.string.cloud_library_disabled_action),
                onAction = actions.onConnectCloudClick,
                icon = Icons.Rounded.CloudOff,
                modifier = libraryDesktopItemModifier(),
            )
        }
        LibraryCloudView.Kind.NotConnected -> item(key = "cloud-connect") {
            LibraryMessageTile(
                title = stringResource(Res.string.cloud_library_connect_title),
                message = stringResource(Res.string.cloud_library_connect_message),
                actionLabel = stringResource(Res.string.cloud_library_connect_action),
                onAction = actions.onConnectCloudClick,
                icon = Icons.Rounded.CloudOff,
                modifier = libraryDesktopItemModifier(),
            )
        }
        LibraryCloudView.Kind.Ready -> {
            view.failedProviders.forEach { provider ->
                item(key = "cloud-error-${provider.providerId}") {
                    LibraryMessageTile(
                        title = stringResource(Res.string.cloud_library_load_failed, provider.providerName),
                        message = provider.errorMessage.orEmpty(),
                        actionLabel = stringResource(Res.string.action_retry),
                        onAction = actions.cloudRefresh,
                        modifier = libraryDesktopItemModifier().padding(bottom = 12.dp),
                    )
                }
            }
            when {
                model.cloud.isRefreshing && view.items.isEmpty() -> libraryCloudPlaceholders()
                view.items.isEmpty() && view.failedProviders.isEmpty() -> item(key = "cloud-empty") {
                    LibraryMessageTile(
                        title = stringResource(if (view.hasActiveFilter) Res.string.cloud_library_no_matches_title else Res.string.cloud_library_empty_title),
                        message = stringResource(if (view.hasActiveFilter) Res.string.cloud_library_no_matches_message else Res.string.cloud_library_empty_message),
                        actionLabel = if (view.hasActiveFilter) null else stringResource(Res.string.action_retry),
                        onAction = if (view.hasActiveFilter) null else actions.cloudRefresh,
                        modifier = libraryDesktopItemModifier(),
                    )
                }
                else -> {
                    val last = view.items.lastIndex
                    items(view.items.size, key = { view.items[it].stableKey }) { index ->
                        val item = view.items[index]
                        LibraryCloudRow(
                            item = item,
                            selected = item.stableKey == model.cloudItemKey,
                            shape = cloudRowShape(index, last),
                            divider = index > 0,
                            onClick = { actions.cloudSelectItem(item) },
                            modifier = libraryDesktopItemModifier(),
                        )
                    }
                }
            }
        }
    }
}

/** Rows read as one frosted tile: only the first and last round their outer corners. */
private fun cloudRowShape(index: Int, last: Int): Shape {
    val radius = 18.dp
    return RoundedCornerShape(
        topStart = if (index == 0) radius else 0.dp,
        topEnd = if (index == 0) radius else 0.dp,
        bottomStart = if (index == last) radius else 0.dp,
        bottomEnd = if (index == last) radius else 0.dp,
    )
}

private fun LazyListScope.libraryCloudPlaceholders() {
    items(4, key = { "cloud-placeholder:$it" }) { index ->
        Row(
            libraryDesktopItemModifier().fillMaxWidth().clip(cloudRowShape(index, 3)).background(RedesignColors.fill)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBlock(width = 40.dp, height = 40.dp, cornerRadius = 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.6f), height = 14.dp)
                SkeletonBlock(Modifier.fillMaxWidth(0.35f), height = 11.dp)
            }
            SkeletonBlock(width = 36.dp, height = 36.dp, cornerRadius = 18.dp)
        }
    }
}

@Composable
private fun LibraryCloudRow(
    item: CloudLibraryItem,
    selected: Boolean,
    shape: Shape,
    divider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val playable = item.playableFiles.size
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.13f)
            hovered && playable > 0 -> RedesignColors.fillStrong
            else -> RedesignColors.fill
        },
        tween(160),
        label = "cloud_row_fill",
    )
    Column(modifier.fillMaxWidth().clip(shape).background(fill)) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        Row(
            Modifier.fillMaxWidth()
                .hoverable(interaction)
                .clickable(enabled = playable > 0, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { this.selected = selected }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("library-cloud-row-${item.stableKey}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RedesignColors.fill), contentAlignment = Alignment.Center) {
                Icon(
                    if (playable > 1) Icons.Rounded.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = RedesignColors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${cloudLibrarySubtitle(item)} · ${cloudLibraryStatusLine(item)}",
                    style = redesignText(12.sp, color = RedesignColors.textMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                item.progressFraction?.takeIf { it in 0f..0.999f }?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = RedesignColors.textSecondary,
                        trackColor = RedesignColors.fill,
                    )
                }
            }
            if (playable > 0) {
                RedesignIconButton(
                    contentDescription = stringResource(Res.string.action_play),
                    onClick = onClick,
                    active = selected,
                ) { tint ->
                    Icon(
                        if (playable > 1) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** The selected multi-file item's files, beside the list (Calendar's selected-day width). */
@Composable
internal fun LibraryCloudFilePanel(
    item: CloudLibraryItem,
    actions: LibraryDesktopActions,
    modifier: Modifier,
) {
    Column(
        modifier.clip(RedesignTileShape).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, RedesignTileShape)
            .padding(16.dp)
            .testTag("library-cloud-panel"),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                RedesignKicker(
                    listOfNotNull(
                        cloudLibraryTypeLabel(item.type),
                        stringResource(Res.string.cloud_library_playable_file_count, item.playableFiles.size),
                        item.sizeBytes?.let(::formatCloudBytes),
                    ).joinToString(" · "),
                )
                Text(
                    item.name,
                    style = redesignText(18.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(item.providerName, style = redesignText(12.sp, color = RedesignColors.textMuted), modifier = Modifier.padding(top = 3.dp))
            }
            RedesignIconButton(
                contentDescription = stringResource(Res.string.action_close),
                onClick = actions.cloudCloseItem,
                vector = Icons.Rounded.Close,
                size = 30.dp,
                modifier = Modifier.testTag("library-cloud-panel-close"),
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (item.playableFiles.isEmpty()) {
                Text(stringResource(Res.string.cloud_library_no_files_message), style = redesignText(13.sp, color = RedesignColors.textSecondary))
            }
            item.playableFiles.forEach { file ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val fill by animateColorAsState(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent, tween(140), label = "cloud_file_fill")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { actions.cloudPlayFile(item, file) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("library-cloud-file-${file.stableKey}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(Res.string.cloud_library_play_file), tint = if (hovered) RedesignColors.textPrimary else RedesignColors.textSecondary, modifier = Modifier.size(18.dp))
                    Text(file.name, style = redesignText(13.sp, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    file.sizeBytes?.let { Text(formatCloudBytes(it), style = redesignText(12.sp, color = RedesignColors.textMuted)) }
                }
            }
        }
    }
}
