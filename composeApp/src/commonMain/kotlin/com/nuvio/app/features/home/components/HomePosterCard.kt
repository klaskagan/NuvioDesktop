package com.nuvio.app.features.home.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.home.HomeRowStyle
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
    rowStyle: HomeRowStyle = HomeRowStyle.Automatic,
) {
    val providerEvent = item.id.startsWith("go3:programme:")
    val calendar = item.calendarCard
    val posterCardStyle = rememberPosterCardStyleUiState()
    val forcedShape = rowStyle.posterShape
    val banner = rowStyle == HomeRowStyle.BannerWithTitle
    val round = rowStyle == HomeRowStyle.Round
    val forceLandscapeTitle = alwaysShowLandscapeTitle || banner
    val isLandscapeMode = if (forcedShape != null) forcedShape == PosterShape.Landscape
    else if (calendar != null) posterCardStyle.catalogLandscapeModeEnabled else useLandscapeBackdropMode || (respectGlobalPosterStyle && posterCardStyle.catalogLandscapeModeEnabled) || (enhanceLandscapeTitles && isTitleArtworkMedia(item.type) && item.posterShape == PosterShape.Landscape)
    val enhance = calendar == null && enhanceLandscapeTitles && rowStyle.enhancesLandscapeArtwork && showLandscapeOverlay && isLandscapeMode && isTitleArtworkMedia(item.type)
    val artworkState = rememberLandscapeMediaArtwork(item.id, item.type, enhance && !forceLandscapeTitle)
    val artwork = artworkState.artwork
    val rowReveal = LocalLandscapeRowReveal.current.takeIf { isTitleArtworkMedia(item.type) }
    val prepared = LocalPreparedLandscapeArtwork.current[item.stableKey()]
    val rowPending = rowReveal != null && prepared?.isLoading == true
    var cleanImageFailed by remember(item.id, item.type, artwork?.cleanBackdrop) { mutableStateOf(false) }
    val cleanBackdrop = artwork?.cleanBackdrop?.takeUnless { cleanImageFailed }
    val effectiveLandscapePoster = if ((!enhance && posterCardStyle.alwaysShowLandscapeClearlogo) || forceLandscapeTitle) null else item.landscapePoster
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
    val drawLandscapeTitle = if (calendar != null) false else if (banner || rowStyle.isLandscapeSize) true
        else if (enhance && !forceLandscapeTitle) cleanBackdrop != null
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
            basePosterWidthDp = (desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp) * rowStyle.landscapeWidthFactor).roundToInt(),
            shape = if (isLandscapeMode) NuvioPosterShape.Landscape else (forcedShape ?: item.posterShape).toNuvioPosterShape(),
            circularArtwork = round,
            artworkBadge = calendar?.let { calendarBadge(it) } ?: item.status?.takeIf { providerEvent }?.uppercase(),
            placeholderTitle = if (providerEvent) "Go3" else item.name,
            requiredLabels = calendar != null || providerEvent || round,
            secondaryDetailLine = if (isLandscapeMode) calendar?.let { calendarReleaseLabel(it) } else null,
            detailLine = if (calendar != null) calendar.episodeLabel ?: calendarMovieLabel(calendar.media.year) else if (round || isLandscapeMode || posterCardStyle.hideLabelsEnabled) null else item.releaseInfo?.let { formatReleaseDateForDisplay(it) },
            showTitleBelow = round || providerEvent || calendar != null || (rowStyle.posterShape != PosterShape.Landscape && showTitleBelow && !posterCardStyle.hideLabelsEnabled && !(enhance && isLandscapeMode)),
            bottomLeftLogoUrl = when {
                !drawLandscapeTitle || banner -> null
                !artworkState.logoFailed -> artwork?.logo ?: item.logo
                rowStyle.isLandscapeSize -> item.logo
                else -> null
            },
            bottomLeftText = if (drawLandscapeTitle && (enhance || forceLandscapeTitle || rowStyle.isLandscapeSize || !posterCardStyle.hideLabelsEnabled)) item.name else null,
            bottomLeftDetail = if (banner) {
                (listOf(localizedMediaTypeLabel(item.type)) + item.genres).filter { it.isNotBlank() }.joinToString(" · ")
            } else null,
            logoTextFallback = enhance || forceLandscapeTitle || rowStyle.isLandscapeSize,
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
