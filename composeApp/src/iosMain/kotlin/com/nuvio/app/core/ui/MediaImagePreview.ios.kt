package com.nuvio.app.core.ui

import coil3.Image
import coil3.request.ImageRequest

internal actual fun cachedMediaImagePreview(request: ImageRequest?, identity: String): MediaImagePreview? = null
internal actual fun markMediaImageDisplayed(request: ImageRequest?, identity: String) = Unit
internal actual suspend fun cacheMediaImagePreview(request: ImageRequest, image: Image) = Unit
