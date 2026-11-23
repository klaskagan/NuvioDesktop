package com.nuvio.app.features.library

import com.nuvio.app.core.ui.RedesignMessageTile
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.core.poster.withCustomPosterUrl
import com.nuvio.app.core.ui.DisintegratingContainer
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.RedesignIconButton
import com.nuvio.app.core.ui.RedesignKicker
import com.nuvio.app.core.ui.RedesignMenuButton
import com.nuvio.app.core.ui.RedesignMenuOption
import com.nuvio.app.core.ui.RedesignPillButton
import com.nuvio.app.core.ui.RedesignPillGroup
import com.nuvio.app.core.ui.RedesignTightTracking
import com.nuvio.app.core.ui.RedesignTileShape
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.components.HomeRowLeadCard
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.features.home.components.PosterGridSkeletonRow
import com.nuvio.app.features.watching.application.WatchingState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val BoardHeight = 260.dp

/** List pills shown before the rest move into a "More" menu. */
private const val VisibleListPills = 5

// ─── Shelves ────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LibraryShelvesPane(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    lead: LibrarySpotlight?,
    state: LazyListState,
    gutter: Dp,
) {
    val ui = model.uiState
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("library-shelves"),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            !ui.isLoaded || (ui.isLoading && ui.sections.isEmpty()) -> {
                item(key = "library-board-placeholder") {
                    LibraryBoardPlaceholder(libraryDesktopItemModifier().padding(horizontal = gutter))
                }
                items(2, key = { "library-shelf-placeholder:$it" }) {
                    HomeSkeletonRow(libraryDesktopItemModifier().padding(top = 18.dp), horizontalPadding = gutter)
                }
            }
            !ui.errorMessage.isNullOrBlank() && ui.sections.isEmpty() -> item(key = "library-problem") {
                Box(libraryDesktopItemModifier()) { LibraryLoadProblem(model, actions, gutter) }
            }
            ui.sections.isEmpty() -> item(key = "library-empty") {
                Box(libraryDesktopItemModifier()) { LibraryEmptyLibrary(model, actions, gutter) }
            }
            else -> {
                if (lead != null) {
                    item(key = "library-board") {
                        LibraryBoard(
                            lead = lead,
                            sections = ui.sections,
                            posterPattern = model.posterPattern,
                            actions = actions,
                            modifier = libraryDesktopItemModifier().padding(horizontal = gutter),
                        )
                    }
                }
                libraryDesktopShelves(model, actions, gutter)
            }
        }
    }
}

