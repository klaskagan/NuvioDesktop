package com.nuvio.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioShelfSectionHeader
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.CatalogFilterBar
import com.nuvio.app.core.ui.CatalogFilterControl
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.features.catalog.CatalogContent
import com.nuvio.app.features.catalog.CatalogUiState
import com.nuvio.app.features.collection.TmdbCollectionMediaType
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal val PersonSubview.titleResource
    get() = when (this) {
        PersonSubview.KnownFor -> Res.string.person_known_for_shelf
        PersonSubview.Movies -> Res.string.seasonal_movies
        PersonSubview.Series -> Res.string.seasonal_series
        PersonSubview.Upcoming -> Res.string.person_upcoming
    }

private val creditQuerySaver = listSaver<PersonCreditQuery, String>(
    save = { listOf(it.sort.name, it.release.name, it.watched.name, it.genreId?.toString().orEmpty()) },
    restore = {
        PersonCreditQuery(
            PersonCreditSort.valueOf(it[0]),
            PersonReleaseFilter.valueOf(it[1]),
            PersonWatchedFilter.valueOf(it[2]),
            it[3].toIntOrNull(),
        )
    },
)

@Composable
internal fun PersonExpandedContent(
    person: PersonDetail,
    section: PersonSubview,
    knownFor: List<MetaPreview>?,
    horizontalPadding: Dp,
    profileHeader: (@Composable (LazyGridState) -> Unit)?,
    controlsTopInset: Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onOpenMeta: (MetaPreview) -> Unit,
    onBack: () -> Unit,
) {
    val today = LocalDate.parse(CurrentDateProvider.todayIsoDate())
    val grid = rememberLazyGridState()
    var query by rememberSaveable(person.tmdbId, section, stateSaver = creditQuerySaver) {
        mutableStateOf(PersonCreditQuery())
    }
    val filterable = section == PersonSubview.Movies || section == PersonSubview.Series
    val credits = remember(person, section, knownFor, today) {
        when (section) {
            PersonSubview.KnownFor -> knownFor.orEmpty()
            PersonSubview.Movies -> person.movieCredits
            PersonSubview.Series -> person.tvCredits
            PersonSubview.Upcoming -> upcomingPersonCredits(person, today)
        }
    }
    val items = remember(credits, query, today, watchedKeys, fullyWatchedSeriesKeys, person.creditGenreIds, filterable) {
        if (filterable) filterPersonCredits(credits, query, today, person.creditGenreIds, watchedKeys, fullyWatchedSeriesKeys)
        else credits
    }
    val title = stringResource(section.titleResource)
    val heading: @Composable (Dp) -> Unit = { padding ->
        NuvioShelfSectionHeader(
            title, Modifier.padding(horizontal = padding),
            viewAllPillSize = NuvioViewAllPillSize.Compact,
        )
    }
    val filters: @Composable (Dp) -> Unit = { padding ->
        PersonCatalogFilters(person, section, credits, query, padding) { selection ->
            if (selection != query) {
                query = selection
                if (profileHeader == null) grid.requestScrollToItem(0)
                else if (grid.firstVisibleItemIndex > 0) grid.requestScrollToItem(1)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (profileHeader == null) {
            heading(horizontalPadding)
            if (filterable) filters(horizontalPadding)
        }
        CatalogContent(
            title = title,
            subtitle = "",
            target = null,
            uiState = CatalogUiState(items, isLoading = section == PersonSubview.KnownFor && knownFor == null),
            gridState = grid,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            networkCondition = NetworkCondition.Unknown,
            onBack = onBack,
            onRetry = {},
            onPosterClick = onOpenMeta,
            modifier = Modifier.weight(1f),
            showHeader = false,
            gridIdentity = person.tmdbId to section,
            embeddedHorizontalPadding = horizontalPadding,
            scrollingIntro = profileHeader?.let { profile ->
                {
                    Column {
                        profile(grid)
                        heading(0.dp)
                    }
                }
            },
            pinnedControlsTopInset = controlsTopInset,
            pinnedControls = if (profileHeader != null && filterable) {
                { filters(0.dp) }
            } else null,
            emptyContent = if (filterable && credits.isNotEmpty()) {
                { Text(stringResource(Res.string.person_no_matching_credits), color = MaterialTheme.nuvio.colors.textPrimary) }
            } else null,
        )
    }
}

@Composable
private fun PersonCatalogFilters(
    person: PersonDetail,
    section: PersonSubview,
    credits: List<MetaPreview>,
    query: PersonCreditQuery,
    horizontalPadding: Dp,
    onChange: (PersonCreditQuery) -> Unit,
) {
    val movie = section == PersonSubview.Movies
    val genreIds = remember(credits, person.creditGenreIds) {
        credits.flatMap { person.creditGenreIds[it.stableKey()].orEmpty() }.toSet()
    }
    val genres by produceState(emptyMap<Int, String>(), person.tmdbId, section, genreIds) {
        value = emptyMap()
        if (genreIds.isEmpty()) return@produceState
        try {
            val mediaType = if (movie) TmdbCollectionMediaType.MOVIE else TmdbCollectionMediaType.TV
            value = TmdbCollectionSourceResolver.genres(mediaType).filterKeys { it in genreIds }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The remaining controls are usable without genre labels.
        }
    }
    val sorts = listOf(
        PersonCreditSort.Popularity to Res.string.seasonal_sort_popular,
        PersonCreditSort.Latest to Res.string.person_latest,
        PersonCreditSort.HighestRated to Res.string.seasonal_sort_highest,
        PersonCreditSort.LowestRated to Res.string.seasonal_sort_lowest,
        PersonCreditSort.NewestYear to Res.string.person_year_newest,
        PersonCreditSort.OldestYear to Res.string.person_year_oldest,
    ).map { (sort, label) -> NuvioDropdownOption(sort.name, stringResource(label)) }
    val controls = listOfNotNull(
        CatalogFilterControl(stringResource(Res.string.seasonal_sort_filter), query.sort.name, sorts) {
            onChange(query.withSort(PersonCreditSort.valueOf(it)))
        },
        CatalogFilterControl(
            stringResource(Res.string.person_release_status),
            query.release.name,
            listOf(
                NuvioDropdownOption(PersonReleaseFilter.All.name, stringResource(Res.string.person_all_release_dates)),
                NuvioDropdownOption(PersonReleaseFilter.Released.name,
                    stringResource(if (movie) Res.string.person_released else Res.string.person_aired)),
                NuvioDropdownOption(PersonReleaseFilter.Upcoming.name, stringResource(Res.string.person_upcoming)),
            ),
        ) { onChange(query.withRelease(PersonReleaseFilter.valueOf(it))) },
        CatalogFilterControl(
            stringResource(Res.string.seasonal_watched_filter),
            query.watched.name,
            listOf(
                NuvioDropdownOption(PersonWatchedFilter.All.name, stringResource(Res.string.seasonal_any_watch_state)),
                NuvioDropdownOption(PersonWatchedFilter.Watched.name, stringResource(Res.string.seasonal_watched)),
                NuvioDropdownOption(PersonWatchedFilter.Unwatched.name, stringResource(Res.string.seasonal_unwatched)),
            ),
        ) { onChange(query.copy(watched = PersonWatchedFilter.valueOf(it))) },
        genres.takeIf { it.isNotEmpty() }?.let { options ->
            CatalogFilterControl(
                stringResource(Res.string.seasonal_genre_filter),
                query.genreId?.toString().orEmpty(),
                listOf(NuvioDropdownOption("", stringResource(Res.string.seasonal_all_genres))) + options.entries.sortedBy { it.value }
                    .map { NuvioDropdownOption(it.key.toString(), it.value) },
            ) { onChange(query.copy(genreId = it.toIntOrNull())) }
        },
    )
    val cleared = query.clearFilters()
    CatalogFilterBar(
        controls = controls,
        clearLabel = stringResource(Res.string.person_clear_filters),
        onClear = if (query != cleared) ({ onChange(cleared) }) else null,
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
    )
}
