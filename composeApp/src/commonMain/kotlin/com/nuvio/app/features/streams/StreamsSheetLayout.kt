package com.nuvio.app.features.streams

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.streams_sheet_all_languages
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.LocalDetailTopBarState
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.core.ui.DesktopTopBarContentGap
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignOnLight
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.debrid.DebridStreamResolution
import com.nuvio.app.features.details.components.BoardPlayButton
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.detail_board_episode_code
import nuvio.composeapp.generated.resources.streams_checking_more_addons
import nuvio.composeapp.generated.resources.streams_sheet_all
import nuvio.composeapp.generated.resources.streams_sheet_best_match
import nuvio.composeapp.generated.resources.streams_sheet_count
import nuvio.composeapp.generated.resources.streams_sheet_count_one_addon
import nuvio.composeapp.generated.resources.streams_sheet_faster_start
import nuvio.composeapp.generated.resources.streams_sheet_instant_only
import nuvio.composeapp.generated.resources.streams_sheet_looking
import nuvio.composeapp.generated.resources.streams_sheet_none_filtered
import nuvio.composeapp.generated.resources.streams_sheet_on_server
import nuvio.composeapp.generated.resources.streams_sheet_play_quality
import nuvio.composeapp.generated.resources.streams_sheet_refresh
import nuvio.composeapp.generated.resources.streams_sheet_searching
import nuvio.composeapp.generated.resources.streams_sheet_sort_quality
import nuvio.composeapp.generated.resources.streams_sheet_sort_seeds
import nuvio.composeapp.generated.resources.streams_sheet_sort_size
import nuvio.composeapp.generated.resources.streams_sheet_title
import nuvio.composeapp.generated.resources.streams_torrent_not_supported
import org.jetbrains.compose.resources.stringResource

/** Desktop windows at least this wide show the Sources sheet in place of the two-pane layout. */
internal val StreamsSheetMinWidth = 1000.dp

private val SheetShape = RoundedCornerShape(24.dp)
private val SheetSurface = Color(0xD10E0F12)

/**
 * The desktop Sources sheet. Left: what is about to play, where it resumes, and the Best match.
 * Right: every source, grouped by resolution, with the release decoded into plain labels.
 */
