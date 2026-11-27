package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The visible part of a loaded logo, its transparent border cut away. */
internal class TrimmedLogo(val painter: BitmapPainter, val aspectRatio: Float)

/** What a logo load ended in: the trimmed logo, or a failure (draw the name instead). */
internal sealed interface TrimmedLogoState {
    data object Loading : TrimmedLogoState
    data object Failed : TrimmedLogoState
    class Ready(val logo: TrimmedLogo) : TrimmedLogoState
}

/** Converts a loaded image to pixels this module can read (platform bitmap types differ). */
internal expect fun logoImageBitmap(image: coil3.Image): ImageBitmap

/**
 * Bounds of the pixels that are not (nearly) transparent, as left, top, right, bottom (exclusive).
 * Null when nothing is visible. Service logos often sit in a large transparent canvas, so a logo
 * sized by its box showed at half its intended height (Crunchyroll, Netflix).
 */
internal fun opaqueBounds(argb: IntArray, width: Int, height: Int, alphaThreshold: Int = 8): IntArray? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((argb[row + x] ushr 24) > alphaThreshold) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < 0) return null
    return intArrayOf(left, top, right + 1, bottom + 1)
}

private val trimmedLogoCache = LinkedHashMap<String, TrimmedLogo>()

/**
 * Loads [url] and cuts away its transparent border once (cached by URL), so a logo drawn at a
 * given height shows its artwork at that height whatever padding the image file carries.
 */
@Composable
internal fun rememberTrimmedLogo(url: String?): TrimmedLogoState {
    val context = LocalPlatformContext.current
    var state by remember(url) {
        mutableStateOf<TrimmedLogoState>(
            when {
                url.isNullOrBlank() -> TrimmedLogoState.Failed
                else -> trimmedLogoCache[url]?.let { TrimmedLogoState.Ready(it) } ?: TrimmedLogoState.Loading
            },
        )
    }
    LaunchedEffect(url, context) {
        if (url.isNullOrBlank() || state !is TrimmedLogoState.Loading) return@LaunchedEffect
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).build()) as? SuccessResult
        val logo = result?.let {
            withContext(Dispatchers.Default) { runCatching { trimLogo(logoImageBitmap(it.image)) }.getOrNull() }
        }
        if (logo == null) {
            state = TrimmedLogoState.Failed
            return@LaunchedEffect
        }
        trimmedLogoCache[url] = logo
        if (trimmedLogoCache.size > 32) trimmedLogoCache.remove(trimmedLogoCache.keys.first())
        state = TrimmedLogoState.Ready(logo)
    }
    return state
}

private fun trimLogo(bitmap: ImageBitmap): TrimmedLogo? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels)
    val (left, top, right, bottom) = opaqueBounds(pixels, width, height)?.toList() ?: return null
    val size = IntSize(right - left, bottom - top)
    return TrimmedLogo(BitmapPainter(bitmap, IntOffset(left, top), size), size.width.toFloat() / size.height)
}
