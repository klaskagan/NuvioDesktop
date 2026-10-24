package com.nuvio.app.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.SeasonalTheme
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Its own measured list item: artwork cannot draw over Continue Watching or other shelves. */
@Composable
internal fun SeasonalSpotlightSection(
    theme: SeasonalTheme,
    section: HomeCatalogSection,
    sectionPadding: Dp,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onExplore: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (section.items.isEmpty()) return
    val pageBackground = MaterialTheme.colorScheme.background
    val listState = remember(theme.id) { LazyListState() }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val wide = maxWidth >= 900.dp
        val editorialWidth = (maxWidth * 0.32f).coerceAtMost(480.dp)
        val backdrop = section.items.firstOrNull { !it.banner.isNullOrBlank() }?.banner
        Box(Modifier.matchParentSize().background(theme.tint)) {
            val artwork = theme.backgroundArtwork
            if (artwork != null) {
                Image(
                    painter = painterResource(artwork),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
            } else {
                NuvioAsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                )
            }
            val overlay = if (artwork != null) {
                listOf(Color.Black.copy(alpha = 0.45f), theme.tint.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.24f))
            } else {
                listOf(pageBackground.copy(alpha = 0.95f), theme.tint.copy(alpha = 0.65f), pageBackground.copy(alpha = 0.5f))
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(overlay)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                pageBackground, Color.Transparent, Color.Transparent, pageBackground,
            ))))
        }
        val copy: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(Res.string.seasonal_spotlight_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(stringResource(theme.title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text(
                    stringResource(theme.description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                )
                if (onExplore != null) {
                    Button(
                        onClick = onExplore,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(stringResource(Res.string.seasonal_explore))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        val movies: @Composable (Modifier) -> Unit = { rowModifier ->
            Box(rowModifier) {
                val prepared = rememberShelfArtworkPrefetch(listState, section.items, listOf("seasonal", section.key), enhanceTitles = false, forceShape = com.nuvio.app.features.home.PosterShape.Landscape, allowPortraitFallback = false, alwaysShowLandscapeTitle = true)
                val gate = rememberMediaShelfGate(listState, section.items, listOf(section.key, prepared))
                androidx.compose.runtime.CompositionLocalProvider(LocalPreparedLandscapeArtwork provides prepared, LocalLandscapeRowReveal provides gate) {
                NuvioShelfSection(
                    title = "",
                    entries = section.items,
                    state = listState,
                    rowContentPadding = PaddingValues(start = if (wide) 0.dp else sectionPadding, end = sectionPadding),
                    itemSpacing = 12.dp,
                    rowModifier = Modifier.fillMaxWidth(),
                    key = MetaPreview::stableKey,
                ) { movie ->
                    HomePosterCard(
                        item = movie,
                        useLandscapeBackdropMode = true,
                        isWatched = WatchingState.isPosterWatched(watchedKeys, movie, fullyWatchedSeriesKeys),
                        onClick = onPosterClick?.let { { it(movie) } },
                        onLongClick = onPosterLongClick?.let { { it(movie) } },
                        showLandscapeOverlay = true,
                        allowPortraitFallback = false,
                        alwaysShowLandscapeTitle = true,
                        showTitleBelow = false,
                    )
                }
                }
                if (listState.canScrollForward) {
                    FilledIconButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem((listState.firstVisibleItemIndex + 2).coerceAtMost(section.items.lastIndex))
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.75f), contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(Res.string.seasonal_next_movies))
                    }
                }
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(vertical = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Box(Modifier.width(editorialWidth).padding(start = sectionPadding)) { copy() }
                movies(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(Modifier.padding(horizontal = sectionPadding)) { copy() }
                movies(Modifier.fillMaxWidth())
            }
        }
    }
}
