package com.nuvio.app.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.pow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalOnBackdrop = staticCompositionLocalOf { false }

@Composable
internal fun backdropControlColor(default: Color): Color =
    if (LocalOnBackdrop.current) {
        MaterialTheme.nuvio.colors.textPrimary.copy(alpha = NuvioTokens.Opacity.hover)
    } else {
        default
    }

private val backdropCache = linkedMapOf<String, ImageBitmap>()
private val backdropScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Black.copy(alpha = 0.64f),
)

@Composable
internal fun rememberArtworkBackdrop(imageUrl: String?, enabled: Boolean = true): ImageBitmap? {
    val context = LocalPlatformContext.current
    var image by remember(imageUrl) { mutableStateOf(backdropCache[imageUrl]) }

    LaunchedEffect(imageUrl, enabled, context) {
        if (!enabled || imageUrl.isNullOrBlank() || image != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128)
            .precision(Precision.EXACT)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return@LaunchedEffect
        val blurred = withContext(Dispatchers.Default) {
            runCatching { prepareArtworkBackdrop(result.image) }.getOrNull()
        } ?: return@LaunchedEffect
        backdropCache[imageUrl] = blurred
        if (backdropCache.size > 8) backdropCache.remove(backdropCache.keys.first())
        image = blurred
    }

    return image
}

@Composable
internal fun ArtworkBackdrop(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(
            targetState = image,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "artwork_backdrop",
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    alignment = alignment,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(backdropScrim))
    }
}

internal fun Modifier.featherArtwork(
    edgeMask: (CacheDrawScope.() -> Brush)? = null,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val fade = Brush.verticalGradient(
        colorStops = Array(17) { index ->
            val t = index / 16f
            val progress = t * t * t * (t * (t * 6f - 15f) + 10f)
            t to Color.Black.copy(alpha = 1f - progress)
        },
        startY = size.height * 0.58f,
        endY = size.height,
    )
    val extraFade = edgeMask?.invoke(this)
    onDrawWithContent {
        drawContent()
        drawRect(fade, blendMode = BlendMode.DstIn)
        if (extraFade != null) drawRect(extraFade, blendMode = BlendMode.DstIn)
    }
}

internal expect fun prepareArtworkBackdrop(image: coil3.Image): ImageBitmap

/**
 * Mean relative luminance (0–1) of a backdrop bitmap, with the top 40% (where the top bar and page
 * headers sit) counted twice. Backdrops are ~128 px, so a full pass is cheap.
 */
internal fun artworkLuminance(image: ImageBitmap): Float {
    if (image.width <= 0 || image.height <= 0) return 0f
    val pixels = image.toPixelMap()
    val chromeRows = (image.height * 0.4f).toInt()
    var total = 0.0
    var weight = 0.0
    for (y in 0 until image.height) {
        val w = if (y < chromeRows) 2.0 else 1.0
        for (x in 0 until image.width) {
            total += w * relativeLuminance(pixels[x, y])
            weight += w
        }
    }
    return (total / weight).toFloat()
}

/** WCAG relative luminance of an sRGB colour (alpha ignored). */
internal fun relativeLuminance(color: androidx.compose.ui.graphics.Color): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

internal fun blurBackdropPixels(pixels: IntArray, width: Int, height: Int) {
    require(width > 0 && height > 0 && pixels.size == width * height)
    val scratch = IntArray(pixels.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)
    repeat(3) {
        blurBackdropPass(pixels, scratch, width, height, radius, horizontal = true)
        blurBackdropPass(scratch, pixels, width, height, radius, horizontal = false)
    }
}

private fun blurBackdropPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val stride = if (horizontal) 1 else width
    val count = radius * 2 + 1

    repeat(lines) { line ->
        val start = if (horizontal) line * width else line
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = source[start + offset.coerceIn(0, length - 1) * stride]
            alpha += pixel ushr 24
            red += (pixel ushr 16) and 255
            green += (pixel ushr 8) and 255
            blue += pixel and 255
        }
        repeat(length) { position ->
            target[start + position * stride] = ((alpha / count) shl 24) or
                ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            val leaving = source[start + (position - radius).coerceAtLeast(0) * stride]
            val entering = source[start + (position + radius + 1).coerceAtMost(length - 1) * stride]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering ushr 16) and 255) - ((leaving ushr 16) and 255)
            green += ((entering ushr 8) and 255) - ((leaving ushr 8) and 255)
            blue += (entering and 255) - (leaving and 255)
        }
    }
}
