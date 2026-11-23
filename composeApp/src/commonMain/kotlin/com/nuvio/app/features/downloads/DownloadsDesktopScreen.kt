package com.nuvio.app.features.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.LocalDetailTopBarState
import com.nuvio.app.core.ui.BackdropFocusController
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.core.ui.LocalBackdropContentStartInset
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.LocalShellBackdropMode
import com.nuvio.app.core.ui.NavLayer
import com.nuvio.app.core.ui.NavSide
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.RedesignAction
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignMessageTile
import com.nuvio.app.core.ui.RedesignOverflowButton
import com.nuvio.app.core.ui.mousePressAndHoldAt
import com.nuvio.app.core.ui.secondaryClick
import com.nuvio.app.core.ui.RedesignPageLead
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.core.ui.ShellBackdrop
import com.nuvio.app.core.ui.ShellBackdropMode
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.navTransitionLayer
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val DownloadCardMinWidth = 300.dp

/**
 * Desktop Downloads on the redesign: its own ambient backdrop (from the latest download's art),
 * a Library-style header under the merged top bar, active transfers as one frosted list, finished
 * titles as landscape cards, and a show's episodes in place (Esc returns). Card and row actions are
 * a frosted ⋯ menu instead of the mobile bottom sheet.
 */
@Composable
internal fun DownloadsDesktopScreen(
    onBack: () -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenDetails: (DownloadItem) -> Unit,
    initialShowId: String? = null,
    onBackFromShow: (() -> Unit)? = null,
    /** Tests render from fixtures instead of the stored downloads. */
    fixtureItems: List<DownloadItem>? = null,
) {
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchProgressState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completedItems.size) { DownloadsRepository.markCompletedSeen() }

    var selectedShowId by rememberSaveable(initialShowId) { mutableStateOf(initialShowId) }
    var deleteItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val speeds = rememberDownloadSpeeds(uiState.items)
    val watchStatusOf = remember(watchProgressState, watchedState) {
        { item: DownloadItem -> item.watchStatus(watchProgressState) }
    }
    val openFolderFailed = stringResource(Res.string.downloads_open_directory_failed)
    val items = fixtureItems ?: uiState.items
    val showId = selectedShowId
    val showEpisodes = remember(showId, items) {
        showId?.let { id -> items.filter { it.isEpisode && it.parentMetaId == id }.sortedForSeriesDownloads() }.orEmpty()
    }
    val closeShow: () -> Unit = { onBackFromShow?.invoke() ?: run { selectedShowId = null } }
    val goBack: () -> Unit = { if (showId != null) closeShow() else onBack() }

    // A show opened in place: Back and Esc return to all downloads first.
    val inPlaceShow = showId != null && onBackFromShow == null
    PlatformBackHandler(enabled = inPlaceShow, onBack = closeShow)
    val screenActive = LocalScreenActive.current
    OverlayEscapeHandler(enabled = inPlaceShow && screenActive) { closeShow() }

    val actionsFor: @Composable (DownloadItem) -> List<RedesignAction> = { item ->
        downloadActions(item, watchStatusOf(item), onOpenDownload, onOpenDetails) { deleteItemId = it.id }
    }

    // Ambient backdrop: one stable title, the show on its page, else the latest download.
    val backdropMode = LocalShellBackdropMode.current
    val backdrop = remember(backdropMode == ShellBackdropMode.Solid) {
        if (backdropMode != ShellBackdropMode.Solid) BackdropFocusController() else null
    }
    val backdropArt = (showEpisodes.firstOrNull() ?: items.maxByOrNull { it.createdAtEpochMs })
        ?.let { it.background ?: it.landscapeArtwork() }
    LaunchedEffect(backdrop, backdropArt) { backdrop?.base = backdropArt }

    val listState = rememberLazyListState()
    val scrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val topBarState = LocalDetailTopBarState.current
    LaunchedEffect(topBarState, backdrop) {
        if (topBarState == null) return@LaunchedEffect
        snapshotFlow { scrolled to backdrop?.tint }.collect { (isScrolled, tint) ->
            topBarState.scrolled = isScrolled
            topBarState.tint = tint
        }
    }

    Box(
        Modifier.fillMaxSize()
            .then(if (backdrop == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
            .testTag("downloads-desktop"),
    ) {
        if (backdrop != null) {
            Box(Modifier.fillMaxSize().navTransitionLayer(NavLayer.Background, NavSide.Above)) {
                ShellBackdrop(backdrop, showArtwork = backdropMode == ShellBackdropMode.Ambient)
            }
        }
        RedesignTypography(enabled = true) {
            BoxWithConstraints(
                Modifier.fillMaxSize().navTransitionLayer(NavLayer.Content, NavSide.Above)
                    .padding(start = LocalBackdropContentStartInset.current),
            ) {
                val gutter = desktopPageHorizontalPaddingForWidth((maxWidth + LocalBackdropContentStartInset.current).value)
                val contentWidth = maxWidth - gutter * 2
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val top = if (topBarState != null) statusBar + DesktopMergedTopBarHeight + 10.dp else statusBar + 16.dp
                Column(Modifier.fillMaxSize().padding(top = top)) {
                    DownloadsHeader(
                        items = items,
                        showEpisodes = showEpisodes.takeIf { showId != null },
                        watchStatusOf = watchStatusOf,
                        onBack = goBack,
                        onOpenFolder = {
                            if (!DownloadsPlatformDownloader.openDownloadsDirectory()) NuvioToastController.show(openFolderFailed)
                        },
                        modifier = Modifier.padding(horizontal = gutter),
                    )
                    Spacer(Modifier.height(18.dp))
                    AnimatedContent(
                        targetState = showId,
                        transitionSpec = {
                            (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 40 }) togetherWith
                                fadeOut(tween(180))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "downloads_view",
                    ) { shownShow ->
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().testTag(if (shownShow == null) "downloads-list" else "downloads-show"),
                            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            if (shownShow == null) {
                                downloadsRoot(items, speeds, watchStatusOf, contentWidth, actionsFor, onOpenDownload) { selectedShowId = it.parentMetaId }
                            } else {
                                downloadsShow(showEpisodes, speeds, watchStatusOf, actionsFor, onOpenDownload)
                            }
                        }
                    }
                }
            }
        }
    }

    val deleteItem = deleteItemId?.let { id -> items.firstOrNull { it.id == id } }
    if (deleteItem != null) {
        DeleteDownloadDialog(
            item = deleteItem,
            onConfirm = {
                DownloadsRepository.cancelDownload(deleteItem.id)
                deleteItemId = null
            },
            onDismiss = { deleteItemId = null },
        )
    }
}

