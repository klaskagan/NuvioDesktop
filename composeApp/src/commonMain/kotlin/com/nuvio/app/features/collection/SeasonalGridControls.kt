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
