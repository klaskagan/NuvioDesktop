package com.nuvio.app.features.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.OverlayEscapeHandler
import com.nuvio.app.core.ui.RedesignColors
import com.nuvio.app.core.ui.LocalRedesignMenuSurface
import com.nuvio.app.core.ui.RedesignPillMenuShape
import com.nuvio.app.core.ui.RedesignPillMenuMinWidth
import com.nuvio.app.core.ui.redesignText
import com.nuvio.app.features.home.MetaPreview
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.action_clear
import nuvio.composeapp.generated.resources.library_grid_showing
import nuvio.composeapp.generated.resources.search_titles_count
import nuvio.composeapp.generated.resources.detail_facts_rating
import nuvio.composeapp.generated.resources.seasonal_all_genres
import nuvio.composeapp.generated.resources.seasonal_all_years
import nuvio.composeapp.generated.resources.seasonal_any_rating
import nuvio.composeapp.generated.resources.seasonal_any_watch_state
import nuvio.composeapp.generated.resources.seasonal_genre_filter
import nuvio.composeapp.generated.resources.seasonal_sort_filter
import nuvio.composeapp.generated.resources.seasonal_sort_highest
import nuvio.composeapp.generated.resources.seasonal_sort_newest
import nuvio.composeapp.generated.resources.seasonal_sort_oldest
import nuvio.composeapp.generated.resources.seasonal_sort_popular
import nuvio.composeapp.generated.resources.seasonal_sort_title
import nuvio.composeapp.generated.resources.seasonal_unwatched
import nuvio.composeapp.generated.resources.seasonal_watched
import nuvio.composeapp.generated.resources.seasonal_watched_filter
import nuvio.composeapp.generated.resources.seasonal_year_filter
import nuvio.composeapp.generated.resources.streaming_grid_in_progress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How a service row's grid orders its titles. */
internal enum class GridSort(val label: StringResource) {
    Popular(Res.string.seasonal_sort_popular),
    TopRated(Res.string.seasonal_sort_highest),
    Newest(Res.string.seasonal_sort_newest),
    Oldest(Res.string.seasonal_sort_oldest),
    Title(Res.string.seasonal_sort_title),
    ;

    companion object {
        /** The row's own order: Popular for popular and originals rows, and so on. */
        fun forShelf(sort: SearchShelfSort): GridSort = when (sort) {
            SearchShelfSort.TopRated -> TopRated
            SearchShelfSort.New -> Newest
            else -> Popular
        }
    }
}

/** Watch status: unwatched, in progress and watched (there is no separate Hide watched switch). */
internal enum class GridWatch(val label: StringResource) {
    Unwatched(Res.string.seasonal_unwatched),
    InProgress(Res.string.streaming_grid_in_progress),
    Watched(Res.string.seasonal_watched),
}

/**
 * The menus of a service row's grid (the shared pinned bar's order: Sort, Genre, Year, Rating,
 * Watch status). They work on the titles loaded so far; the grid keeps loading pages as you scroll.
 */
@Immutable
internal data class StreamingGridFilters(
    val sort: GridSort,
    val defaultSort: GridSort,
    val genres: Set<Int> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val minRating: Int? = null,
    val watch: Set<GridWatch> = emptySet(),
) {
    val sortChanged: Boolean get() = sort != defaultSort
    val isActive: Boolean get() = sortChanged || genres.isNotEmpty() || decades.isNotEmpty() || minRating != null || watch.isNotEmpty()
    /** Choices that narrow the grid; a changed sort alone keeps every title. */
    val narrows: Boolean get() = genres.isNotEmpty() || decades.isNotEmpty() || minRating != null || watch.isNotEmpty()

    fun cleared(): StreamingGridFilters = StreamingGridFilters(sort = defaultSort, defaultSort = defaultSort)

    fun apply(items: List<MetaPreview>, watchOf: (MetaPreview) -> GridWatch): List<MetaPreview> {
        val kept = items.filter { item ->
            (genres.isEmpty() || item.tmdbGenreIds.any(genres::contains)) &&
                (decades.isEmpty() || item.releaseYear()?.let { decadeOf(it) in decades } == true) &&
                (minRating == null || (item.sortRating ?: 0.0) >= minRating) &&
                (watch.isEmpty() || watchOf(item) in watch)
        }
        return when (sort) {
            GridSort.Popular -> kept.sortedByDescending { it.popularity ?: -1.0 }
            GridSort.TopRated -> kept.sortedByDescending { it.sortRating ?: -1.0 }
            GridSort.Newest -> kept.sortedByDescending { it.rawReleaseDate.orEmpty() }
            GridSort.Oldest -> kept.sortedBy { it.rawReleaseDate ?: "9999" }
            GridSort.Title -> kept.sortedBy { it.name.lowercase() }
        }
    }
}

