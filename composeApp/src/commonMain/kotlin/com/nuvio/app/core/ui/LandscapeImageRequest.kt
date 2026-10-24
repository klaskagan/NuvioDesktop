package com.nuvio.app.core.ui

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale

/** The prefetcher and renderer must use the same decoded size and scale to share memory entries. */
internal fun landscapeImageRequest(context: PlatformContext, url: String, logo: Boolean = false, fallback: String? = null): ImageRequest =
    ImageRequest.Builder(context).data(url)
        .memoryCacheKey("nuvio-media:${if (logo) "logo" else "landscape"}:$url")
        .size(if (logo) 500 else 780, if (logo) 250 else 439)
        .scale(if (logo) Scale.FIT else Scale.FILL)
        .crossfade(false)
        .apply {
            if (!fallback.isNullOrBlank() && fallback != url) memoryCacheKeyExtras(
                mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallback)
            )
        }.build()

/** Shared decoded request sizes for media rows in every poster orientation. */
internal fun posterImageRequest(context: PlatformContext, url: String, shape: NuvioPosterShape, fallback: String? = null): ImageRequest {
    if (shape == NuvioPosterShape.Landscape) return landscapeImageRequest(context, url, fallback = fallback)
    return ImageRequest.Builder(context).data(url)
        .memoryCacheKey("nuvio-media:$shape:$url")
        .size(500, if (shape == NuvioPosterShape.Poster) 741 else 500)
        .scale(Scale.FILL).crossfade(false)
        .apply {
            if (!fallback.isNullOrBlank() && fallback != url) memoryCacheKeyExtras(
                mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallback)
            )
        }.build()
}
