package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.painter.Painter
import coil3.Image
import coil3.request.ImageRequest

/** A small, already-displayed image survives eviction of full-size decoded artwork. */
internal data class MediaImagePreview(val painter: Painter, val wasDisplayed: Boolean)
internal expect fun cachedMediaImagePreview(request: ImageRequest?, identity: String = ""): MediaImagePreview?
internal expect fun markMediaImageDisplayed(request: ImageRequest?, identity: String = "")
internal expect suspend fun cacheMediaImagePreview(request: ImageRequest, image: Image)