private fun LazyListScope.libraryDesktopShelves(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    gutter: Dp,
) {
    items(model.displaySections, key = { section -> "library-shelf:${section.type}" }) { section ->
        val style = rememberPosterCardStyleUiState()
        val leadHeight = desktopCatalogShelfPosterBaseWidthDp(style.widthDp).dp *
            (style.heightDp.toFloat() / style.widthDp.coerceAtLeast(1))
        val leadKey = section.previewEntries.firstOrNull()
            ?.takeIf { !it.exiting && !style.catalogLandscapeModeEnabled && it.item.posterShape == PosterShape.Poster }
            ?.globalKey
        val count = section.source?.items?.size ?: section.previewEntries.size
        val open = section.source?.let { source -> { actions.openList(source) } }
        NuvioShelfSection(
            title = localizedLibrarySectionTitle(section.type, section.displayTitle),
            entries = section.previewEntries,
            modifier = libraryDesktopItemModifier().padding(top = 18.dp).testTag("library-shelf-${section.type}"),
            headerHorizontalPadding = gutter,
            rowContentPadding = PaddingValues(horizontal = gutter),
            onViewAllClick = open,
            onTitleClick = open,
            viewAllPillSize = NuvioViewAllPillSize.Compact,
            eyebrow = pluralStringResource(Res.plurals.library_title_count, count, count),
            key = { entry -> entry.globalKey },
            animatePlacement = true,
        ) { entry ->
            val item = entry.item
            val preview = remember(item, model.posterPattern) {
                item.toMetaPreview().let { if (model.posterPattern.isNotBlank()) it.withCustomPosterUrl(model.posterPattern) else it }
            }
            val entrySection = entry.section
            val onClick = if (entry.exiting) null else actions.onPosterClick?.let { { it(item) } }
            val onLongClick = if (entry.exiting || entrySection == null) null else actions.onPosterLongClick?.let { { it(item, entrySection) } }
            DisintegratingContainer(
                disintegrating = entry.exiting,
                onDisintegrated = { actions.onDisintegrated(entry.globalKey) },
            ) {
                if (entry.globalKey == leadKey) {
                    HomeRowLeadCard(item = preview, width = leadHeight * (16f / 9f), height = leadHeight, onClick = onClick, onLongClick = onLongClick)
                } else {
                    HomePosterCard(
                        item = preview,
                        isWatched = WatchingState.isPosterWatched(model.watchedKeys, preview, model.fullyWatchedSeriesKeys),
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                }
            }
        }
    }
}

// ─── Board ──────────────────────────────────────────────────────────────────────────────────

/** "Recently saved" lead tile beside "Your lists" (each list opens in place in the grid). */
@Composable
private fun LibraryBoard(
    lead: LibrarySpotlight,
    sections: List<LibrarySection>,
    posterPattern: String,
    actions: LibraryDesktopActions,
    modifier: Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(BoardHeight).testTag("library-board"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1.75f).fillMaxSize()) {
            val preview = remember(lead.item, posterPattern) {
                lead.item.toMetaPreview().let { if (posterPattern.isNotBlank()) it.withCustomPosterUrl(posterPattern) else it }
            }
            HomeRowLeadCard(
                item = preview,
                width = maxWidth,
                height = maxHeight,
                onClick = actions.onPosterClick?.let { { it(lead.item) } },
                onLongClick = actions.onPosterLongClick?.let { { it(lead.item, lead.section) } },
                modifier = Modifier.testTag("library-board-lead"),
            )
            RedesignKicker(
                stringResource(Res.string.library_board_recent) + " · " +
                    localizedLibrarySectionTitle(lead.section.type, lead.section.displayTitle),
                Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp, end = 64.dp),
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.onPlayClick?.let { play ->
                    // A movie plays; a show opens Details, which knows the next episode.
                    val movie = lead.item.type == "movie"
                    RedesignPillButton(
                        label = stringResource(if (movie) Res.string.action_play else Res.string.library_board_episodes),
                        onClick = { play(lead.item) },
                        primary = true,
                        leading = if (movie) Icons.Rounded.PlayArrow else null,
                        modifier = Modifier.testTag("library-board-play"),
                    )
                }
                actions.onPosterLongClick?.let { open ->
                    RedesignIconButton(
                        contentDescription = stringResource(Res.string.library_board_actions),
                        onClick = { open(lead.item, lead.section) },
                        vector = Icons.Rounded.MoreHoriz,
                        modifier = Modifier.testTag("library-board-actions"),
                    )
                }
            }
        }
        LibraryListsTile(sections, actions.openList, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun LibraryListsTile(
    sections: List<LibrarySection>,
    onOpen: (LibrarySection) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.clip(RedesignTileShape).background(RedesignColors.fill)
            .border(1.dp, RedesignColors.hairline, RedesignTileShape)
            .testTag("library-lists-tile"),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.library_board_lists),
                style = redesignText(15.sp, FontWeight.Bold, letterSpacing = RedesignTightTracking),
                modifier = Modifier.weight(1f),
            )
            Text(
                pluralStringResource(Res.plurals.library_list_count, sections.size, sections.size),
                style = redesignText(12.sp, FontWeight.Medium, RedesignColors.textMuted),
            )
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            sections.forEach { section -> LibraryListRow(section, onOpen) }
        }
    }
}

