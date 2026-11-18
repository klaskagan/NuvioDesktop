package com.nuvio.app.features.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.posterGridColumnCountForCatalogWidth
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.details.components.DetailCastSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.calendar_orientation_landscape
import nuvio.composeapp.generated.resources.calendar_orientation_portrait
import nuvio.composeapp.generated.resources.calendar_posters_landscape
import nuvio.composeapp.generated.resources.calendar_posters_portrait
import nuvio.composeapp.generated.resources.compose_search_movies_and_tv
import nuvio.composeapp.generated.resources.compose_search_people
import nuvio.composeapp.generated.resources.person_role_director
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Column count for the merged results grid; landscape cards use the wider landscape poster width. */
internal fun searchGridColumns(screenWidth: Dp, basePosterWidthDp: Int, landscape: Boolean): Int {
    val cardWidthDp = if (landscape) landscapePosterWidth(basePosterWidthDp).value.toInt() else basePosterWidthDp
    return if (isDesktop || landscape) {
        posterGridColumnCountForCatalogWidth(screenWidth = screenWidth, basePosterWidthDp = cardWidthDp)
            .let { if (landscape) it.coerceAtLeast(2) else it }
    } else {
        com.nuvio.app.core.ui.posterGridColumnCountForWidth(screenWidth)
    }
}

/** Search results: optional People row, then one vertical Movies & TV grid without catalog names. */
internal fun LazyListScope.searchResultsContent(
    state: SearchUiState,
    columns: Int,
    landscape: Boolean,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onPersonClick: ((SearchPerson) -> Unit)?,
) {
    val results = state.results
    if (state.people.isNotEmpty()) {
        item(key = "search-people") {
            SearchPeopleRow(people = state.people, onPersonClick = onPersonClick)
        }
    }
    if (state.people.isNotEmpty() && results.isNotEmpty()) {
        item(key = "search-movies-tv-label") {
            SearchSectionTitle(text = stringResource(Res.string.compose_search_movies_and_tv))
        }
    }
    items(count = (results.size + columns - 1) / columns, key = { "search-row:$it" }) { rowIndex ->
        val firstIndex = rowIndex * columns
        PosterGridRow(
            items = results.subList(firstIndex, minOf(firstIndex + columns, results.size)),
            columns = columns,
            modifier = Modifier.padding(horizontal = 16.dp),
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
        )
    }
    if (state.isLoading) {
        item(key = "search-loading-more") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                NuvioLoadingIndicator(modifier = Modifier.size(22.dp))
            }
        }
    }
}

private val SearchPersonItemWidth = 150.dp

/** People matches, rendered with the Details cast row so avatars, sizing and fallbacks stay identical. */
@Composable
private fun SearchPeopleRow(
    people: List<SearchPerson>,
    onPersonClick: ((SearchPerson) -> Unit)?,
) {
    val directorRole = stringResource(Res.string.person_role_director)
    val cast = remember(people, directorRole) {
        people.map { person ->
            MetaPerson(
                name = person.name,
                role = directorRole.takeIf { person.isDirector },
                photo = person.profilePhoto,
                tmdbId = person.tmdbId,
            )
        }
    }
    val peopleById = remember(people) { people.associateBy { it.tmdbId } }
    Column(modifier = Modifier.padding(bottom = 18.dp).testTag("search-people")) {
        SearchSectionTitle(text = stringResource(Res.string.compose_search_people))
        DetailCastSection(
            cast = cast,
            modifier = Modifier.padding(horizontal = 16.dp),
            showHeader = false,
            horizontalScrollPadding = 16.dp,
            minItemWidth = SearchPersonItemWidth,
            onCastClick = onPersonClick?.let { click ->
                { person, _ -> person.tmdbId?.let(peopleById::get)?.let(click) }
            },
        )
    }
}

@Composable
internal fun SearchSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** Same icons and button as Calendar's orientation toggle, backed by Search's own setting. */
@Composable
internal fun SearchPosterOrientationButton(
    landscape: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (landscape) Res.string.calendar_orientation_landscape else Res.string.calendar_orientation_portrait,
    )
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .padding(start = 4.dp)
            .testTag("search-landscape")
            .semantics { stateDescription = if (landscape) "Landscape" else "Portrait" },
    ) {
        Icon(
            painter = painterResource(
                if (landscape) Res.drawable.calendar_posters_landscape else Res.drawable.calendar_posters_portrait,
            ),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
