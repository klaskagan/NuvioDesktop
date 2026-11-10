package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import com.nuvio.app.features.home.HomeRowStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.PosterLandscapeAspectRatio
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.collection.usesConfiguredArtwork
import com.nuvio.app.features.home.PosterShape

@Composable
fun HomeCollectionRowSection(
    collection: Collection,
    modifier: Modifier = Modifier,
    sectionPadding: Dp? = null,
    animateGifs: Boolean = true,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)? = null,
    rowStyleKey: String? = null,
    rowStyle: HomeRowStyle = HomeRowStyle.Automatic,
) {
    if (collection.folders.isEmpty()) return

    if (sectionPadding != null && rowStyleKey == null && rowStyle == HomeRowStyle.Automatic) {
        HomeCollectionRowSectionContent(
            collection = collection,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            animateGifs = animateGifs,
            onFolderClick = onFolderClick,
            rowStyleKey = rowStyleKey,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCollectionRowSectionContent(
                collection = collection,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = sectionPadding ?: homeSectionHorizontalPaddingForWidth(maxWidth.value),
                animateGifs = animateGifs,
                onFolderClick = onFolderClick,
                rowStyleKey = rowStyleKey,
                rowStyle = rowStyle,
                rankingLayout = if (rowStyle == HomeRowStyle.NumberedRanking) NumberedRankingLayout(maxWidth) else null,
            )
        }
    }
}

@Composable
private fun HomeCollectionRowSectionContent(
    collection: Collection,
    modifier: Modifier,
    sectionPadding: Dp,
    animateGifs: Boolean,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    rowStyleKey: String?,
    rowStyle: HomeRowStyle = HomeRowStyle.Automatic,
    rankingLayout: NumberedRankingLayout? = null,
) {
    var pickerOpen by remember(rowStyleKey) { mutableStateOf(false) }
    val shelfInset = rankingLayout?.inset ?: sectionPadding
    NuvioShelfSection(
        title = collection.title,
        entries = collection.folders,
        modifier = modifier,
        headerHorizontalPadding = shelfInset,
        rowContentPadding = PaddingValues(horizontal = shelfInset),
        itemSpacing = rankingLayout?.gap ?: 10.dp,
        key = { folder -> "collection_${collection.id}_folder_${folder.id}" },
    ) { folder ->
        CollectionFolderCard(
            folder = folder,
            shape = rowStyle.posterShape ?: if (collection.usesConfiguredArtwork()) PosterShape.Landscape else folder.posterShape,
            rowStyle = rowStyle,
            rankingLayout = rankingLayout,
            onLongClick = rowStyleKey?.let { { pickerOpen = true } },
            animateGifs = animateGifs,
            onClick = onFolderClick?.let { { it(collection.id, folder.id) } },
        )
    }
    if (pickerOpen && rowStyleKey != null) {
        HomeRowStylePicker(rowStyleKey, onDismiss = { pickerOpen = false })
    }
}

@Composable
private fun CollectionFolderCard(
    folder: CollectionFolder,
    shape: PosterShape,
    rowStyle: HomeRowStyle,
    modifier: Modifier = Modifier,
    animateGifs: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    rankingLayout: NumberedRankingLayout? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
    val cardWidth: Dp
    val aspectRatio: Float

    when (shape) {
        PosterShape.Poster -> {
            cardWidth = basePosterWidthDp.dp
            aspectRatio = 0.675f
        }
        PosterShape.Landscape -> {
            cardWidth = landscapePosterWidth((basePosterWidthDp * rowStyle.landscapeWidthFactor).roundToInt())
            aspectRatio = PosterLandscapeAspectRatio
        }
        PosterShape.Square -> {
            cardWidth = basePosterWidthDp.dp
            aspectRatio = 1f
        }
    }

    Column(
        modifier = Modifier
            .posterCardClickable(onClick = onClick, onLongClick = onLongClick)
            .then(modifier)
            .width(rankingLayout?.posterWidth ?: cardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val coverShape = if (rowStyle == HomeRowStyle.Round) {
            CircleShape
        } else {
            RoundedCornerShape(rankingLayout?.posterCorner ?: posterCardStyle.cornerRadiusDp.dp)
        }
        val imageUrl = collectionFolderCardImageUrl(folder)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (rankingLayout == null) aspectRatio else 212f / 322f)
                .nuvioCardDepth(
                    shape = coverShape,
                    surface = NuvioCardDepthSurface.Posters,
                ),
            shape = coverShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !imageUrl.isNullOrBlank() -> {
                        CollectionCardRemoteImage(
                            imageUrl = imageUrl,
                            staticImageUrl = firstNonBlank(folder.coverImageUrl),
                            contentDescription = folder.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            animateIfPossible = animateGifs &&
                                isAnimatedCollectionFolderImage(folder, imageUrl) &&
                                LocalScreenActive.current,
                        )
                    }
                    !folder.coverEmoji.isNullOrBlank() -> {
                        Text(
                            text = folder.coverEmoji,
                            fontSize = 36.sp,
                        )
                    }
                    else -> {
                        Text(
                            text = folder.title.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (!folder.hideTitle && rowStyle == HomeRowStyle.BannerWithTitle) {
                    Text(
                        text = folder.title,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!folder.hideTitle && rowStyle != HomeRowStyle.BannerWithTitle) {
            Text(
                text = folder.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun collectionFolderCardImageUrl(folder: CollectionFolder): String? {
    return if (folder.mobileFocusGifEnabled) {
        firstNonBlank(folder.focusGifUrl, folder.coverImageUrl)
    } else {
        firstNonBlank(folder.coverImageUrl)
    }
}

private fun firstNonBlank(vararg candidates: String?): String? {
    return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun isAnimatedCollectionFolderImage(
    folder: CollectionFolder,
    imageUrl: String,
): Boolean {
    val gifUrl = firstNonBlank(folder.focusGifUrl) ?: return false
    return folder.mobileFocusGifEnabled && imageUrl == gifUrl
}
