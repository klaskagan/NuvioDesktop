package com.nuvio.app.features.search

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.features.home.components.PosterGridSkeletonRow
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.discoverContent(
    state: DiscoverUiState,
    isSourceLoading: Boolean,
    columns: Int,
    networkCondition: NetworkCondition,
    onTypeSelected: (String) -> Unit,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onRetry: (() -> Unit)? = null,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    desktopHeader: Boolean = false,
    /** Desktop Search passes its own, so these rows are blur sources for the pinned field band. */
    itemModifier: LazyItemScope.() -> Modifier = { Modifier },
    /**
     * The previous selection's titles. A new type/catalog/genre starts with no items, and the grid
     * collapsing to two skeleton rows snapped the page to the top and back under the heading; the
     * old titles stay, dimmed, until the new ones arrive (refetches never flash placeholders).
     */
    heldItems: List<MetaPreview> = emptyList(),
) {
    if (desktopHeader) {
        item(key = "discover-header") {
            SearchSectionHeader(
                title = stringResource(Res.string.compose_search_discover_title),
                count = state.selectedCatalog?.let { selected ->
                    stringResource(Res.string.discover_catalog_context, selected.addonName, selected.type.displayTypeLabel())
                },
                modifier = itemModifier().padding(horizontal = horizontalPadding).padding(top = 18.dp),
            ) {
                DiscoverFilterRow(
                    state = state,
                    onTypeSelected = onTypeSelected,
                    onCatalogSelected = onCatalogSelected,
                    onGenreSelected = onGenreSelected,
                    desktopStyle = true,
                )
            }
        }
    } else {
    item {
        DiscoverSectionHeader(modifier = Modifier.padding(horizontal = 16.dp))
    }
    item {
        DiscoverFilterRow(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp),
            onTypeSelected = onTypeSelected,
            onCatalogSelected = onCatalogSelected,
            onGenreSelected = onGenreSelected,
        )
    }
    state.selectedCatalog?.let { selectedCatalog ->
        item {
            Text(
                text = stringResource(
                    Res.string.discover_catalog_context,
                    selectedCatalog.addonName,
                    selectedCatalog.type.displayTypeLabel(),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }

    when {
        heldItems.isNotEmpty() && state.items.isEmpty() && state.emptyStateReason == null && state.errorMessage == null -> {
            items(count = (heldItems.size + columns - 1) / columns, key = { "discover-row:$it" }) { rowIndex ->
                val firstIndex = rowIndex * columns
                PosterGridRow(
                    items = heldItems.subList(firstIndex, minOf(firstIndex + columns, heldItems.size)),
                    columns = columns,
                    modifier = itemModifier().padding(horizontal = horizontalPadding)
                        .graphicsLayer { alpha = 0.4f }.testTag("discover-held-row"),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = null,
                    onPosterLongClick = null,
                )
            }
        }

        (state.isLoading || (isSourceLoading && state.selectedCatalogKey?.startsWith("go3:") != true)) && state.items.isEmpty() -> {
            items(2) {
                PosterGridSkeletonRow(
                    columns = columns,
                    aspectRatio = if(state.selectedCatalogKey?.startsWith("go3:")==true) 16f/9f else 0.68f,
                    modifier = itemModifier().padding(horizontal = horizontalPadding),
                )
            }
        }

        state.items.isEmpty() -> {
            item {
                DiscoverEmptyStateCard(
                    reason = state.emptyStateReason,
                    errorMessage = state.errorMessage,
                    networkCondition = networkCondition,
                    onRetry = onRetry,
                    modifier = itemModifier().padding(horizontal = horizontalPadding),
                )
            }
        }

        else -> {
            items(count = (state.items.size + columns - 1) / columns, key = { "discover-row:$it" }) { rowIndex ->
                val firstIndex = rowIndex * columns
                PosterGridRow(
                    items = state.items.subList(firstIndex, minOf(firstIndex + columns, state.items.size)),
                    columns = columns,
                    modifier = itemModifier().padding(horizontal = horizontalPadding),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
            if (state.isLoading) {
                item {
                    CatalogLoadingFooter(
                        modifier = itemModifier().padding(horizontal = horizontalPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.compose_search_discover_title),
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun DiscoverFilterRow(
    state: DiscoverUiState,
    onTypeSelected: (String) -> Unit,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    desktopStyle: Boolean = false,
) {
    @Composable
    fun Chip(
        title: String,
        label: String,
        selectedKey: String?,
        options: List<NuvioDropdownOption>,
        enabled: Boolean,
        onSelected: (NuvioDropdownOption) -> Unit,
    ) = if (desktopStyle) {
        SearchDropdownChip(label = label, selectedKey = selectedKey, options = options, enabled = enabled, onSelected = onSelected)
    } else {
        NuvioDropdownChip(title = title, label = label, selectedKey = selectedKey, options = options, enabled = enabled, onSelected = onSelected)
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(
            title = stringResource(Res.string.discover_select_type),
            label = state.selectedType?.displayTypeLabel() ?: stringResource(Res.string.discover_type),
            selectedKey = state.selectedType,
            options = state.typeOptions.map { NuvioDropdownOption(key = it, label = it.displayTypeLabel()) },
            enabled = state.typeOptions.isNotEmpty(),
            onSelected = { onTypeSelected(it.key) },
        )
        Chip(
            title = stringResource(Res.string.discover_select_catalog),
            label = state.selectedCatalog?.catalogName ?: stringResource(Res.string.discover_catalog),
            selectedKey = state.selectedCatalogKey,
            options = state.catalogOptions.sortedBy { if(it.catalogName.contains("go3",ignoreCase=true) || it.addonName.contains("go3",ignoreCase=true)) 0 else 1 }.map { option -> NuvioDropdownOption(key = option.key, label = option.catalogName) },
            enabled = state.catalogOptions.isNotEmpty(),
            onSelected = { onCatalogSelected(it.key) },
        )

        val selectedCatalog = state.selectedCatalog
        val genreOptions = buildList {
            if (selectedCatalog?.genreRequired != true) {
                add(NuvioDropdownOption(key = "", label = if(state.selectedCatalogKey?.startsWith("go3:")==true) "All events" else stringResource(Res.string.discover_all_genres)))
            }
            addAll(state.genreOptions.map { genre -> NuvioDropdownOption(key = genre, label = genre) })
        }
        Chip(
            title = stringResource(Res.string.discover_select_genre),
            label = state.selectedGenre ?: if(state.selectedCatalogKey?.startsWith("go3:")==true) "All events" else stringResource(Res.string.discover_all_genres),
            selectedKey = state.selectedGenre ?: "",
            options = genreOptions,
            enabled = genreOptions.size > 1 || selectedCatalog?.genreRequired == true,
            onSelected = { option ->
                onGenreSelected(option.key.ifBlank { null })
            },
        )
    }
}

@Composable
private fun CatalogLoadingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DiscoverEmptyStateCard(
    reason: DiscoverEmptyStateReason?,
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (
        reason == DiscoverEmptyStateReason.RequestFailed &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            modifier = modifier,
            onRetry = onRetry,
        )
        return
    }

    val title: String
    val message: String

    when (reason) {
        DiscoverEmptyStateReason.NoActiveAddons -> {
            title = stringResource(Res.string.home_empty_no_sources_title)
            message = stringResource(Res.string.search_empty_no_sources_message)
        }

        DiscoverEmptyStateReason.NoDiscoverCatalogs -> {
            title = stringResource(Res.string.discover_empty_no_catalogs_title)
            message = stringResource(Res.string.discover_empty_no_catalogs_message)
        }

        DiscoverEmptyStateReason.RequestFailed -> {
            title = stringResource(Res.string.discover_empty_load_failed_title)
            message = errorMessage ?: stringResource(Res.string.discover_empty_load_failed_message)
        }

        DiscoverEmptyStateReason.NoResults, null -> {
            title = stringResource(Res.string.discover_empty_no_results_title)
            message = stringResource(Res.string.discover_empty_no_results_message)
        }
    }

    HomeEmptyStateCard(
        modifier = modifier,
        title = title,
        message = message,
        actionLabel = if (reason == DiscoverEmptyStateReason.RequestFailed) {
            stringResource(Res.string.action_retry)
        } else {
            null
        },
        onActionClick = if (reason == DiscoverEmptyStateReason.RequestFailed) onRetry else null,
    )
}

@Composable
private fun String.displayTypeLabel(): String =
    when (lowercase()) {
        "movie" -> stringResource(Res.string.media_movies)
        "series" -> stringResource(Res.string.media_series)
        "anime" -> stringResource(Res.string.media_anime)
        "channel" -> stringResource(Res.string.media_channels)
        "tv" -> stringResource(Res.string.media_tv)
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
