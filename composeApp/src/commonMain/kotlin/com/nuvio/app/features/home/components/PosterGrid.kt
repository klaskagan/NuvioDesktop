package com.nuvio.app.features.home.components

import com.nuvio.app.core.ui.rememberAnimatedArtworkAnimation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.posterImageRequest
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.NuvioPosterWatchedOverlay
import com.nuvio.app.core.ui.SkeletonPoster
import com.nuvio.app.core.ui.desktopPosterHoverScale
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.isDesktop

internal fun posterGridColumnCountForWidth(screenWidth: Dp): Int =
    com.nuvio.app.core.ui.posterGridColumnCountForWidth(screenWidth)

@Composable
internal fun PosterGridRow(
    items: List<MetaPreview>,
    columns: Int,
    modifier: Modifier = Modifier,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    // Quick look's ← → walks this grid row.
    androidx.compose.runtime.CompositionLocalProvider(LocalPosterQuickLookRow provides items) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            PosterGridTile(
                item = item,
                cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                hideLabels = posterCardStyle.hideLabelsEnabled,
                modifier = Modifier.weight(1f),
                isWatched = WatchingState.isPosterWatched(
                    watchedKeys = watchedKeys,
                    item = item,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                ),
                onClick = onPosterClick?.let { { it(item) } },
                onLongClick = onPosterLongClick?.let { { it(item) } },
            )
        }
        repeat(columns - items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
    }
}

@Composable
internal fun PosterGridSkeletonRow(
    columns: Int,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.68f,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(columns) {
            SkeletonPoster(
                modifier = Modifier.weight(1f),
                aspectRatio = aspectRatio,
                cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                showLabels = !posterCardStyle.hideLabelsEnabled,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGridTile(
    item: MetaPreview,
    cornerRadiusDp: Int,
    hideLabels: Boolean,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val style = rememberPosterCardStyleUiState()
    if (item.posterShape == PosterShape.Landscape || style.catalogLandscapeModeEnabled) {
        HomePosterCard(item = item, modifier = modifier, fillWidth = true, isWatched = isWatched,
            onClick = onClick, onLongClick = onLongClick)
        return
    }
    val platformContext = coil3.compose.LocalPlatformContext.current
    val imageModel = remember(item.poster, item.rawPosterUrl, item.posterShape, platformContext) {
        item.poster?.let { posterImageRequest(platformContext, it,
            if (item.posterShape == PosterShape.Square) NuvioPosterShape.Square else NuvioPosterShape.Poster,
            fallback = item.rawPosterUrl) }
    }
    // No poster, or one that failed (e.g. a custom poster service 404): show the title in the tile.
    var imageFailed by remember(item.id, item.type, item.poster, item.rawPosterUrl) { mutableStateOf(false) }
    HomePosterHoverPreview(
        item = item,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        allowInlineExpand = false,
    ) { hoverModifier, hoverOverlay ->
        Column(
            modifier = Modifier
                .desktopPosterHoverScale()
                .fillMaxWidth()
                .then(hoverModifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.posterShape.posterGridAspectRatio())
                    .clip(RoundedCornerShape(cornerRadiusDp.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .nuvioCardDepth(
                        shape = RoundedCornerShape(cornerRadiusDp.dp),
                        surface = NuvioCardDepthSurface.Posters,
                    )
                    .posterCardClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        zoomImageUrl = item.poster,
                        zoomCornerRadius = cornerRadiusDp.dp,
                        hoverScaleEnabled = false,
                    ),
            ) {
                if (item.poster == null || imageFailed) {
                    Text(
                        text = item.name,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = NuvioTokens.Space.s14),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.nuvio.colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    if (isDesktop) {
                        NuvioAsyncImage(
                            model = imageModel,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            onError = { imageFailed = true },
                            contentScale = ContentScale.Crop,
                            desktopAnimation = rememberAnimatedArtworkAnimation(),
                        )
                    } else {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            onError = { imageFailed = true },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                NuvioPosterWatchedOverlay(isWatched = isWatched)
                hoverOverlay?.invoke(this, RoundedCornerShape(cornerRadiusDp.dp))
            }
            if (!hideLabels) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = item.releaseInfo?.let { formatReleaseDateForDisplay(it) }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                }
            }
        }
    }
}

private fun PosterShape.posterGridAspectRatio(): Float =
    when (this) {
        PosterShape.Poster -> 0.68f
        PosterShape.Square -> 1f
        PosterShape.Landscape -> 1.78f
    }
