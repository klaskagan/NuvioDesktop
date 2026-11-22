package com.nuvio.app.features.search

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.DpOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.compose_search_clear
import nuvio.composeapp.generated.resources.compose_search_empty_failed_message
import nuvio.composeapp.generated.resources.compose_search_empty_failed_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_title
import nuvio.composeapp.generated.resources.home_empty_no_sources_title
import nuvio.composeapp.generated.resources.search_empty_no_sources_message
import nuvio.composeapp.generated.resources.search_no_results_for
import nuvio.composeapp.generated.resources.search_placeholder_desktop
import nuvio.composeapp.generated.resources.compose_search_movies_and_tv
import nuvio.composeapp.generated.resources.compose_search_people
import nuvio.composeapp.generated.resources.compose_search_recent_searches
import nuvio.composeapp.generated.resources.compose_search_remove_recent_search
import nuvio.composeapp.generated.resources.home_view_details
import nuvio.composeapp.generated.resources.library_sort_rating_desc
import nuvio.composeapp.generated.resources.library_sort_released_desc
import nuvio.composeapp.generated.resources.search_hero_subtitle
import nuvio.composeapp.generated.resources.search_hero_title
import nuvio.composeapp.generated.resources.search_more_options
import nuvio.composeapp.generated.resources.search_people_count
import nuvio.composeapp.generated.resources.search_scope_all
import nuvio.composeapp.generated.resources.search_scope_movies
import nuvio.composeapp.generated.resources.search_scope_shows
import nuvio.composeapp.generated.resources.search_see_all
import nuvio.composeapp.generated.resources.search_sort_relevance
import nuvio.composeapp.generated.resources.search_titles_count
import nuvio.composeapp.generated.resources.search_top_result
import org.jetbrains.compose.resources.stringResource

// ---------- Model ----------

internal enum class SearchScope { All, Movies, Shows, People }

internal enum class SearchSort { Relevance, Newest, Rating }

private fun MetaPreview.isMovie(): Boolean = type.equals("movie", ignoreCase = true)

private val yearPattern = Regex("""(19|20)\d{2}""")

internal fun MetaPreview.releaseYear(): Int? =
    rawReleaseDate?.take(4)?.toIntOrNull() ?: releaseInfo?.let { yearPattern.find(it)?.value?.toIntOrNull() }

private fun MetaPreview.ratingValue(): Double? = imdbRating?.toDoubleOrNull() ?: sortRating

internal fun List<MetaPreview>.inScope(scope: SearchScope): List<MetaPreview> = when (scope) {
    SearchScope.All -> this
    SearchScope.Movies -> filter { it.isMovie() }
    SearchScope.Shows -> filterNot { it.isMovie() }
    SearchScope.People -> emptyList()
}

internal fun List<MetaPreview>.sortedFor(sort: SearchSort): List<MetaPreview> = when (sort) {
    SearchSort.Relevance -> this
    SearchSort.Newest -> sortedByDescending { it.releaseYear() ?: Int.MIN_VALUE }
    SearchSort.Rating -> sortedByDescending { it.ratingValue() ?: -1.0 }
}

/** Grid titles for the scope: everything except the spotlighted title, in the chosen order. */
internal fun SearchUiState.gridItemsFor(scope: SearchScope, sort: SearchSort): List<MetaPreview> {
    val spotlight = spotlightFor(scope)
    return results.inScope(scope).let { if (spotlight != null) it - spotlight else it }.sortedFor(sort)
}

/** The title the spotlight and backdrop feature for the current scope (none on the People tab). */
internal fun SearchUiState.spotlightFor(scope: SearchScope): MetaPreview? =
    if (scope == SearchScope.All) results.firstOrNull() else null

// ---------- Hero, field, tabs ----------

@Composable
internal fun SearchIdleHero(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 36.dp, bottom = 22.dp, start = 4.dp)) {
        Text(
            text = stringResource(Res.string.search_hero_title),
            style = searchText(SearchHeroTitleSize, FontWeight.Bold, lineHeight = 40.sp, letterSpacing = SearchTightTracking),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.search_hero_subtitle),
            style = searchText(15.sp, color = SearchColors.textSecondary),
        )
    }
}

