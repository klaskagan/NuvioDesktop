package com.nuvio.app.features.details.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DesktopBackdropVerticalBias
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.nuvio.app.core.ui.NuvioDesktopImageScaling
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.StandardDesktopViewportAspectRatio
import com.nuvio.app.core.ui.WideDesktopViewportAspectRatio
import com.nuvio.app.core.ui.expandingWideArtworkWidthDp
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tmdb.originalTmdbImageUrl

/** Viewport-filling title artwork with side fades; Go3 events use it behind their page. */
@Composable
fun DesktopDetailBackdrop(
    meta: MetaDetails,
    viewportHeight: Dp,
    heroTrailerReady: Boolean = false,
    heroTrailer: (@Composable (Modifier, Boolean) -> Unit)? = null,
    heroGradientColor: Color? = null,
    blurBackdrop: Boolean = false,
    onBackdropLoaded: (Painter) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val sideGradientColor = heroGradientColor ?: colorScheme.background
    val opacity = NuvioTokens.Opacity
    val trailerAlpha by animateFloatAsState(
        targetValue = if (heroTrailerReady) 1f else 0f,
        animationSpec = tween(durationMillis = NuvioTokens.Motion.sheetEnterMillis),
        label = "desktop_detail_hero_trailer_alpha",
    )
    val gradientIntensity by animateFloatAsState(
        targetValue = if (heroTrailerReady) 0.3f else 1f,
        animationSpec = tween(durationMillis = NuvioTokens.Motion.sheetEnterMillis),
        label = "desktop_detail_hero_gradient_intensity",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(sideGradientColor),
    ) {
        val aspectRatio = maxWidth.value / viewportHeight.value
        val useWideArtworkFrame = aspectRatio > StandardDesktopViewportAspectRatio
        val cropSideBackdrop = aspectRatio > WideDesktopViewportAspectRatio
        val backdropWidth = if (useWideArtworkFrame) {
            expandingWideArtworkWidthDp(maxWidth.value, viewportHeight.value).dp
        } else {
            maxWidth
        }
        val backdropModifier = Modifier
            .align(Alignment.CenterEnd)
            .width(backdropWidth)
            .fillMaxHeight()
        val artworkModifier = if (blurBackdrop) backdropModifier.blur(30.dp) else backdropModifier
        val bottomSpreadStrength = ((21f / 9f - aspectRatio) / (5f / 9f)).coerceIn(0f, 1f)
        val baseSideFade = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.00f to sideGradientColor,
                0.12f to sideGradientColor.copy(alpha = 0.98f * gradientIntensity),
                0.34f to sideGradientColor.copy(alpha = opacity.overlayHeavy * gradientIntensity),
                0.62f to sideGradientColor.copy(alpha = opacity.overlayLight * gradientIntensity),
                0.86f to sideGradientColor.copy(alpha = opacity.subtle * gradientIntensity),
                1.00f to Color.Transparent,
            ),
        )
        val imageUrl = meta.background ?: meta.poster
        if (imageUrl != null) {
            AsyncImage(
                model = originalTmdbImageUrl(imageUrl),
                contentDescription = meta.name,
                desktopAnimation = NuvioDesktopAnimation.Always,
                modifier = artworkModifier,
                alignment = BiasAlignment(0f, DesktopBackdropVerticalBias),
                contentScale = if (useWideArtworkFrame && !cropSideBackdrop) ContentScale.Fit else ContentScale.Crop,
                desktopImageScaling = NuvioDesktopImageScaling.Disabled,
                onSuccess = { state -> onBackdropLoaded(state.painter) },
            )
        } else {
            DesktopStripePlaceholder(modifier = backdropModifier)
        }

        if (heroTrailer != null) {
            heroTrailer(backdropModifier.graphicsLayer(alpha = trailerAlpha), true)
        }

        Box(
            modifier = backdropModifier
                .drawWithCache {
                    val bottomSpread = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to sideGradientColor.copy(alpha = 0.12f * gradientIntensity * bottomSpreadStrength),
                            0.55f to sideGradientColor.copy(alpha = 0.12f * gradientIntensity * bottomSpreadStrength),
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(0f, size.height * 1.15f),
                        radius = size.width * 0.72f,
                    )
                    onDrawBehind {
                        drawRect(baseSideFade)
                        if (bottomSpreadStrength > 0f) drawRect(bottomSpread)
                    }
                },
        )
    }
}
