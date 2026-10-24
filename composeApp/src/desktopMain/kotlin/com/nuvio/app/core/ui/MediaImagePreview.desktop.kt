package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import coil3.BitmapImage
import coil3.Image
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.MipmapMode
import kotlin.math.roundToInt

/** Byte bounded, separate from Coil's full-resolution cache. Never retains full-size source images. */
internal object DesktopMediaImagePreviews {
    private const val budget = 128L * 1024 * 1024
    private data class Entry(val bitmap: ImageBitmap, val displayed: MutableSet<String> = mutableSetOf()) {
        val bytes get() = bitmap.width.toLong() * bitmap.height * 4
    }
    private val entries = linkedMapOf<String, Entry>()
    private var bytes = 0L
    private fun key(request: ImageRequest?): String? = request?.memoryCacheKey
        ?.takeIf { it.startsWith("nuvio-media:") }?.let { it + request.memoryCacheKeyExtras.toSortedMap() }
    @Synchronized fun get(request: ImageRequest?, identity: String): MediaImagePreview? {
        val key = key(request) ?: return null
        val entry = entries.remove(key) ?: return null
        entries[key] = entry
        return MediaImagePreview(BitmapPainter(entry.bitmap), identity in entry.displayed)
    }
    @Synchronized fun displayed(request: ImageRequest?, identity: String) { entries[key(request)]?.displayed?.add(identity) }
    @Synchronized fun contains(request: ImageRequest): Boolean = key(request) in entries
    @Synchronized fun put(request: ImageRequest, bitmap: ImageBitmap) {
        val key = key(request) ?: return
        val old = entries.remove(key)
        if (old != null) bytes -= old.bytes
        val entry = Entry(bitmap, old?.displayed ?: mutableSetOf())
        entries[key] = entry; bytes += entry.bytes
        while (bytes > budget && entries.isNotEmpty()) bytes -= entries.remove(entries.keys.first())!!.bytes
    }
}

internal actual fun cachedMediaImagePreview(request: ImageRequest?, identity: String): MediaImagePreview? = DesktopMediaImagePreviews.get(request, identity)
internal actual fun markMediaImageDisplayed(request: ImageRequest?, identity: String) = DesktopMediaImagePreviews.displayed(request, identity)
internal actual suspend fun cacheMediaImagePreview(request: ImageRequest, image: Image) {
    if (request.memoryCacheKey?.startsWith("nuvio-media:") != true || DesktopMediaImagePreviews.contains(request)) return
    val source = (image as? BitmapImage)?.bitmap ?: return
    withContext(Dispatchers.Default) {
        val longest = if (request.memoryCacheKey?.startsWith("nuvio-media:logo:") == true) 128 else 192
        val scale = minOf(1.0, longest.toDouble() / maxOf(source.width, source.height))
        val target = Bitmap().apply { allocN32Pixels((source.width * scale).roundToInt().coerceAtLeast(1), (source.height * scale).roundToInt().coerceAtLeast(1)) }
        SkiaImage.makeFromBitmap(source).use { original ->
            original.scalePixels(target.peekPixels()!!, FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), false)
        }
        DesktopMediaImagePreviews.put(request, target.asComposeImageBitmap())
    }
}
