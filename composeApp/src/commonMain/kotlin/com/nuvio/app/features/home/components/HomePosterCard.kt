package com.nuvio.app.features.home.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.stableKey

@Composable
fun HomePosterCard(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    useLandscapeBackdropMode: Boolean = false,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLandscapeOverlay: Boolean = true,
    allowPortraitFallback: Boolean = true,
    alwaysShowLandscapeTitle: Boolean = false,
    showTitleBelow: Boolean = true,
    enhanceLandscapeTitles: Boolean = true,
    fillWidth: Boolean = false,
    respectGlobalPosterStyle: Boolean = true,
) {
    val providerEvent = item.id.startsWith("go3:programme:")
    val calendar = item.calendarCard
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeMode = if (calendar != null) posterCardStyle.catalogLandscapeModeEnabled else useLandscapeBackdropMode || (respectGlobalPosterStyle && posterCardStyle.catalogLandscapeModeEnabled) || (enhanceLandscapeTitles && isTitleArtworkMedia(item.type) && item.posterShape == PosterShape.Landscape)
    val enhance = calendar == null && enhanceLandscapeTitles && showLandscapeOverlay && isLandscapeMode && isTitleArtworkMedia(item.type)
    val artworkState = rememberLandscapeMediaArtwork(item.id, item.type, enhance && !alwaysShowLandscapeTitle)
    val artwork = artworkState.artwork
    val rowReveal = LocalLandscapeRowReveal.current.takeIf { isTitleArtworkMedia(item.type) }
    val prepared = LocalPreparedLandscapeArtwork.current[item.stableKey()]
    val rowPending = rowReveal != null && prepared?.isLoading == true
    var cleanImageFailed by remember(item.id, item.type, artwork?.cleanBackdrop) { mutableStateOf(false) }
    val cleanBackdrop = artwork?.cleanBackdrop?.takeUnless { cleanImageFailed }
    val effectiveLandscapePoster = if ((!enhance && posterCardStyle.alwaysShowLandscapeClearlogo) || alwaysShowLandscapeTitle) null else item.landscapePoster
    val originalImageUrl = if (isLandscapeMode) {
        effectiveLandscapePoster ?: item.banner ?: item.poster.takeIf { allowPortraitFallback }
    } else item.poster
    val fallbackImageUrl = if (isLandscapeMode && !effectiveLandscapePoster.isNullOrBlank()) {
        // Landscape custom poster -> fall back to original backdrop, then portrait
        item.banner ?: item.rawPosterUrl.takeIf { allowPortraitFallback }
    } else {
        item.rawPosterUrl.takeIf { !isLandscapeMode || allowPortraitFallback }
    }

    // Unknown supplied art is never classified as textless. A failed/absent clean lookup keeps it intact.
    val imageUrl = if ((enhance && artworkState.isLoading) || rowPending) null else cleanBackdrop ?: originalImageUrl
    val drawLandscapeTitle = if (calendar != null) false else if (enhance && !alwaysShowLandscapeTitle) cleanBackdrop != null
        else isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank() && imageUrl != null

    HomePosterHoverPreview(
        item = item,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) { hoverModifier ->
        NuvioPosterCard(
            title = item.name,
            imageUrl = imageUrl,
            modifier = hoverModifier,
            fallbackImageUrl = if (cleanBackdrop != null) null else fallbackImageUrl,
            onImageError = if (cleanBackdrop != null) ({ cleanImageFailed = true }) else null,
            fillWidth = fillWidth,
            basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp),
            shape = if (isLandscapeMode) NuvioPosterShape.Landscape else item.posterShape.toNuvioPosterShape(),
            artworkBadge = calendar?.let { calendarBadge(it) } ?: item.status?.takeIf { providerEvent }?.uppercase(),
            placeholderTitle = if (providerEvent) "Go3" else item.name,
            requiredLabels = calendar != null || providerEvent,
            secondaryDetailLine = if (isLandscapeMode) calendar?.let { calendarReleaseLabel(it) } else null,
            detailLine = if (calendar != null) calendar.episodeLabel ?: calendarMovieLabel(calendar.media.year) else if (isLandscapeMode || posterCardStyle.hideLabelsEnabled) null else item.releaseInfo?.let { formatReleaseDateForDisplay(it) },
            showTitleBelow = providerEvent || calendar != null || (showTitleBelow && !posterCardStyle.hideLabelsEnabled && !(enhance && isLandscapeMode)),
            bottomLeftLogoUrl = if (drawLandscapeTitle && !artworkState.logoFailed) artwork?.logo ?: item.logo else null,
            bottomLeftText = if (drawLandscapeTitle && (enhance || alwaysShowLandscapeTitle || !posterCardStyle.hideLabelsEnabled)) item.name else null,
            logoTextFallback = enhance || alwaysShowLandscapeTitle,
            revealArtworkTogether = enhance || rowReveal != null,
            artworkPending = (enhance && artworkState.isLoading) || rowPending,
            mediaIdentity = item.stableKey(),
            showArtwork = (rowReveal?.revealed ?: true) && LocalPreparedLandscapeArtwork.current.mayReveal(item.stableKey()),
            onArtworkReadyChanged = rowReveal?.let { row -> { ready -> row.report(item.stableKey(), ready) } },
            isWatched = isWatched,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
