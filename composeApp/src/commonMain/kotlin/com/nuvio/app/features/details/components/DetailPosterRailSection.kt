package com.nuvio.app.features.details.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.components.LocalPreparedLandscapeArtwork
import com.nuvio.app.features.home.components.LocalLandscapeRowReveal
import com.nuvio.app.features.home.components.LocalLandscapeArtworkLoader
import com.nuvio.app.features.home.components.LandscapeRowRevealState
import com.nuvio.app.features.home.components.isTitleArtworkMedia
import com.nuvio.app.features.home.components.rememberShelfArtworkPrefetch
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.nuvioShelfHoverOverdraw
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun DetailPosterRailSection(
    title: String,
    items: List<MetaPreview>,
    watchedKeys: Set<String>,
    modifier: Modifier = Modifier,
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    showHeader: Boolean = true,
    headerHorizontalPadding: Dp = 0.dp,
    horizontalScrollPadding: Dp = 0.dp,
    sourceLabel: String? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val posterCardStyle = rememberPosterCardStyleUiState()
    val rowState = rememberLazyListState()
    val settings by remember {
        TmdbSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val profile by ProfileRepository.state.collectAsStateWithLifecycle()
    val loader = LocalLandscapeArtworkLoader.current
    val identities = items.map { it.stableKey() to it.posterShape }
    val gate = remember(title, identities, posterCardStyle.catalogLandscapeModeEnabled,
        settings.language, settings.apiKey, loader, profile.activeProfile?.id) { LandscapeRowRevealState() }
    val visibleIndices by remember(rowState) {
        derivedStateOf { rowState.layoutInfo.visibleItemsInfo.map { it.index } }
    }
    val cohort = visibleIndices.mapNotNull(items::getOrNull).filter { isTitleArtworkMedia(it.type) }
    val ready = visibleIndices.isNotEmpty() && cohort.all { gate.ready[it.stableKey()] == true }
    SideEffect { if (!gate.revealed && ready) gate.revealed = true }
    val prepared = rememberShelfArtworkPrefetch(rowState, items, identity = listOf("detail-rail", title))

    val rowHoverInset = if (isDesktop) DetailRailHoverInset else 0.dp
    val rowEdgePadding = headerHorizontalPadding + horizontalScrollPadding + rowHoverInset

    Column(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared, LocalLandscapeRowReveal provides gate) {
        NuvioShelfSection(
            title = if (showHeader) title else "",
            entries = items,
            state = rowState,
            rowModifier = Modifier
                .nuvioHorizontalScrollBleed(horizontalScrollPadding)
                .nuvioShelfHoverOverdraw(rowHoverInset),
            headerHorizontalPadding = headerHorizontalPadding,
            rowContentPadding = PaddingValues(
                start = rowEdgePadding,
                top = rowHoverInset,
                end = rowEdgePadding,
                bottom = rowHoverInset,
            ),
            key = { item -> item.stableKey() },
        ) { item ->
            val landscape = posterCardStyle.catalogLandscapeModeEnabled || item.posterShape == PosterShape.Landscape
            HomePosterCard(
                item = item,
                useLandscapeBackdropMode = landscape,
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
        }

        sourceLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = headerHorizontalPadding, top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

private val DetailRailHoverInset = 20.dp