@Composable
private fun DownloadsHeader(
    items: List<DownloadItem>,
    showEpisodes: List<DownloadItem>?,
    watchStatusOf: (DownloadItem) -> DownloadWatchStatus,
    onBack: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier,
) {
    val label = stringResource(Res.string.compose_settings_root_downloads_title)
    val title: String
    val kicker: String
    val subtitle: String?
    if (showEpisodes != null) {
        val episodes = showEpisodes.size
        val watched = showEpisodes.count { watchStatusOf(it).isWatched }
        title = showEpisodes.firstOrNull()?.title ?: stringResource(Res.string.downloads_show_downloads)
        kicker = listOf(
            label,
            pluralStringResource(Res.plurals.downloads_episodes, episodes, episodes),
            formatBytes(showEpisodes.sumOf { it.sizeOnDiskBytes }),
        ).joinToString(" · ")
        subtitle = stringResource(Res.string.downloads_watched_count, watched).takeIf { watched > 0 }
    } else {
        val titles = items.distinctBy { it.parentMetaId }.size
        title = label
        // The title already says Downloads; the kicker carries only the counts (none when empty).
        kicker = if (items.isEmpty()) "" else listOf(
            pluralStringResource(Res.plurals.downloads_titles, titles, titles),
            formatBytes(items.sumOf { it.sizeOnDiskBytes }),
        ).joinToString(" · ")
        val downloading = items.count { it.status == DownloadStatus.Downloading }
        val paused = items.count { it.status == DownloadStatus.Paused }
        val failed = items.count { it.status == DownloadStatus.Failed }
        val folder = rememberDownloadsFolderControls()
        subtitle = listOfNotNull(
            stringResource(Res.string.downloads_folder_saving_to, folder.displayPath).takeIf { DownloadsFolder.isSupported },
            pluralStringResource(Res.plurals.downloads_downloading_count, downloading, downloading).takeIf { downloading > 0 },
            pluralStringResource(Res.plurals.downloads_paused_count, paused, paused).takeIf { paused > 0 },
            pluralStringResource(Res.plurals.downloads_failed_count, failed, failed).takeIf { failed > 0 },
        ).joinToString(" · ").ifBlank { null }
    }
    val active = items.filter { it.status != DownloadStatus.Completed }
    val hasDownloading = active.any { it.status == DownloadStatus.Downloading }
    val hasPaused = active.any { it.status == DownloadStatus.Paused }
    Row(modifier.fillMaxWidth().testTag("downloads-header"), verticalAlignment = Alignment.Bottom) {
        // The 48dp button centres a 24dp glyph; pull it left so the arrow sits on the page edge.
        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier.offset(x = (-12).dp).padding(bottom = 30.dp).testTag("downloads-back"),
            containerColor = Color.Transparent,
            contentColor = RedesignColors.textPrimary,
            buttonSize = 48.dp,
            iconSize = 24.dp,
        )
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier.weight(1f),
            label = "downloads_title",
        ) { shown ->
            RedesignPageLead(title = shown, kicker = kicker, subtitle = subtitle)
        }
        Row(
            Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showEpisodes == null && active.size >= 2 && (hasDownloading || hasPaused)) {
                RedesignPillButton(
                    label = stringResource(if (hasDownloading) Res.string.downloads_pause_all else Res.string.downloads_resume_all),
                    onClick = {
                        if (hasDownloading) DownloadsRepository.pauseActiveDownloads() else DownloadsRepository.resumePausedDownloads()
                    },
                    leading = if (hasDownloading) Icons.Rounded.Pause else Icons.Rounded.Download,
                    modifier = Modifier.testTag("downloads-transfer-all"),
                )
            }
            if (DownloadsFolder.isSupported) {
                // Open the folder, or choose where new downloads go.
                RedesignOverflowButton(
                    contentDescription = stringResource(Res.string.downloads_folder_menu),
                    actions = downloadsFolderActions(rememberDownloadsFolderControls(), onOpenFolder),
                    vector = Icons.Outlined.Folder,
                    modifier = Modifier.testTag("downloads-folder"),
                )
            } else {
                RedesignIconButton(
                    contentDescription = stringResource(Res.string.downloads_open_directory),
                    onClick = onOpenFolder,
                    vector = Icons.Outlined.Folder,
                    modifier = Modifier.testTag("downloads-open-folder"),
                )
            }
        }
    }
}