@Composable
internal fun StreamsSheetLayout(
    isEpisode: Boolean,
    title: String,
    background: String?,
    poster: String?,
    episodeThumbnail: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    uiState: StreamsUiState,
    debridEnabled: Boolean,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamSecondaryClick: (StreamItem, Offset) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdropMode = LocalShellBackdropMode.current
    val artwork = background?.takeIf { it.isNotBlank() } ?: poster
    val backdrop = remember(backdropMode == ShellBackdropMode.Solid) {
        if (backdropMode != ShellBackdropMode.Solid) BackdropFocusController() else null
    }
    LaunchedEffect(backdrop, artwork) { backdrop?.base = artwork }
    val topBarState = LocalDetailTopBarState.current
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        if (topBarState != null) DesktopMergedTopBarHeight + DesktopTopBarContentGap else 16.dp

    val torrentNotSupported = stringResource(Res.string.streams_torrent_not_supported)
    val rows = remember(uiState.filteredGroups) { uiState.filteredGroups.flatMap { it.streams }.map(::streamRowInfo) }
    val selectable: (StreamItem) -> Boolean = { it.isSelectableForPlayback(debridEnabled) }
    val preferredLanguages = rememberStreamPreferredLanguages()
    val rememberedLanguage = StreamBadgeSettingsRepository.uiState.collectAsStateWithLifecycle().value.sheetLanguage
    val picks = remember(rows, debridEnabled, preferredLanguages) { streamQuickPicks(rows, preferredLanguages, selectable) }
    var filter by remember { mutableStateOf(StreamSheetFilter(language = rememberedLanguage)) }
    // The pills keep one order (Settings' languages, then the most common), whichever is selected:
    // ordering them by the selection made every click shuffle the row.
    val settingsLanguages = rememberStreamSettingsLanguages()
    val languages = remember(rows, settingsLanguages, filter.language) {
        val present = streamLanguageChoices(rows, settingsLanguages).take(6)
        // A remembered language no source has is added at the end, so it can still be switched off.
        present + listOfNotNull(filter.language?.takeIf { it !in present })
    }
    val resolutions = remember(rows) { streamResolutionChoices(rows) }
    // A resolution that leaves the list (another addon filter) must not keep filtering it to nothing.
    LaunchedEffect(resolutions) { if (filter.resolution != null && filter.resolution !in resolutions) filter = filter.copy(resolution = null) }
    val grouped = remember(rows, filter, preferredLanguages) { groupStreamRows(rows, filter, preferredLanguages) }
    val play: (StreamItem) -> Unit = { stream ->
        if (selectable(stream)) onStreamSelected(stream, resumePositionMs, resumeProgressFraction)
        else if (stream.needsLocalDebridResolve && !AppFeaturePolicy.p2pEnabled) NuvioToastController.show(torrentNotSupported)
    }

    RedesignTypography {
        Box(modifier.fillMaxSize()) {
            if (backdrop != null) ShellBackdrop(backdrop, showArtwork = backdropMode == ShellBackdropMode.Ambient)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (backdrop != null) 0.28f else 0f)))
            Row(
                Modifier.fillMaxSize()
                    .padding(start = LocalBackdropContentStartInset.current)
                    .padding(start = 24.dp, end = 24.dp, top = topPadding, bottom = 20.dp)
                    .clip(SheetShape).background(SheetSurface).border(1.dp, RedesignColors.hairline, SheetShape)
                    .testTag("streams_sheet"),
            ) {
                Column(Modifier.width(400.dp).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetTitleTile(
                        artwork = episodeThumbnail?.takeIf { isEpisode && it.isNotBlank() } ?: artwork,
                        kicker = if (isEpisode && seasonNumber != null && episodeNumber != null) {
                            "$title · ${stringResource(Res.string.detail_board_episode_code, seasonNumber, episodeNumber)}"
                        } else {
                            null
                        },
                        title = episodeTitle?.takeIf { isEpisode && it.isNotBlank() } ?: title,
                        onBack = onBack,
                    )
                    ResumeBanner(positionMs = resumePositionMs, progressFraction = resumeProgressFraction)
                    BestMatchCard(
                        picks = picks,
                        preferredLanguages = preferredLanguages,
                        loading = uiState.isAnyLoading || uiState.groups.any { it.isLoading },
                        onPlay = play,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(RedesignColors.hairline))
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 22.dp, end = 14.dp, top = 18.dp)) {
                    SheetHeader(uiState = uiState, total = rows.size)
                    Spacer(Modifier.height(12.dp))
                    SheetFilters(
                        uiState = uiState,
                        resolutions = resolutions,
                        languages = languages,
                        filter = filter,
                        onFilter = { next ->
                            if (next.language != filter.language) StreamBadgeSettingsRepository.setSheetLanguage(next.language)
                            filter = next
                        },
                        onRefresh = onRefresh,
                    )
                    Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(RedesignColors.hairline))
                    SheetList(
                        uiState = uiState,
                        grouped = grouped,
                        hasRows = rows.isNotEmpty(),
                        selectable = selectable,
                        preferredLanguages = preferredLanguages,
                        onPlay = play,
                        onSecondaryClick = onStreamSecondaryClick,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetTitleTile(artwork: String?, kicker: String?, title: String, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, RoundedCornerShape(16.dp)),
    ) {
        NuvioAsyncImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color(0xEB08090B))))
        RedesignIconButton(
            contentDescription = stringResource(Res.string.action_back),
            onClick = onBack,
            vector = Icons.AutoMirrored.Rounded.ArrowBack,
            size = 40.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.34f)).testTag("streams_sheet_back"),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            if (kicker != null) StreamKicker(kicker, RedesignColors.textSecondary)
            Text(title, style = redesignText(21.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The source the app would pick, with the reason, and the smaller and own-server alternatives. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BestMatchCard(
    picks: StreamQuickPicks,
    preferredLanguages: List<String>,
    loading: Boolean,
    onPlay: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(16.dp)
            .testTag("streams_sheet_best"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val best = picks.best
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreamKicker(stringResource(Res.string.streams_sheet_best_match), modifier = Modifier.weight(1f))
            if (best != null) {
                // Why this one: the preferred language it carries, its quality, and that it starts now.
                val language = preferredLanguages.firstOrNull { it in best.languages }?.let(::streamLanguageName)
                Text(
                    listOfNotNull(language, streamQualityLabel(best.resolution)).joinToString(" · "),
                    style = redesignText(12.sp, FontWeight.SemiBold, Color(0xFF7BE0B0)),
                    maxLines = 1,
                )
            }
        }
        if (best == null) {
            Text(
                stringResource(if (loading) Res.string.streams_sheet_looking else Res.string.streams_sheet_none_filtered),
                style = redesignText(14.sp, color = RedesignColors.textSecondary),
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StreamQualityBadge(best.resolution)
            best.labels.take(3).forEach { StreamTag(it) }
        }
        if (best.languages.isNotEmpty() || best.audioTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StreamLanguageChips(best, preferredLanguages)
                best.audioTags.take(4).forEach { StreamAudioTag(it) }
            }
        }
        Text(best.rawName, style = redesignText(12.5.sp, FontWeight.Medium, RedesignColors.textSecondary, lineHeight = 17.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            best.sizeBytes?.let { Text(formatStreamSize(it), style = redesignText(12.5.sp, FontWeight.SemiBold, RedesignColors.textSecondary)) }
            Text(best.stream.addonName, style = redesignText(12.5.sp, color = RedesignColors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            StreamStatusChip(best)
        }
        BoardPlayButton(
            label = stringResource(Res.string.streams_sheet_play_quality, streamQualityLabel(best.resolution)),
            enabled = true,
            onClick = { onPlay(best.stream) },
            onLongClick = null,
            modifier = Modifier.fillMaxWidth().testTag("streams_sheet_play_best"),
        )
        Spacer(Modifier.weight(1f))
        picks.faster?.let { AlternativeRow(stringResource(Res.string.streams_sheet_faster_start), it, onPlay) }
        picks.server?.let { AlternativeRow(stringResource(Res.string.streams_sheet_on_server), it, onPlay) }
    }
}

@Composable
private fun AlternativeRow(label: String, row: StreamRowInfo, onPlay: (StreamItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else RedesignColors.fill, tween(160), label = "alt_row")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onPlay(row.stream) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = redesignText(12.5.sp, color = RedesignColors.textMuted), maxLines = 1)
        Text(
            listOfNotNull(streamQualityLabel(row.resolution), row.labels.firstOrNull(), row.sizeBytes?.let(::formatStreamSize)).joinToString(" · "),
            style = redesignText(12.5.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StreamStatusChip(row)
    }
}

@Composable
private fun SheetHeader(uiState: StreamsUiState, total: Int) {
    val loading = uiState.isAnyLoading || uiState.groups.any { it.isLoading }
    val addons = uiState.groups.count { it.streams.isNotEmpty() }
    val count = if (addons == 1) stringResource(Res.string.streams_sheet_count_one_addon, total) else stringResource(Res.string.streams_sheet_count, total, addons)
    Column {
        Text(stringResource(Res.string.streams_sheet_title), style = redesignText(22.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
        Text(
            when {
                total > 0 && loading -> "$count · ${stringResource(Res.string.streams_checking_more_addons)}"
                total > 0 -> count
                loading -> stringResource(Res.string.streams_sheet_searching)
                else -> " "
            },
            style = redesignText(12.5.sp, color = RedesignColors.textMuted),
            maxLines = 1,
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, trailing: String? = null, tag: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> RedesignColors.textPrimary
            hovered -> RedesignColors.fillStrong
            else -> RedesignColors.fill
        },
        tween(180),
        label = "filter_pill",
    )
    val content by animateColorAsState(if (selected) RedesignOnLight else RedesignColors.textSecondary, tween(180), label = "filter_pill_text")
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(fill).border(1.dp, RedesignColors.hairline, CircleShape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = redesignText(12.8.sp, FontWeight.SemiBold, content), maxLines = 1)
        if (trailing != null) Text("  $trailing", style = redesignText(12.sp, FontWeight.Medium, content.copy(alpha = 0.7f)), maxLines = 1)
    }
}

@Composable
private fun SheetFilters(
    uiState: StreamsUiState,
    resolutions: List<DebridStreamResolution>,
    languages: List<String>,
    filter: StreamSheetFilter,
    onFilter: (StreamSheetFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    val all = stringResource(Res.string.streams_sheet_all)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(all, selected = uiState.selectedFilter == null, onClick = { StreamsRepository.selectFilter(null) },
                trailing = uiState.groups.sumOf { it.streams.size }.takeIf { it > 0 }?.toString(), tag = "streams_sheet_addon_all")
            uiState.groups.filter { it.streams.isNotEmpty() || it.isLoading }.forEach { group ->
                FilterPill(
                    group.addonName,
                    selected = uiState.selectedFilter == group.addonId,
                    onClick = { StreamsRepository.selectFilter(group.addonId) },
                    trailing = if (group.isLoading) "…" else group.streams.size.toString(),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (resolutions.size > 1) {
                val options: List<DebridStreamResolution?> = listOf<DebridStreamResolution?>(null) + resolutions
                RedesignPillGroup(
                    options = options,
                    selected = filter.resolution,
                    label = { if (it == null) all else streamQualityLabel(it) },
                    onSelect = { onFilter(filter.copy(resolution = it)) },
                )
            }
            FilterPill(
                stringResource(Res.string.streams_sheet_instant_only),
                selected = filter.instantOnly,
                onClick = { onFilter(filter.copy(instantOnly = !filter.instantOnly)) },
                tag = "streams_sheet_instant_only",
            )
            FilterPill(
                stringResource(
                    when (filter.sort) {
                        StreamSheetSort.Quality -> Res.string.streams_sheet_sort_quality
                        StreamSheetSort.Size -> Res.string.streams_sheet_sort_size
                        StreamSheetSort.Seeds -> Res.string.streams_sheet_sort_seeds
                    },
                ),
                selected = false,
                onClick = { onFilter(filter.copy(sort = StreamSheetSort.entries[(filter.sort.ordinal + 1) % StreamSheetSort.entries.size])) },
                tag = "streams_sheet_sort",
            )
            Spacer(Modifier.weight(1f))
            RedesignIconButton(
                contentDescription = stringResource(Res.string.streams_sheet_refresh),
                onClick = onRefresh,
                vector = Icons.Rounded.Refresh,
                size = 32.dp,
            )
        }
        if (languages.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(
                    stringResource(Res.string.streams_sheet_all_languages),
                    selected = filter.language == null,
                    onClick = { onFilter(filter.copy(language = null)) },
                    tag = "streams_sheet_language_all",
                )
                languages.forEach { code ->
                    FilterPill(
                        streamLanguageName(code),
                        selected = filter.language == code,
                        onClick = { onFilter(filter.copy(language = if (filter.language == code) null else code)) },
                        tag = "streams_sheet_language_$code",
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetList(
    uiState: StreamsUiState,
    grouped: List<Pair<DebridStreamResolution, List<StreamRowInfo>>>,
    hasRows: Boolean,
    selectable: (StreamItem) -> Boolean,
    preferredLanguages: List<String>,
    onPlay: (StreamItem) -> Unit,
    onSecondaryClick: (StreamItem, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val loading = uiState.isAnyLoading || uiState.groups.any { it.isLoading }
    val highlightOf = LocalStreamHighlight.current
    val shared = LocalSharedStream.current
    val sharedRow = remember(shared) { shared?.let { streamRowInfo(it.stream) } }
    Box(modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
            if (shared != null && sharedRow != null) {
                item(key = "shared") {
                    Column(Modifier.animateItem()) {
                        GroupHeader(shared.label, null)
                        StreamSheetRow(sharedRow, enabled = true, highlight = null, preferredLanguages = preferredLanguages, onClick = { onPlay(shared.stream) }, onSecondaryClick = {})
                    }
                }
            }
            when {
                !hasRows && loading -> items(6, key = { "skeleton-$it" }) { StreamSheetRowSkeleton() }
                !hasRows -> item(key = "empty") { EmptyStateBlock(reason = uiState.emptyStateReason) }
                grouped.isEmpty() -> item(key = "none") {
                    Text(
                        stringResource(Res.string.streams_sheet_none_filtered),
                        style = redesignText(14.sp, color = RedesignColors.textSecondary),
                        modifier = Modifier.animateItem().padding(horizontal = 10.dp, vertical = 24.dp),
                    )
                }
                else -> grouped.forEach { (resolution, rows) ->
                    item(key = "group-${resolution.name}") {
                        Box(Modifier.animateItem()) { GroupHeader(streamQualityLabel(resolution), rows.size) }
                    }
                    items(rows.size, key = { index -> "row-${resolution.name}-$index-${streamSheetRowKey(rows[index].stream)}" }) { index ->
                        val row = rows[index]
                        StreamSheetRow(
                            row = row,
                            enabled = selectable(row.stream),
                            highlight = highlightOf(row.stream),
                            preferredLanguages = preferredLanguages,
                            onClick = { onPlay(row.stream) },
                            onSecondaryClick = { position ->
                                val stream = row.stream
                                if (stream.playableDirectUrl != null || stream.shouldOpenExternally || stream.isAddonDebridCandidate) {
                                    onSecondaryClick(stream, position)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(20.dp)) }
        }
        NuvioDesktopVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp),
        )
    }
}

private fun streamSheetRowKey(stream: StreamItem): String =
    listOf(stream.addonId, stream.url ?: stream.infoHash ?: stream.clientResolve?.infoHash ?: stream.streamLabel, stream.fileIdx).joinToString("|")

@Composable
private fun GroupHeader(title: String, count: Int?) {
    Row(Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = redesignText(14.sp, FontWeight.Bold), maxLines = 1)
        if (count != null) Text(count.toString(), style = redesignText(12.sp, color = RedesignColors.textMuted))
    }
}
