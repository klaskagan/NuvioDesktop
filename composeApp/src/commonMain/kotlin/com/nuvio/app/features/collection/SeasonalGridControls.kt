package com.nuvio.app.features.collection

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SeasonalGridControls(state: FolderDetailUiState) {
    val query = state.seasonalGrid?.query ?: return
    var genres by remember(state.collectionId, state.folder?.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(state.collectionId, state.folder?.id) {
        if (state.folder?.resolvedSources?.none { it.isTmdb } == true) return@LaunchedEffect
        try { genres = TmdbCollectionSourceResolver.genres(TmdbCollectionMediaType.MOVIE)
        } catch (error: CancellationException) { throw error } catch (_: Exception) { /* Other controls remain usable. */ }
    }
    val all = stringResource(Res.string.collections_tab_all)
    val currentYear = CurrentDateProvider.todayIsoDate().take(4).toInt()
    val sorts = listOf(
        SeasonalSort.SOURCE to stringResource(Res.string.seasonal_sort_source),
        SeasonalSort.HIGHEST_RATED to stringResource(Res.string.seasonal_sort_highest),
        SeasonalSort.LOWEST_RATED to stringResource(Res.string.seasonal_sort_lowest),
        SeasonalSort.POPULARITY to stringResource(Res.string.seasonal_sort_popular),
        SeasonalSort.NEWEST to stringResource(Res.string.seasonal_sort_newest),
        SeasonalSort.OLDEST to stringResource(Res.string.seasonal_sort_oldest),
        SeasonalSort.TITLE to stringResource(Res.string.seasonal_sort_title),
        SeasonalSort.TITLE_DESC to stringResource(Res.string.seasonal_sort_title_desc),
        SeasonalSort.SHUFFLED to stringResource(Res.string.seasonal_sort_shuffle))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeasonalChip(stringResource(Res.string.seasonal_catalog_filter), state.selectedTabIndex.toString(),
            state.tabs.mapIndexed { index, tab -> index.toString() to tab.label }) {
            FolderDetailRepository.selectTab(it.toInt())
        }
        SeasonalChip(stringResource(Res.string.seasonal_media_filter), query.mediaType,
            listOf("all" to stringResource(Res.string.library_filter_all_types), "movie" to stringResource(Res.string.seasonal_movies), "series" to stringResource(Res.string.seasonal_series))) {
            FolderDetailRepository.setSeasonalQuery(query.copy(mediaType = it))
        }
        SeasonalChip(stringResource(Res.string.seasonal_sort_filter), query.sort.name, sorts.map { it.first.name to it.second }) {
            FolderDetailRepository.setSeasonalQuery(query.copy(sort = SeasonalSort.valueOf(it)))
        }
        SeasonalChip(stringResource(Res.string.seasonal_genre_filter), query.genreId?.toString().orEmpty(),
            listOf("" to stringResource(Res.string.seasonal_all_genres)) + genres.entries.sortedBy { it.value }.map { it.key.toString() to it.value }) {
            FolderDetailRepository.setSeasonalQuery(query.copy(genreId = it.toIntOrNull(), genreName = genres[it.toIntOrNull()]))
        }
        SeasonalChip(stringResource(Res.string.seasonal_year_filter), query.year?.toString().orEmpty(),
            listOf("" to stringResource(Res.string.seasonal_all_years)) + (currentYear downTo 1900).map { it.toString() to it.toString() }) {
            FolderDetailRepository.setSeasonalQuery(query.copy(year = it.toIntOrNull()))
        }
        SeasonalChip(stringResource(Res.string.seasonal_rating_filter), query.minimumRating?.toString().orEmpty(),
            listOf("" to stringResource(Res.string.seasonal_any_rating)) + (5..9).map { it.toDouble().toString() to "$it+" }) {
            FolderDetailRepository.setSeasonalQuery(query.copy(minimumRating = it.toDoubleOrNull()))
        }
        SeasonalChip(stringResource(Res.string.seasonal_watched_filter), query.watched?.toString().orEmpty(),
            listOf("" to stringResource(Res.string.seasonal_any_watch_state), "true" to stringResource(Res.string.seasonal_watched),
                "false" to stringResource(Res.string.seasonal_unwatched))) {
            FolderDetailRepository.setSeasonalQuery(query.copy(watched = it.toBooleanStrictOrNull()))
        }
    }
}

@Composable
private fun SeasonalChip(title: String, selected: String, values: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    NuvioDropdownChip(title = title, label = values.firstOrNull { it.first == selected }?.second ?: title,
        selectedKey = selected, options = values.map { NuvioDropdownOption(it.first, it.second) },
        onSelected = { onSelected(it.key) })
}

/**
 * The seasonal folder's filters as the shared filter row's pills (Home design, "Seasonal · Explore
 * collection"): catalog picker, Type, Sort, Genre, Year, Rating, Watch status. Each asks the seasonal
 * pager for a new query; an active pill fills.
 */