// ─── Root ──────────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.downloadsRoot(
    items: List<DownloadItem>,
    speeds: Map<String, Long>,
    watchStatusOf: (DownloadItem) -> DownloadWatchStatus,
    contentWidth: Dp,
    actionsFor: @Composable (DownloadItem) -> List<RedesignAction>,
    onPlay: (DownloadItem) -> Unit,
    onOpenShow: (DownloadItem) -> Unit,
) {
    if (items.isEmpty()) {
        item(key = "downloads-empty") {
            RedesignMessageTile(
                title = stringResource(Res.string.downloads_empty_title),
                message = stringResource(Res.string.downloads_desktop_empty_message),
                icon = Icons.Rounded.Download,
                tag = "downloads-empty",
                modifier = downloadsItemModifier(),
            )
        }
        return
    }
    val active = items.filter { it.status != DownloadStatus.Completed }
        .sortedWith(compareBy<DownloadItem> { it.status.ordinal }.thenByDescending { it.createdAtEpochMs })
    if (active.isNotEmpty()) {
        item(key = "active-heading") { DownloadsSectionHeading(stringResource(Res.string.downloads_section_active), active.size, downloadsItemModifier()) }
        val last = active.lastIndex
        items(active.size, key = { "active:${active[it].id}" }) { index ->
            val item = active[index]
            DownloadListRow(
                item = item,
                shape = listRowShape(index, last),
                divider = index > 0,
                title = item.title,
                detail = item.episodeLabel(),
                status = activeStatusText(item, speeds[item.id]),
                progress = item.progressFraction,
                error = item.status == DownloadStatus.Failed,
                art = item.landscapeArtwork(),
                primary = transferAction(item),
                actions = actionsFor(item),
                modifier = downloadsItemModifier(),
            )
        }
    }
    val entries = items.downloadedEntries()
    if (entries.isNotEmpty()) {
        item(key = "downloaded-heading") {
            DownloadsSectionHeading(
                stringResource(Res.string.downloads_section_downloaded),
                entries.size,
                downloadsItemModifier().padding(top = if (active.isNotEmpty()) 28.dp else 0.dp),
            )
        }
        val columns = ((contentWidth + 16.dp) / (DownloadCardMinWidth + 16.dp)).toInt().coerceAtLeast(1)
        items(entries.chunked(columns), key = { row -> "downloaded:${row.first().key}" }) { row ->
            Row(downloadsItemModifier().fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { entry ->
                    when (entry) {
                        is DownloadedEntry.Movie -> DownloadCard(
                            art = entry.item.landscapeArtwork(),
                            title = entry.item.title,
                            meta = listOfNotNull(formatBytes(entry.item.sizeOnDiskBytes), watchStatusText(watchStatusOf(entry.item))).joinToString(" · "),
                            kicker = stringResource(Res.string.downloads_kind_movie),
                            watchProgress = watchStatusOf(entry.item).let { if (it.isWatched) 1f else it.progress },
                            onClick = { onPlay(entry.item) },
                            actions = actionsFor(entry.item),
                            tag = "downloads-card-${entry.item.id}",
                            modifier = Modifier.weight(1f),
                        )
                        is DownloadedEntry.Show -> {
                            val cover = entry.episodes.first()
                            DownloadCard(
                                art = cover.background ?: cover.landscapeArtwork(),
                                title = cover.title,
                                meta = listOfNotNull(
                                    pluralStringResource(Res.plurals.downloads_episodes, entry.episodes.size, entry.episodes.size),
                                    formatBytes(entry.episodes.sumOf { it.sizeOnDiskBytes }),
                                    pluralStringResource(Res.plurals.downloads_downloading_count, entry.downloadingCount, entry.downloadingCount)
                                        .takeIf { entry.downloadingCount > 0 },
                                ).joinToString(" · "),
                                kicker = stringResource(Res.string.downloads_kind_show),
                                watchProgress = entry.episodes.count { watchStatusOf(it).isWatched }.toFloat() / entry.episodes.size,
                                onClick = { onOpenShow(cover) },
                                actions = null,
                                tag = "downloads-show-${cover.parentMetaId}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadsShow(
    episodes: List<DownloadItem>,
    speeds: Map<String, Long>,
    watchStatusOf: (DownloadItem) -> DownloadWatchStatus,
    actionsFor: @Composable (DownloadItem) -> List<RedesignAction>,
    onPlay: (DownloadItem) -> Unit,
) {
    if (episodes.isEmpty()) {
        item(key = "show-empty") {
            RedesignMessageTile(
                title = stringResource(Res.string.downloads_empty_title),
                message = "",
                icon = Icons.Rounded.Download,
                tag = "downloads-empty",
                modifier = downloadsItemModifier(),
            )
        }
        return
    }
    episodes.groupBy { it.seasonNumber ?: 0 }.toList().sortedBy { it.first }.forEachIndexed { seasonIndex, (season, seasonEpisodes) ->
        item(key = "season:$season") {
            DownloadsSectionHeading(
                title = if (season == 0) stringResource(Res.string.episodes_specials) else stringResource(Res.string.episodes_season, season),
                count = null,
                detail = listOf(
                    pluralStringResource(Res.plurals.downloads_episodes, seasonEpisodes.size, seasonEpisodes.size),
                    formatBytes(seasonEpisodes.sumOf { it.sizeOnDiskBytes }),
                ).joinToString(" · "),
                modifier = downloadsItemModifier().padding(top = if (seasonIndex > 0) 28.dp else 0.dp),
            )
        }
        val last = seasonEpisodes.lastIndex
        items(seasonEpisodes.size, key = { "episode:${seasonEpisodes[it].id}" }) { index ->
            val item = seasonEpisodes[index]
            val completed = item.status == DownloadStatus.Completed
            val watch = watchStatusOf(item)
            DownloadListRow(
                item = item,
                shape = listRowShape(index, last),
                divider = index > 0,
                title = item.episodeDisplayTitle(),
                detail = null,
                status = if (completed) listOfNotNull(formatBytes(item.sizeOnDiskBytes), watchStatusText(watch)).joinToString(" · ")
                else activeStatusText(item, speeds[item.id]),
                progress = if (completed) (if (watch.isWatched) 1f else watch.progress).takeIf { it > 0f } else item.progressFraction,
                error = item.status == DownloadStatus.Failed,
                art = item.episodeThumbnail ?: item.landscapeArtwork(),
                primary = if (completed) PrimaryAction(Icons.Rounded.PlayArrow, stringResource(Res.string.action_play)) { onPlay(item) } else transferAction(item),
                actions = actionsFor(item),
                modifier = downloadsItemModifier(),
            )
        }
    }
}

/** Rows and cards fade and slide when transfers finish or are removed. */
private fun LazyItemScope.downloadsItemModifier(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(220),
    placementSpec = tween(260, easing = FastOutSlowInEasing),
    fadeOutSpec = tween(140),
)

// ─── Pieces ────────────────────────────────────────────────────────────────────────────────

private class PrimaryAction(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun transferAction(item: DownloadItem): PrimaryAction = when (item.status) {
    DownloadStatus.Downloading -> PrimaryAction(Icons.Rounded.Pause, stringResource(Res.string.compose_action_pause)) { item.toggleTransfer() }
    DownloadStatus.Paused -> PrimaryAction(Icons.Rounded.Download, stringResource(Res.string.action_resume)) { item.toggleTransfer() }
    DownloadStatus.Failed -> PrimaryAction(Icons.Rounded.Refresh, stringResource(Res.string.action_retry)) { item.toggleTransfer() }
    DownloadStatus.Completed -> PrimaryAction(Icons.Rounded.PlayArrow, stringResource(Res.string.action_play)) {}
}

@Composable
private fun downloadActions(
    item: DownloadItem,
    watch: DownloadWatchStatus,
    onPlay: (DownloadItem) -> Unit,
    onOpenDetails: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
): List<RedesignAction> = buildList {
    when (item.status) {
        DownloadStatus.Completed -> add(RedesignAction(stringResource(if (watch.progress > 0f) Res.string.action_resume else Res.string.action_play), Icons.Rounded.PlayArrow, tag = "downloads-action-play") { onPlay(item) })
        DownloadStatus.Downloading -> add(RedesignAction(stringResource(Res.string.compose_action_pause), Icons.Rounded.Pause, tag = "downloads-action-pause") { item.toggleTransfer() })
        DownloadStatus.Paused -> add(RedesignAction(stringResource(Res.string.action_resume), Icons.Rounded.Download, tag = "downloads-action-resume") { item.toggleTransfer() })
        DownloadStatus.Failed -> add(RedesignAction(stringResource(Res.string.action_retry), Icons.Rounded.Refresh, tag = "downloads-action-retry") { item.toggleTransfer() })
    }
    // Cloud files have no details page.
    if (item.parentMetaType != com.nuvio.app.features.cloud.CloudLibraryContentType) {
        add(RedesignAction(stringResource(Res.string.cw_action_go_to_details), Icons.Rounded.Info, tag = "downloads-action-details") { onOpenDetails(item) })
    }
    add(RedesignAction(stringResource(Res.string.action_delete), Icons.Rounded.DeleteOutline, destructive = true, tag = "downloads-action-delete") { onDelete(item) })
}

@Composable
private fun DownloadsSectionHeading(title: String, count: Int?, modifier: Modifier, detail: String? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = redesignText(20.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking))
        count?.let {
            Text(it.toString(), style = redesignText(16.sp, FontWeight.Medium, RedesignColors.textMuted), modifier = Modifier.padding(start = 8.dp, bottom = 1.dp))
        }
        Spacer(Modifier.weight(1f))
        detail?.let { Text(it, style = redesignText(13.sp, color = RedesignColors.textMuted)) }
    }
}

/** List rows read as one frosted tile: only the first and last round their outer corners. */
private fun listRowShape(index: Int, last: Int): Shape {
    val radius = 18.dp
    return RoundedCornerShape(
        topStart = if (index == 0) radius else 0.dp,
        topEnd = if (index == 0) radius else 0.dp,
        bottomStart = if (index == last) radius else 0.dp,
        bottomEnd = if (index == last) radius else 0.dp,
    )
}

@Composable
private fun DownloadListRow(
    item: DownloadItem,
    shape: Shape,
    divider: Boolean,
    title: String,
    detail: String?,
    status: String,
    progress: Float?,
    error: Boolean,
    art: String?,
    primary: PrimaryAction,
    actions: List<RedesignAction>,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) RedesignColors.fillStrong else RedesignColors.fill, tween(160), label = "download_row_fill")
    val menu = remember { MutableTransitionState(false) }
    Column(
        modifier.fillMaxWidth().clip(shape).background(fill).hoverable(interaction)
            .secondaryClick { menu.targetState = true }
            .mousePressAndHoldAt { menu.targetState = true }
            .testTag("downloads-row-${item.id}"),
    ) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DownloadArt(art, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f)) {
                Text(title, style = redesignText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let {
                    Text(it, style = redesignText(13.sp, color = RedesignColors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    if (error) item.errorMessage?.takeIf { it.isNotBlank() } ?: status else status,
                    style = redesignText(12.sp, color = if (error) com.nuvio.app.core.ui.RedesignDanger else RedesignColors.textMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (progress != null) {
                    DownloadProgressLine(progress, Modifier.padding(top = 8.dp).fillMaxWidth(0.6f))
                }
            }
            RedesignIconButton(contentDescription = primary.label, onClick = primary.onClick, modifier = Modifier.testTag("downloads-primary-${item.id}")) { tint ->
                Icon(primary.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            RedesignOverflowButton(
                contentDescription = stringResource(Res.string.details_actions_menu_label),
                actions = actions,
                modifier = Modifier.testTag("downloads-more-${item.id}"),
                menu = menu,
            )
        }
    }
}

@Composable
private fun DownloadCard(
    art: String?,
    title: String,
    meta: String,
    kicker: String,
    watchProgress: Float,
    onClick: () -> Unit,
    actions: List<RedesignAction>?,
    tag: String,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(if (hovered) RedesignColors.hairlineFocused else RedesignColors.hairline, tween(180), label = "download_card_border")
    val menu = remember { MutableTransitionState(false) }
    val openMenu: (() -> Unit)? = actions?.let { { menu.targetState = true } }
    Box(
        modifier.aspectRatio(16f / 9f).clip(shape).background(RedesignColors.fill).border(1.dp, border, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .secondaryClick(openMenu)
            .mousePressAndHoldAt(openMenu?.let { open -> { _ -> open() } })
            .testTag(tag),
    ) {
        DownloadArt(art, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            com.nuvio.app.core.ui.RedesignKicker(kicker)
            Text(title, style = redesignText(17.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = redesignText(12.5.sp, color = RedesignColors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (watchProgress > 0f) {
            DownloadProgressLine(watchProgress, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp))
        }
        if (actions != null) {
            RedesignOverflowButton(
                contentDescription = stringResource(Res.string.details_actions_menu_label),
                actions = actions,
                size = 32.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).testTag("$tag-more"),
                menu = menu,
            )
        }
    }
}

@Composable
private fun DownloadArt(url: String?, modifier: Modifier) {
    Box(modifier.background(RedesignColors.fill), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Rounded.Movie, contentDescription = null, tint = RedesignColors.textMuted, modifier = Modifier.size(22.dp))
        } else {
            NuvioAsyncImage(url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun DownloadProgressLine(progress: Float, modifier: Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(RedesignColors.textPrimary))
    }
}
