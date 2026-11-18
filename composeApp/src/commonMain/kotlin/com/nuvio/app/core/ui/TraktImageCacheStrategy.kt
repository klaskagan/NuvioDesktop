package com.nuvio.app.core.ui

import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.request.Options
import io.ktor.http.Url

/** Trakt requires downloaded artwork to be cached locally, including CDN responses
 * whose HTTP cache headers would otherwise bypass the image disk cache. */
@OptIn(ExperimentalCoilApi::class)
internal class TraktImageCacheStrategy : CacheStrategy {
    private val httpCache = CacheControlCacheStrategy()

    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult {
        val strategy = if (isTraktArtworkUrl(networkRequest.url) && cacheResponse.code in 200..299) {
            CacheStrategy.DEFAULT
        } else {
            httpCache
        }
        return strategy.read(cacheResponse, networkRequest, options)
    }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult {
        if (isTraktArtworkUrl(networkRequest.url)) {
            return if (networkResponse.code in 200..299 || networkResponse.code == 304) {
                CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
            } else {
                CacheStrategy.WriteResult.DISABLED
            }
        }
        return httpCache.write(cacheResponse, networkRequest, networkResponse, options)
    }
}

internal fun isTraktArtworkUrl(value: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    val host = url.host.lowercase()
    return url.protocol.name == "https" &&
        (host == "trakt.tv" || host.endsWith(".trakt.tv")) &&
        url.encodedPath.startsWith("/images/")
}
