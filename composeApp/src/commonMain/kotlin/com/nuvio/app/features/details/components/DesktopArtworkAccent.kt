package com.nuvio.app.features.details.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

private val ArtworkAccentBase = Color(0xFF050505)

@Composable
internal fun DesktopArtworkAccent(backdrop: String?, poster: String?) {
    val landscape = backdrop?.takeIf { it.isNotBlank() }
    val portrait = poster?.takeIf { it.isNotBlank() && it != landscape }
    val context = LocalPlatformContext.current
    var loadedArtwork by remember(landscape, portrait) { mutableStateOf<Painter?>(null) }
    var tryPoster by remember(landscape, portrait) { mutableStateOf(landscape == null) }
    val url = if (tryPoster) portrait else landscape
    if (url != null) {
        val request = remember(context, url) {
            ImageRequest.Builder(context).data(url).size(780).build()
        }
        rememberAsyncImagePainter(
            model = request,
            onSuccess = { loadedArtwork = it.painter },
            onError = {
                loadedArtwork = null
                if (!tryPoster) tryPoster = true
            },
        )
    }
    val opacity by animateFloatAsState(
        targetValue = if (loadedArtwork == null) 0f else 1f,
        animationSpec = tween(320),
        label = "details_artwork_accent",
    )
    ArtworkAccentLayers(loadedArtwork, opacity, MaterialTheme.colorScheme.background)
}

@Composable
internal fun ArtworkAccentLayers(artwork: Painter?, opacity: Float, fallback: Color) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .background(lerp(fallback, ArtworkAccentBase, opacity)),
    ) {
        if (artwork != null && opacity > 0f) {
            val glowHeight = maxHeight * 0.4f
            AccentArtworkImage(artwork, Modifier.fillMaxSize(), opacity * 0.5f, blurRadius = 80)
            // Screen the top glow against the blurred base without tinting foreground content.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = opacity * 0.2f
                    blendMode = BlendMode.Screen
                },
            ) {
                AccentArtworkImage(
                    artwork,
                    Modifier.fillMaxWidth().height(glowHeight),
                    opacity = 1f,
                    blurRadius = 50,
                )
            }
        }
    }
}

@Composable
private fun AccentArtworkImage(
    artwork: Painter,
    modifier: Modifier,
    opacity: Float,
    blurRadius: Int,
) {
    Image(
        painter = artwork,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.graphicsLayer {
            alpha = opacity
            scaleX = 1.2f
            scaleY = 1.2f
        }.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded),
    )
}