@Composable
internal fun SearchHeroField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focused: Boolean,
    onClear: () -> Unit,
    fieldModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (focused) SearchColors.fillStrong else SearchColors.fill)
            .border(1.dp, if (focused) SearchColors.hairlineFocused else SearchColors.hairline, shape)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = SearchColors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = searchText(17.sp, color = SearchColors.textMuted), maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = searchText(17.sp, FontWeight.Medium).copy(textDirection = TextDirection.Content),
                cursorBrush = SolidColor(SearchColors.textPrimary),
                modifier = Modifier.fillMaxWidth().then(fieldModifier),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.compose_search_clear), tint = SearchColors.textSecondary)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
internal fun SearchScopeTabs(
    selected: SearchScope,
    counts: Map<SearchScope, Int>,
    onSelect: (SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Each tab reports where it sits so one underline can slide between them.
    val bounds = remember { mutableStateMapOf<SearchScope, Pair<Dp, Dp>>() }
    val target = bounds[selected]
    val indicatorX by animateDpAsState(target?.first ?: 0.dp, tween(260, easing = FastOutSlowInEasing), label = "tab_x")
    val indicatorW by animateDpAsState(target?.second ?: 0.dp, tween(260, easing = FastOutSlowInEasing), label = "tab_w")
    Box(modifier.padding(start = 4.dp, top = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            SearchScope.entries.forEach { scope ->
                val count = counts[scope] ?: 0
                if (scope != SearchScope.All && count == 0) return@forEach
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val isSelected = scope == selected
                val labelColor by animateColorAsState(
                    when {
                        isSelected -> SearchColors.textPrimary
                        hovered -> SearchColors.textSecondary
                        else -> SearchColors.textMuted
                    },
                    tween(160),
                    label = "tab_label",
                )
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            with(density) {
                                bounds[scope] = coords.positionInParent().x.toDp() to coords.size.width.toDp()
                            }
                        }
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(scope) }
                        .padding(top = 4.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(scope.label(), style = searchText(14.sp, FontWeight.SemiBold, labelColor))
                    Spacer(Modifier.width(5.dp))
                    Text(count.toString(), style = searchText(14.sp, FontWeight.Medium, labelColor.copy(alpha = 0.6f)))
                }
            }
        }
        if (target != null) {
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = indicatorX).width(indicatorW).height(2.dp)
                    .clip(RoundedCornerShape(2.dp)).background(SearchColors.textPrimary),
            )
        }
    }
}

@Composable
private fun SearchScope.label(): String = when (this) {
    SearchScope.All -> stringResource(Res.string.search_scope_all)
    SearchScope.Movies -> stringResource(Res.string.search_scope_movies)
    SearchScope.Shows -> stringResource(Res.string.search_scope_shows)
    SearchScope.People -> stringResource(Res.string.compose_search_people)
}

