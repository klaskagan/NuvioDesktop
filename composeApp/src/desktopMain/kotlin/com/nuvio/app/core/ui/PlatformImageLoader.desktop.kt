package com.nuvio.app.core.ui

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import com.nuvio.app.core.storage.DesktopStorage
import okio.Path.Companion.toOkioPath

private const val ImageMemoryCacheBytes = 256L * 1024 * 1024
private val desktopImageDiskCache by lazy {
    DiskCache.Builder().directory(DesktopStorage.cacheDir.resolve("images").toOkioPath())
        .maxSizeBytes(512L * 1024 * 1024).build()
}

internal actual val platformProvidesImageLoader: Boolean = false

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder {
    return diskCache { desktopImageDiskCache }.memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(ImageMemoryCacheBytes)
            .build()
    }
}

internal actual fun ComponentRegistry.Builder.addPlatformDecoders() {
    add(SkiaGifDecoder.Factory())
    add(SvgEmbeddedImageDecoder.Factory())
}