internal fun decadeOf(year: Int): Int = year / 10 * 10

/**
 * Genres on the loaded titles, named from TMDB's fixed genre list, A–Z: the list stays in place
 * while more pages load under an open menu (most-common-first reordered it as you looked).
 */
internal fun gridGenreOptions(items: List<MetaPreview>): List<Pair<Int, String>> =
    items.flatMap { it.tmdbGenreIds }.distinct()
        .mapNotNull { id -> tmdbGenreName(id)?.let { id to it } }
        .distinctBy { it.second }
        .sortedBy { it.second.lowercase() }

/** Decades the loaded titles come from, newest first. */
internal fun gridDecadeOptions(items: List<MetaPreview>): List<Int> =
    items.mapNotNull { it.releaseYear()?.let(::decadeOf) }.distinct().sortedDescending()

internal val GridRatingOptions = listOf(9, 8, 7, 6, 5)

/** TMDB's movie and TV genre ids (both lists; the ids never change). */
internal fun tmdbGenreName(id: Int): String? = when (id) {
    28 -> "Action"
    12 -> "Adventure"
    16 -> "Animation"
    35 -> "Comedy"
    80 -> "Crime"
    99 -> "Documentary"
    18 -> "Drama"
    10751 -> "Family"
    14 -> "Fantasy"
    36 -> "History"
    27 -> "Horror"
    10402 -> "Music"
    9648 -> "Mystery"
    10749 -> "Romance"
    878 -> "Science Fiction"
    10770 -> "TV Movie"
    53 -> "Thriller"
    10752 -> "War"
    37 -> "Western"
    10759 -> "Action & Adventure"
    10762 -> "Kids"
    10763 -> "News"
    10764 -> "Reality"
    10765 -> "Sci-Fi & Fantasy"
    10766 -> "Soap"
    10767 -> "Talk"
    10768 -> "War & Politics"
    else -> null
}