// ---------- Recent searches ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchRecentChips(
    recentSearches: List<String>,
    onSearchPress: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
        Text(stringResource(Res.string.compose_search_recent_searches), style = searchText(13.sp, FontWeight.SemiBold, SearchColors.textMuted))
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val removeLabel = stringResource(Res.string.compose_search_remove_recent_search)
            recentSearches.forEach { recent ->
                val shape = RoundedCornerShape(999.dp)
                Row(
                    modifier = Modifier.clip(shape).background(SearchColors.fill).border(1.dp, SearchColors.hairline, shape)
                        .clickable { onSearchPress(recent) }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = SearchColors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(recent, style = searchText(13.sp, FontWeight.Medium), maxLines = 1)
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).clickable { onRemoveSearch(recent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = removeLabel, tint = SearchColors.textMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ---------- Results ----------

/** Desktop results body: spotlight + people panel, then a sortable grid; or a people grid. */
internal fun LazyListScope.searchDesktopResults(
    state: SearchUiState,
    scope: SearchScope,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    columns: Int,
    peopleColumns: Int,
    gutter: Dp,
    landscape: Boolean,
    onToggleLandscape: () -> Unit,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onPersonClick: ((SearchPerson) -> Unit)?,
) {
    if (scope == SearchScope.People) {
        val rows = state.people.chunked(peopleColumns)
        item(key = "search-people-header") {
            SearchSectionHeader(
                title = stringResource(Res.string.compose_search_people),
                count = stringResource(Res.string.search_people_count, state.people.size),
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
        items(count = rows.size, key = { "search-people-row:$it" }) { index ->
            Row(Modifier.fillMaxWidth().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rows[index].forEach { person ->
                    SearchPersonCard(person, onClick = onPersonClick?.let { { it(person) } }, modifier = Modifier.weight(1f))
                }
                repeat(peopleColumns - rows[index].size) { Spacer(Modifier.weight(1f)) }
            }
        }
        return
    }

    val spotlight = state.spotlightFor(scope)
    if (spotlight != null) {
        item(key = "search-spotlight") {
            SearchSpotlight(
                item = spotlight,
                people = state.people,
                onDetails = { onPosterClick?.invoke(spotlight) },
                onMore = onPosterLongClick?.let { { it(spotlight) } },
                onPersonClick = onPersonClick,
                onSeeAllPeople = { onScopeChange(SearchScope.People) },
                modifier = Modifier.padding(horizontal = gutter).padding(top = 22.dp),
            )
        }
    }

    val gridItems = state.gridItemsFor(scope, sort)
    if (gridItems.isNotEmpty()) {
        item(key = "search-grid-header") {
            SearchSectionHeader(
                title = if (scope == SearchScope.All) stringResource(Res.string.compose_search_movies_and_tv) else scope.label(),
                count = stringResource(Res.string.search_titles_count, gridItems.size),
                modifier = Modifier.padding(horizontal = gutter).padding(top = if (spotlight != null) 18.dp else 8.dp),
            ) {
                SearchSortChip(sort = sort, onSortChange = onSortChange)
                SearchPosterOrientationButton(landscape = landscape, onToggle = onToggleLandscape)
            }
        }
    }
    val rowCount = (gridItems.size + columns - 1) / columns
    items(count = rowCount, key = { "search-row:$it" }) { rowIndex ->
        val first = rowIndex * columns
        PosterGridRow(
            items = gridItems.subList(first, minOf(first + columns, gridItems.size)),
            columns = columns,
            modifier = Modifier.padding(horizontal = gutter),
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
        )
    }
    if (state.isLoading) {
        item(key = "search-loading-more") {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                NuvioLoadingIndicator(modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun SearchSectionHeader(
    title: String,
    count: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = searchText(20.sp, FontWeight.Bold, letterSpacing = (-0.01).em))
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text(count, style = searchText(13.sp, FontWeight.Medium, SearchColors.textMuted))
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { trailing() }
        }
    }
}

@Composable
private fun SearchSortChip(sort: SearchSort, onSortChange: (SearchSort) -> Unit) {
    val labels = mapOf(
        SearchSort.Relevance to stringResource(Res.string.search_sort_relevance),
        SearchSort.Newest to stringResource(Res.string.library_sort_released_desc),
        SearchSort.Rating to stringResource(Res.string.library_sort_rating_desc),
    )
    SearchDropdownChip(
        label = labels.getValue(sort),
        selectedKey = sort.name,
        options = SearchSort.entries.map { NuvioDropdownOption(key = it.name, label = labels.getValue(it)) },
        onSelected = { option -> onSortChange(SearchSort.valueOf(option.key)) },
    )
}

// ---------- Spotlight ----------

@Composable
private fun SearchSpotlight(
    item: MetaPreview,
    people: List<SearchPerson>,
    onDetails: () -> Unit,
    onMore: (() -> Unit)?,
    onPersonClick: ((SearchPerson) -> Unit)?,
    onSeeAllPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SpotlightHero(item, onDetails, onMore, Modifier.weight(1.65f).fillMaxHeight())
        if (people.isNotEmpty()) {
            SearchPeoplePanel(
                people = people.take(4),
                showSeeAll = people.size > 4,
                onPersonClick = onPersonClick,
                onSeeAll = onSeeAllPeople,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SpotlightHero(item: MetaPreview, onDetails: () -> Unit, onMore: (() -> Unit)?, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(Color(0xFF16171B)).clickable(onClick = onDetails)) {
        NuvioAsyncImage(
            model = item.banner ?: item.landscapePoster ?: item.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.88f), 0.55f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        Text(
            text = stringResource(Res.string.search_top_result).uppercase(),
            style = searchText(11.sp, FontWeight.Bold, letterSpacing = 0.08.em),
            modifier = Modifier.padding(start = 26.dp, top = 22.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.62f).padding(start = 28.dp, bottom = 24.dp)) {
            var logoFailed by remember(item.logo) { mutableStateOf(item.logo.isNullOrBlank()) }
            if (!logoFailed) {
                NuvioAsyncImage(
                    model = item.logo,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.height(72.dp).widthIn(max = 320.dp).fillMaxWidth(),
                )
            } else {
                Text(
                    item.name,
                    style = searchText(32.sp, FontWeight.ExtraBold, lineHeight = 36.sp, letterSpacing = SearchTightTracking),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            SpotlightMeta(item)
            item.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = searchText(13.sp, color = SearchColors.textSecondary, lineHeight = 19.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).clickable(onClick = onDetails)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(Res.string.home_view_details), style = searchText(14.sp, FontWeight.SemiBold, Color.Black))
                }
                if (onMore != null) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(Res.string.search_more_options), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightMeta(item: MetaPreview) {
    val parts = buildList {
        item.releaseYear()?.let { add(it.toString()) }
        add(item.type.replaceFirstChar { it.uppercase() })
        item.genres.take(2).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
        item.runtime?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(parts.joinToString("  ·  "), style = searchText(13.sp, FontWeight.Medium, Color(0xFFD4D7DB)), maxLines = 1)
        item.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
            Text("  ·  ", style = searchText(13.sp, FontWeight.Medium, Color(0xFFD4D7DB)))
            Text("IMDb $rating", style = searchText(13.sp, FontWeight.Bold, SearchColors.imdb))
        }
    }
}

// ---------- People ----------

@Composable
private fun SearchPeoplePanel(
    people: List<SearchPerson>,
    showSeeAll: Boolean,
    onPersonClick: ((SearchPerson) -> Unit)?,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.compose_search_people), style = searchText(16.sp, FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            if (showSeeAll) {
                Text(
                    stringResource(Res.string.search_see_all),
                    style = searchText(13.sp, FontWeight.Medium, SearchColors.textSecondary),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSeeAll).padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        people.forEach { person ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = onPersonClick != null) { onPersonClick?.invoke(person) }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(person, Modifier.size(46.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = searchText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    person.department?.let {
                        Text(it, style = searchText(12.sp, color = SearchColors.textMuted), maxLines = 1)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SearchColors.textMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SearchPersonCard(person: SearchPerson, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PersonAvatar(person, Modifier.fillMaxWidth(0.78f).aspectRatio(1f))
        Spacer(Modifier.height(10.dp))
        Text(person.name, style = searchText(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        person.department?.let {
            Text(it, style = searchText(12.sp, color = SearchColors.textMuted), maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PersonAvatar(person: SearchPerson, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0xFF24262B)), contentAlignment = Alignment.Center) {
        val initials = person.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        Text(initials, style = searchText(15.sp, FontWeight.SemiBold, SearchColors.textSecondary))
        if (!person.profilePhoto.isNullOrBlank()) {
            NuvioAsyncImage(
                model = person.profilePhoto,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------- Empty ----------

@Composable
internal fun SearchDesktopEmptyState(
    title: String,
    message: String,
    onClear: () -> Unit,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 96.dp, bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(SearchColors.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = SearchColors.textSecondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = searchText(24.sp, FontWeight.Bold, letterSpacing = SearchTightTracking), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = searchText(15.sp, color = SearchColors.textSecondary, lineHeight = 22.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onRetry != null) {
                SearchPillButton(stringResource(Res.string.action_retry), primary = true, onClick = onRetry)
            }
            SearchPillButton(stringResource(Res.string.compose_search_clear), primary = onRetry == null, onClick = onClear)
        }
    }
}

@Composable
private fun SearchPillButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = searchText(14.sp, FontWeight.SemiBold, if (primary) Color.Black else SearchColors.textPrimary),
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (primary) Color.White else SearchColors.fillStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

// ---------- Screen list ----------

/** The whole desktop Search list: hero, sticky search bar and tabs, then idle/loading/empty/results bodies. */
internal fun LazyListScope.searchDesktopList(
    query: String,
    onQueryChange: (String) -> Unit,
    lastRequestedQuery: String?,
    isSearchFocused: Boolean,
    captureFieldFocus: () -> Unit,
    fieldModifier: Modifier,
    headerColor: Color,
    topPadding: Dp?,
    gutter: Dp,
    recentSearches: List<String>,
    uiState: SearchUiState,
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    sort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    searchColumns: Int,
    peopleColumns: Int,
    searchLandscape: Boolean,
    onToggleLandscape: () -> Unit,
    discoverUiState: DiscoverUiState,
    discoverColumns: Int,
    addonManifestsLoading: Boolean,
    networkCondition: NetworkCondition,
    onRetrySearch: () -> Unit,
    onRetryDiscover: () -> Unit,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onPersonClick: ((SearchPerson) -> Unit)?,
) {
    val normalizedQuery = query.trim()
    val isWaiting = normalizedQuery.isNotBlank() && lastRequestedQuery != normalizedQuery
    val showResults = normalizedQuery.isNotBlank() && !isWaiting && uiState.hasContent

    if (query.isBlank()) {
        item(key = "search-hero") { SearchIdleHero(Modifier.padding(horizontal = gutter)) }
    }
    stickyHeader(key = "search-bar") {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().background(headerColor).nuvioConsumePointerEvents())
            if (headerColor.alpha > 0.5f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(SearchColors.hairline))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = (topPadding ?: 0.dp) + 14.dp, bottom = 14.dp)) {
                SearchHeroField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(Res.string.search_placeholder_desktop),
                    focused = isSearchFocused,
                    onClear = { onQueryChange("") },
                    fieldModifier = fieldModifier,
                )
                if (showResults) {
                    SearchScopeTabs(
                        selected = scope,
                        counts = mapOf(
                            SearchScope.All to uiState.results.size + uiState.people.size,
                            SearchScope.Movies to uiState.results.inScope(SearchScope.Movies).size,
                            SearchScope.Shows to uiState.results.inScope(SearchScope.Shows).size,
                            SearchScope.People to uiState.people.size,
                        ),
                        onSelect = onScopeChange,
                    )
                }
            }
        }
    }

    if (query.isBlank()) {
        if (isSearchFocused && recentSearches.isNotEmpty()) {
            // Keep the field focused while chips are up, or the click blurs it and removes the chip mid-press.
            captureFieldFocus()
            item(key = "search-recent") {
                SearchRecentChips(
                    recentSearches = recentSearches,
                    onSearchPress = onQueryChange,
                    onRemoveSearch = SearchHistoryRepository::removeSearch,
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
        }
        discoverContent(
            state = discoverUiState,
            isSourceLoading = addonManifestsLoading,
            columns = discoverColumns,
            networkCondition = networkCondition,
            onTypeSelected = SearchRepository::selectDiscoverType,
            onCatalogSelected = SearchRepository::selectDiscoverCatalog,
            onGenreSelected = SearchRepository::selectDiscoverGenre,
            onRetry = onRetryDiscover,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
            horizontalPadding = gutter,
            desktopHeader = true,
        )
        return
    }

    when {
        isWaiting || ((uiState.isLoading || addonManifestsLoading) && !uiState.hasContent) -> {
            item(key = "search-skeleton-spotlight") {
                Box(
                    Modifier.padding(horizontal = gutter).padding(top = 22.dp).fillMaxWidth().height(300.dp)
                        .clip(RoundedCornerShape(18.dp)).background(SearchColors.fill),
                )
            }
            items(2, key = { "search-skeleton:$it" }) {
                com.nuvio.app.features.home.components.PosterGridSkeletonRow(
                    columns = searchColumns,
                    aspectRatio = if (searchLandscape) 16f / 9f else 0.68f,
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
        }

        !uiState.hasContent -> item(key = "search-empty") {
            val offline = uiState.emptyStateReason == SearchEmptyStateReason.RequestFailed &&
                (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
            if (offline) {
                NuvioNetworkOfflineCard(
                    condition = networkCondition,
                    onRetry = onRetrySearch,
                    modifier = Modifier.padding(horizontal = gutter).padding(top = 32.dp),
                )
            } else {
                val (title, message) = searchEmptyCopy(uiState, normalizedQuery)
                SearchDesktopEmptyState(
                    title = title,
                    message = message,
                    onClear = { onQueryChange("") },
                    onRetry = onRetrySearch.takeIf { uiState.emptyStateReason == SearchEmptyStateReason.RequestFailed },
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
        }

        else -> searchDesktopResults(
            state = uiState,
            scope = scope,
            sort = sort,
            onSortChange = onSortChange,
            onScopeChange = onScopeChange,
            columns = searchColumns,
            peopleColumns = peopleColumns,
            gutter = gutter,
            landscape = searchLandscape,
            onToggleLandscape = onToggleLandscape,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
            onPersonClick = onPersonClick,
        )
    }
}

@Composable
private fun searchEmptyCopy(state: SearchUiState, query: String): Pair<String, String> = when (state.emptyStateReason) {
    SearchEmptyStateReason.NoActiveAddons ->
        stringResource(Res.string.home_empty_no_sources_title) to stringResource(Res.string.search_empty_no_sources_message)
    SearchEmptyStateReason.NoSearchCatalogs ->
        stringResource(Res.string.compose_search_empty_no_search_catalogs_title) to
            stringResource(Res.string.compose_search_empty_no_search_catalogs_message)
    SearchEmptyStateReason.RequestFailed ->
        stringResource(Res.string.compose_search_empty_failed_title) to
            (state.errorMessage ?: stringResource(Res.string.compose_search_empty_failed_message))
    SearchEmptyStateReason.NoResults, null ->
        stringResource(Res.string.search_no_results_for, query) to
            stringResource(Res.string.compose_search_empty_no_results_message)
}

// ---------- Dropdown ----------

/** Desktop Search dropdown: translucent pill + matching menu, replacing NuvioDropdownChip's opaque card look. */
@Composable
internal fun SearchDropdownChip(
    label: String,
    selectedKey: String?,
    options: List<NuvioDropdownOption>,
    enabled: Boolean = true,
    onSelected: (NuvioDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(999.dp)
    val fill by animateColorAsState(
        when {
            expanded -> SearchColors.fillStrong
            hovered && enabled -> Color.White.copy(alpha = 0.10f)
            else -> SearchColors.fill
        },
        tween(140),
        label = "dropdown_fill",
    )
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "dropdown_chevron")
    Box(modifier) {
        Row(
            Modifier.clip(shape).background(fill)
                .border(1.dp, if (expanded) SearchColors.hairlineFocused.copy(alpha = 0.35f) else SearchColors.hairline, shape)
                .hoverable(interaction)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null) { expanded = true }
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = searchText(13.sp, FontWeight.Medium, if (enabled) SearchColors.textPrimary else SearchColors.textMuted.copy(alpha = 0.6f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = if (enabled) SearchColors.textSecondary else SearchColors.textMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp).rotate(chevronTurn),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 6.dp),
            containerColor = Color(0xFF17181C),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SearchColors.hairline),
            modifier = Modifier.widthIn(min = 200.dp, max = 340.dp).heightIn(max = 420.dp),
        ) {
            options.forEach { option ->
                val isSelected = option.key == selectedKey
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = searchText(14.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                if (isSelected) SearchColors.textPrimary else SearchColors.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = SearchColors.textPrimary, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).height(40.dp),
                )
            }
        }
    }
}