@Composable
internal fun SeasonalGridMenus(state: FolderDetailUiState) {
    val query = state.seasonalGrid?.query ?: return
    var genres by remember(state.collectionId, state.folder?.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(state.collectionId, state.folder?.id) {
        if (state.folder?.resolvedSources?.none { it.isTmdb } == true) return@LaunchedEffect
        try { genres = TmdbCollectionSourceResolver.genres(TmdbCollectionMediaType.MOVIE)
        } catch (error: CancellationException) { throw error } catch (_: Exception) { /* Other menus remain usable. */ }
    }
    val currentYear = CurrentDateProvider.todayIsoDate().take(4).toInt()
    val set: (SeasonalGridQuery) -> Unit = FolderDetailRepository::setSeasonalQuery
    if (state.tabs.size > 1) {
        val allLabel = stringResource(Res.string.page_bar_all_catalogs)
        com.nuvio.app.core.ui.RedesignMenuButton(
            label = state.selectedTab?.let { if (it.isAllTab) allLabel else it.label } ?: allLabel,
            options = state.tabs.mapIndexed { index, tab -> com.nuvio.app.core.ui.RedesignMenuOption(index, if (tab.isAllTab) allLabel else tab.label) },
            selected = state.selectedTabIndex,
            onSelect = FolderDetailRepository::selectTab,
            active = state.selectedTab?.isAllTab == false,
            tag = "seasonal_catalog_picker",
        )
    }
    seasonalMenu(stringResource(Res.string.page_bar_type), query.mediaType,
        listOf("all" to stringResource(Res.string.library_filter_all_types), "movie" to stringResource(Res.string.seasonal_movies), "series" to stringResource(Res.string.seasonal_series)),
        active = query.mediaType != "all") { set(query.copy(mediaType = it)) }
    val sorts = listOf(
        SeasonalSort.SOURCE to stringResource(Res.string.seasonal_sort_source),
        SeasonalSort.HIGHEST_RATED to stringResource(Res.string.seasonal_sort_highest),
        SeasonalSort.LOWEST_RATED to stringResource(Res.string.seasonal_sort_lowest),
        SeasonalSort.POPULARITY to stringResource(Res.string.seasonal_sort_popular),
        SeasonalSort.NEWEST to stringResource(Res.string.seasonal_sort_newest),
        SeasonalSort.OLDEST to stringResource(Res.string.seasonal_sort_oldest),
        SeasonalSort.TITLE to stringResource(Res.string.seasonal_sort_title),
        SeasonalSort.TITLE_DESC to stringResource(Res.string.seasonal_sort_title_desc),
        SeasonalSort.SHUFFLED to stringResource(Res.string.seasonal_sort_shuffle))
    seasonalMenu(stringResource(Res.string.page_bar_sort), query.sort.name, sorts.map { it.first.name to it.second },
        active = query.sort != SeasonalSort.SOURCE) { set(query.copy(sort = SeasonalSort.valueOf(it))) }
    if (genres.isNotEmpty()) {
        seasonalMenu(stringResource(Res.string.page_bar_genre), query.genreId?.toString().orEmpty(),
            listOf("" to stringResource(Res.string.seasonal_all_genres)) + genres.entries.sortedBy { it.value }.map { it.key.toString() to it.value },
            active = query.genreId != null) { set(query.copy(genreId = it.toIntOrNull(), genreName = genres[it.toIntOrNull()])) }
    }
    seasonalMenu(stringResource(Res.string.page_bar_year), query.year?.toString().orEmpty(),
        listOf("" to stringResource(Res.string.seasonal_all_years)) + (currentYear downTo 1900).map { it.toString() to it.toString() },
        active = query.year != null) { set(query.copy(year = it.toIntOrNull())) }
    seasonalMenu(stringResource(Res.string.page_bar_rating), query.minimumRating?.toString().orEmpty(),
        listOf("" to stringResource(Res.string.seasonal_any_rating)) + (5..9).map { it.toDouble().toString() to "$it+" },
        active = query.minimumRating != null) { set(query.copy(minimumRating = it.toDoubleOrNull())) }
    seasonalMenu(stringResource(Res.string.page_bar_watch_status), query.watched?.toString().orEmpty(),
        listOf("" to stringResource(Res.string.seasonal_any_watch_state), "true" to stringResource(Res.string.seasonal_watched),
            "false" to stringResource(Res.string.seasonal_unwatched)),
        active = query.watched != null) { set(query.copy(watched = it.toBooleanStrictOrNull())) }
}

@Composable
private fun seasonalMenu(label: String, selected: String, values: List<Pair<String, String>>, active: Boolean, onSelected: (String) -> Unit) {
    com.nuvio.app.core.ui.RedesignMenuButton(
        label = label,
        options = values.map { com.nuvio.app.core.ui.RedesignMenuOption(it.first, it.second) },
        selected = selected,
        onSelect = onSelected,
        active = active,
    )
}

/** Whether any seasonal filter differs from the folder's default (Clear shows while it does). */
internal fun SeasonalGridQuery.isFiltered(): Boolean = this != SeasonalGridQuery()
