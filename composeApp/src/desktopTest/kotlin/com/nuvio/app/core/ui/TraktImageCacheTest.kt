package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.network.*
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Path.Companion.toOkioPath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalCoilApi::class)
class TraktImageCacheTest {
    @Test
    fun reusesTraktArtworkFromDiskAcrossLoaderInstancesEvenWithNoStoreHeaders() = runBlocking {
        val directory = Files.createTempDirectory("trakt-image-cache").toFile()
        val png = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        var requests = 0
        val client = object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                requests++
                return block(NetworkResponse(
                    headers = NetworkHeaders.Builder()
                        .set("Content-Type", "image/png")
                        .set("Cache-Control", "no-store")
                        .build(),
                    body = NetworkResponseBody(Buffer().write(png)),
                ))
            }
        }
        suspend fun load(): DataSource {
            val cache = DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(1024 * 1024).build()
            val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
                .diskCache(cache)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .components {
                    add(NetworkFetcher.Factory(networkClient = { client }, cacheStrategy = { TraktImageCacheStrategy() }))
                }.build()
            try {
                val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data("https://walter-r2.trakt.tv/images/movies/example.webp")
                    .build())
                assertIs<SuccessResult>(result)
                return result.dataSource
            } finally {
                loader.shutdown()
                cache.shutdown()
            }
        }
        try {
            assertEquals(DataSource.NETWORK, load())
            assertEquals(DataSource.DISK, load())
            assertEquals(1, requests)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun onlyTraktArtworkUsesTheMandatoryCachePolicy() {
        assertTrue(isTraktArtworkUrl("https://walter-r2.trakt.tv/images/a.webp"))
        assertFalse(isTraktArtworkUrl("https://api.trakt.tv/sync/history"))
        assertFalse(isTraktArtworkUrl("https://trakt.tv.example.com/images/a.webp"))
        assertFalse(isTraktArtworkUrl("https://example.com/images/a.webp"))
    }
}
