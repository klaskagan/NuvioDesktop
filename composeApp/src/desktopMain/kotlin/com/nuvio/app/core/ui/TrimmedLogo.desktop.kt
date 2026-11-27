package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.toBitmap

internal actual fun logoImageBitmap(image: Image): ImageBitmap = image.toBitmap().asComposeImageBitmap()