/** One choice in a filter menu; [selected] draws its tick. */
internal class GridMenuOption(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * A 36 dp pill that always shows its menu's name, so pills never change width; once set it fills
 * and gets a small badge (choices made). It opens a frosted list under itself; Esc or a click
 * outside closes it. [keepOpen] keeps multi-choice menus open while ticking.
 */
@Composable
internal fun GridFilterMenu(
    name: String,
    badge: Int?,
    active: Boolean,
    options: List<GridMenuOption>,
    tag: String,
    keepOpen: Boolean = false,
) {
    val menu = remember { MutableTransitionState(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val open = menu.targetState
    val shape = RoundedCornerShape(999.dp)
    val fill by animateColorAsState(
        when {
            active || open -> Color.White.copy(alpha = 0.14f)
            hovered -> Color.White.copy(alpha = 0.10f)
            else -> RedesignColors.fill
        },
        tween(140),
        label = "grid_menu_fill",
    )
    val outline by animateColorAsState(if (active) Color.White.copy(alpha = 0.30f) else RedesignColors.hairline, tween(140), label = "grid_menu_outline")
    // The menu is never narrower than its pill ("Dropdown · spec" in the Home design).
    var pillWidthPx by remember { mutableIntStateOf(0) }
    Box(Modifier.onSizeChanged { pillWidthPx = it.width }) {
        Row(
            Modifier.height(36.dp).clip(shape).background(fill).border(1.dp, outline, shape)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { menu.targetState = !menu.targetState }
                .padding(start = 16.dp, end = 12.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(name, style = redesignText(13.sp, FontWeight.SemiBold), maxLines = 1)
            if (badge != null && badge > 0) {
                Box(
                    Modifier.defaultMinSize(minWidth = 18.dp).height(18.dp).clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFF3F4F6)).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge.toString(), style = redesignText(11.sp, FontWeight.Bold, Color(0xFF0A0B0D)))
                }
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = RedesignColors.textSecondary, modifier = Modifier.size(16.dp))
        }
        if (menu.currentState || menu.targetState) {
            val dismiss = { menu.targetState = false }
            OverlayEscapeHandler { dismiss() }
            val offsetY = with(LocalDensity.current) { 42.dp.roundToPx() }
            Popup(alignment = Alignment.TopStart, offset = IntOffset(0, offsetY), onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
                AnimatedVisibility(
                    visibleState = menu,
                    enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f, transformOrigin = TransformOrigin(0f, 0f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.97f, transformOrigin = TransformOrigin(0f, 0f)),
                ) {
                    val panel = RedesignPillMenuShape
                    val minWidth = with(LocalDensity.current) { pillWidthPx.toDp() }.coerceIn(RedesignPillMenuMinWidth, 280.dp)
                    Column(
                        Modifier.widthIn(min = minWidth, max = 280.dp).heightIn(max = 380.dp).clip(panel).background(LocalRedesignMenuSurface.current)
                            .border(1.dp, RedesignColors.hairline, panel).verticalScroll(rememberScrollState()).padding(6.dp)
                            .testTag("${tag}_menu"),
                    ) {
                        options.forEach { option -> GridMenuRow(option) { option.onClick(); if (!keepOpen) dismiss() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridMenuRow(option: GridMenuOption, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            option.selected -> Color.White.copy(alpha = 0.10f)
            hovered -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        tween(140),
        label = "grid_menu_row",
    )
    val ink = if (option.selected || hovered) RedesignColors.textPrimary else RedesignColors.textSecondary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(option.label, style = redesignText(13.sp, if (option.selected) FontWeight.SemiBold else FontWeight.Medium, ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(16.dp)) {
            if (option.selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = RedesignColors.textPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** The five menus in the shared bar's order, with their choices. */
@Composable
internal fun StreamingGridMenus(
    filters: StreamingGridFilters,
    items: List<MetaPreview>,
    onChange: (StreamingGridFilters) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GridFilterMenu(
            name = stringResource(Res.string.seasonal_sort_filter),
            badge = if (filters.sortChanged) 1 else null,
            active = filters.sortChanged,
            options = GridSort.entries.map { sort -> GridMenuOption(stringResource(sort.label), filters.sort == sort) { onChange(filters.copy(sort = sort)) } },
            tag = "grid_menu_sort",
        )
        val genres = remember(items) { gridGenreOptions(items) }
        GridFilterMenu(
            name = stringResource(Res.string.seasonal_genre_filter),
            badge = filters.genres.size,
            active = filters.genres.isNotEmpty(),
            options = listOf(GridMenuOption(stringResource(Res.string.seasonal_all_genres), filters.genres.isEmpty()) { onChange(filters.copy(genres = emptySet())) }) +
                genres.map { (id, label) -> GridMenuOption(label, id in filters.genres) { onChange(filters.copy(genres = filters.genres.toggle(id))) } },
            tag = "grid_menu_genre",
            keepOpen = true,
        )
        val decades = remember(items) { gridDecadeOptions(items) }
        GridFilterMenu(
            name = stringResource(Res.string.seasonal_year_filter),
            badge = filters.decades.size,
            active = filters.decades.isNotEmpty(),
            options = listOf(GridMenuOption(stringResource(Res.string.seasonal_all_years), filters.decades.isEmpty()) { onChange(filters.copy(decades = emptySet())) }) +
                decades.map { decade -> GridMenuOption("${decade}s", decade in filters.decades) { onChange(filters.copy(decades = filters.decades.toggle(decade))) } },
            tag = "grid_menu_year",
            keepOpen = true,
        )
        GridFilterMenu(
            name = stringResource(Res.string.detail_facts_rating),
            badge = if (filters.minRating != null) 1 else null,
            active = filters.minRating != null,
            options = listOf(GridMenuOption(stringResource(Res.string.seasonal_any_rating), filters.minRating == null) { onChange(filters.copy(minRating = null)) }) +
                GridRatingOptions.map { min -> GridMenuOption("★ $min+", filters.minRating == min) { onChange(filters.copy(minRating = min)) } },
            tag = "grid_menu_rating",
        )
        GridFilterMenu(
            name = stringResource(Res.string.seasonal_watched_filter),
            badge = filters.watch.size,
            active = filters.watch.isNotEmpty(),
            options = listOf(GridMenuOption(stringResource(Res.string.seasonal_any_watch_state), filters.watch.isEmpty()) { onChange(filters.copy(watch = emptySet())) }) +
                GridWatch.entries.map { state -> GridMenuOption(stringResource(state.label), state in filters.watch) { onChange(filters.copy(watch = filters.watch.toggle(state))) } },
            tag = "grid_menu_watch",
            keepOpen = true,
        )
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/**
 * A service row's grid bar (the shared pinned bar's layout): Back, the service's logo, a hairline,
 * the row's name and its count ("12 of 48 titles" while filtering), a hairline, the menus, then a
 * reserved slot for Clear on the right so nothing shifts when it appears.
 */
@Composable
internal fun StreamingGridBar(
    rowName: String,
    shown: Int,
    loaded: Int,
    filters: StreamingGridFilters?,
    loadedItems: List<MetaPreview>,
    onFiltersChange: (StreamingGridFilters) -> Unit,
    onBack: () -> Unit,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag("streaming_grid_bar"), verticalAlignment = Alignment.CenterVertically) {
        com.nuvio.app.core.ui.RedesignIconButton(
            contentDescription = stringResource(Res.string.action_back),
            onClick = onBack,
            vector = Icons.AutoMirrored.Rounded.ArrowBack,
            size = 40.dp,
            modifier = Modifier.testTag("streaming_grid_back"),
        )
        Spacer(Modifier.width(14.dp))
        logo()
        GridBarHairline()
        Text(
            rowName,
            style = redesignText(16.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp).testTag("streaming_grid_title"),
        )
        Spacer(Modifier.width(10.dp))
        // A slot sized for the widest form ("999 of 999 titles") in tabular figures, so filtering
        // or loading more never pushes the menus.
        Box(Modifier.width(GridCountSlot), contentAlignment = Alignment.CenterStart) {
            if (loaded > 0) {
                val narrowed = filters?.narrows == true
                Text(
                    if (narrowed) stringResource(Res.string.library_grid_showing, shown, loaded)
                    else stringResource(Res.string.search_titles_count, loaded),
                    style = redesignText(13.sp, color = RedesignColors.textMuted).copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    modifier = Modifier.testTag("streaming_grid_count"),
                )
            }
        }
        if (filters != null) {
            GridBarHairline()
            StreamingGridMenus(filters, loadedItems, onFiltersChange)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
            androidx.compose.animation.AnimatedVisibility(visible = filters?.isActive == true, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
                SearchTextAction(stringResource(Res.string.action_clear), onClick = { filters?.let { onFiltersChange(it.cleared()) } })
            }
        }
    }
}

private val GridCountSlot = 118.dp

@Composable
private fun GridBarHairline() {
    Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(24.dp).background(RedesignColors.hairline))
}