@Composable
private fun LibraryListRow(section: LibrarySection, onOpen: (LibrarySection) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (hovered) RedesignColors.fill else androidx.compose.ui.graphics.Color.Transparent, tween(160), label = "list_row_fill")
    Row(
        Modifier.fillMaxWidth().background(fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onOpen(section) }
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("library-list-row-${section.type}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Up to three overlapping mini posters.
        Box(Modifier.width(48.dp).height(36.dp)) {
            section.items.take(3).forEachIndexed { index, item ->
                Box(
                    Modifier.offset(x = (index * 13).dp).size(width = 24.dp, height = 36.dp)
                        .clip(RoundedCornerShape(4.dp)).background(RedesignColors.fillStrong)
                        .border(1.dp, RedesignColors.hairline, RoundedCornerShape(4.dp)),
                ) {
                    NuvioAsyncImage(item.poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            localizedLibrarySectionTitle(section.type, section.displayTitle),
            style = redesignText(14.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(section.items.size.toString(), style = redesignText(13.sp, FontWeight.Medium, RedesignColors.textMuted))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = if (hovered) RedesignColors.textPrimary else RedesignColors.textMuted,
            modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

/** The board's first-load shape: the same two tiles, blank, so nothing moves when data lands. */
@Composable
private fun LibraryBoardPlaceholder(modifier: Modifier) {
    Row(modifier.fillMaxWidth().height(BoardHeight), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(1.75f, 1f).forEach { weight ->
            Box(
                Modifier.weight(weight).fillMaxSize().clip(RedesignTileShape).background(RedesignColors.fill)
                    .border(1.dp, RedesignColors.hairline, RedesignTileShape),
            )
        }
    }
}

// ─── Grid ───────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LibraryGridPane(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    state: LazyListState,
    gutter: Dp,
    columns: Int,
) {
    val ui = model.uiState
    val projection = model.projection
    // A different list, type or filter starts at the top of its grid.
    LaunchedEffect(projection.selectedSectionKey, projection.selectedType, projection.selectedGenre, projection.selectedYear, model.watchedFilter, model.sortOption) {
        if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) state.scrollToItem(0)
    }
    Column(Modifier.fillMaxSize()) {
        val ready = ui.isLoaded && ui.sections.isNotEmpty()
        LibraryGridControls(model, actions, enabled = ready, modifier = Modifier.padding(horizontal = gutter))
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("library-grid"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                !ui.isLoaded || (ui.isLoading && ui.sections.isEmpty()) -> items(2, key = { "library-grid-placeholder:$it" }) {
                    PosterGridSkeletonRow(columns = columns, modifier = libraryDesktopItemModifier().padding(horizontal = gutter))
                }
                !ui.errorMessage.isNullOrBlank() && ui.sections.isEmpty() -> item(key = "library-problem") {
                    Box(libraryDesktopItemModifier()) { LibraryLoadProblem(model, actions, gutter) }
                }
                ui.sections.isEmpty() -> item(key = "library-empty") {
                    Box(libraryDesktopItemModifier()) { LibraryEmptyLibrary(model, actions, gutter) }
                }
                projection.entries.isEmpty() -> item(key = "library-grid-no-matches") {
                    RedesignMessageTile(tag = "library-message", 
                        title = stringResource(Res.string.library_grid_no_matches_title),
                        message = stringResource(Res.string.library_grid_no_matches_message),
                        modifier = libraryDesktopItemModifier().padding(horizontal = gutter),
                        icon = Icons.Rounded.SearchOff,
                    )
                }
                else -> items(
                    items = projection.entries.chunked(columns),
                    key = { row -> "library-grid:${row.first().item.type}:${row.first().item.id}" },
                ) { row ->
                    val previews = remember(row, model.posterPattern) {
                        row.map { entry ->
                            entry.item.toMetaPreview().let { if (model.posterPattern.isNotBlank()) it.withCustomPosterUrl(model.posterPattern) else it }
                        }
                    }
                    PosterGridRow(
                        items = previews,
                        columns = columns,
                        modifier = libraryDesktopItemModifier().padding(horizontal = gutter),
                        watchedKeys = model.watchedKeys,
                        fullyWatchedSeriesKeys = model.fullyWatchedSeriesKeys,
                        onPosterClick = actions.onPosterClick?.let { open ->
                            { preview -> row.firstOrNull { it.item.id == preview.id && it.item.type == preview.type }?.item?.let(open) }
                        },
                        onPosterLongClick = actions.onPosterLongClick?.let { open ->
                            { preview -> row.firstOrNull { it.item.id == preview.id && it.item.type == preview.type }?.let { open(it.item, it.section) } }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The grid's two rows of controls: lists (pills, the rest under More) with Sort on the right, then
 * type pills and the Genre / Year / Watched menus. Rows keep their height while data loads.
 */
@Composable
private fun LibraryGridControls(
    model: LibraryDesktopModel,
    actions: LibraryDesktopActions,
    enabled: Boolean,
    modifier: Modifier,
) {
    val projection = model.projection
    val sourceMode = model.uiState.sourceMode
    val allLabel = stringResource(Res.string.library_type_all)
    Column(modifier.fillMaxWidth().testTag("library-grid-controls"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.openedListKey != null) LibraryCloseListButton(actions.closeList)
            val sections = projection.availableSections
            if (enabled && sections.size > 1) {
                val selected = sections.firstOrNull { it.type == projection.selectedSectionKey } ?: sections.first()
                val visible = sections.take(VisibleListPills).let { head ->
                    if (selected in head) head else head.take(VisibleListPills - 1) + selected
                }
                val overflow = sections.filter { it !in visible }
                RedesignPillGroup(
                    options = visible,
                    selected = selected,
                    label = { "${localizedLibrarySectionTitle(it.type, it.displayTitle)}  ${it.items.size}" },
                    onSelect = { actions.selectSection(it.type) },
                    optionTag = { "library-list-pill-${it.type}" },
                )
                if (overflow.isNotEmpty()) {
                    RedesignMenuButton(
                        label = stringResource(Res.string.library_lists_more),
                        options = overflow.map { RedesignMenuOption(it.type, localizedLibrarySectionTitle(it.type, it.displayTitle), it.items.size) },
                        selected = null,
                        onSelect = actions.selectSection,
                        tag = "library-lists-more",
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (enabled) {
                val sortOptions = availableLibrarySortOptions(sourceMode)
                RedesignMenuButton(
                    label = librarySortOptionLabel(model.sortOption),
                    options = sortOptions.map { RedesignMenuOption(it, librarySortOptionLabel(it)) },
                    selected = model.sortOption,
                    onSelect = actions.selectSort,
                    alignEnd = true,
                    tag = "library-sort",
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!enabled) return@Row
            if (projection.availableTypes.size > 1) {
                val types = listOf<String?>(null) + projection.availableTypes
                RedesignPillGroup(
                    options = types,
                    selected = projection.selectedType,
                    label = { type -> type?.let(::localizedMediaTypeLabel) ?: allLabel },
                    onSelect = actions.selectType,
                    optionTag = { "library-type-${it ?: "all"}" },
                )
            }
            if (projection.availableGenres.isNotEmpty()) {
                RedesignMenuButton(
                    label = projection.selectedGenre ?: allLabel,
                    prefix = stringResource(Res.string.library_filter_genre),
                    options = listOf(RedesignMenuOption<String?>(null, allLabel)) +
                        projection.availableGenres.map { RedesignMenuOption<String?>(it.key, it.key, it.count) },
                    selected = projection.selectedGenre,
                    onSelect = actions.selectGenre,
                    active = projection.selectedGenre != null,
                    onClear = { actions.selectGenre(null) },
                    tag = "library-genre",
                )
            }
            if (projection.availableYears.isNotEmpty()) {
                RedesignMenuButton(
                    label = projection.selectedYear ?: allLabel,
                    prefix = stringResource(Res.string.library_filter_year),
                    options = listOf(RedesignMenuOption<String?>(null, allLabel)) +
                        projection.availableYears.map { RedesignMenuOption<String?>(it.key, it.key, it.count) },
                    selected = projection.selectedYear,
                    onSelect = actions.selectYear,
                    active = projection.selectedYear != null,
                    onClear = { actions.selectYear(null) },
                    tag = "library-year",
                )
            }
            RedesignMenuButton(
                label = libraryWatchedFilterLabel(model.watchedFilter),
                prefix = stringResource(Res.string.library_filter_watched),
                options = LibraryWatchedFilter.entries.map { RedesignMenuOption(it, libraryWatchedFilterLabel(it)) },
                selected = model.watchedFilter,
                onSelect = actions.selectWatched,
                active = model.watchedFilter != LibraryWatchedFilter.ALL,
                onClear = { actions.selectWatched(LibraryWatchedFilter.ALL) },
                tag = "library-watched",
            )
        }
    }
}

