package com.nuvio.app.features.tmdb

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object TmdbService {
    private val idResolver = TmdbIdResolver { url -> httpGetText(url) }

    suspend fun ensureTmdbId(videoId: String, mediaType: String, fallbackImdbId: String? = null): String? =
        idResolver.ensureTmdbId(videoId, mediaType, TmdbSettingsRepository.effectiveApiKey(), fallbackImdbId)

    suspend fun tmdbToImdb(tmdbId: Int, mediaType: String): String? =
        idResolver.tmdbToImdb(tmdbId, mediaType, TmdbSettingsRepository.effectiveApiKey())

    internal fun normalizeMediaType(mediaType: String): String =
        when (mediaType.trim().lowercase()) {
            "movie", "film" -> "movie"
            "tv", "series", "show", "tvshow" -> "tv"
            else -> mediaType.trim().lowercase()
        }
}

/** Share ID lookup responses across artwork, details and cards, independently of their lifetimes. */
internal class TmdbIdResolver(private val requestText: suspend (String) -> String) {
    private val log = Logger.withTag("TmdbService")
    private val json = Json { ignoreUnknownKeys = true }
    private val imdbToTmdbCache = linkedMapOf<String, String>()
    private val tmdbToImdbCache = linkedMapOf<String, String>()
    private val cacheMutex = Mutex()
    // /find returns both movie and TV results: one response can satisfy either media type.
    private val findResponses = LandscapeArtworkCache<TmdbFindResponse>(capacity = 1024)
    private val externalIdRequests = LandscapeArtworkCache<String>(capacity = 1024)

    suspend fun ensureTmdbId(videoId: String, mediaType: String, apiKey: String, fallbackImdbId: String? = null): String? {
        val normalized = videoId
            .removePrefix("tmdb:")
            .removePrefix("movie:")
            .removePrefix("series:")
            .substringBefore(':')
            .substringBefore('/')
            .trim()

        if (normalized.isBlank()) return null
        if (normalized.all(Char::isDigit)) return normalized
        if (normalized.startsWith("tt", ignoreCase = true)) {
            return imdbToTmdb(imdbId = normalized, mediaType = mediaType, apiKey = apiKey)
        }

        // Fallback: use the IMDB ID supplied by the addon's meta response
        val normalizedFallback = fallbackImdbId
            ?.trim()
            ?.substringBefore(':')
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (normalizedFallback != null) {
            return imdbToTmdb(imdbId = normalizedFallback, mediaType = mediaType, apiKey = apiKey)
        }

        return null
    }

    suspend fun tmdbToImdb(tmdbId: Int, mediaType: String, apiKey: String): String? {
        val cacheKey = "$tmdbId:${normalizeMediaType(mediaType)}"
        cacheMutex.withLock {
            tmdbToImdbCache[cacheKey]?.let { return it }
        }

        val endpoint = when (normalizeMediaType(mediaType)) {
            "tv" -> "tv/$tmdbId/external_ids"
            else -> "movie/$tmdbId/external_ids"
        }
        val imdbId = externalIdRequests.get("$cacheKey:$apiKey") {
            fetch<TmdbExternalIdsResponse>(endpoint = endpoint, apiKey = apiKey)
                ?.imdbId?.trim()?.takeIf(String::isNotBlank)
        } ?: return null

        cacheMutex.withLock {
            tmdbToImdbCache[cacheKey] = imdbId
            imdbToTmdbCache["$imdbId:${normalizeMediaType(mediaType)}"] = tmdbId.toString()
            trimMappings()
        }
        return imdbId
    }

    private suspend fun imdbToTmdb(imdbId: String, mediaType: String, apiKey: String): String? {
        val normalizedType = normalizeMediaType(mediaType)
        val cacheKey = "$imdbId:$normalizedType"
        cacheMutex.withLock {
            imdbToTmdbCache[cacheKey]?.let { return it }
        }

        val body = findResponses.get("${imdbId.lowercase()}:$apiKey") {
            fetch<TmdbFindResponse>(
                endpoint = "find/$imdbId",
                apiKey = apiKey,
                query = mapOf("external_source" to "imdb_id"),
            )?.takeUnless { it.movieResults.isEmpty() && it.tvResults.isEmpty() }
        } ?: return null

        val resultId = when (normalizedType) {
            "movie" -> body.movieResults.firstOrNull()?.id
            "tv" -> body.tvResults.firstOrNull()?.id
            else -> body.movieResults.firstOrNull()?.id ?: body.tvResults.firstOrNull()?.id
        }?.takeIf { it > 0 }?.toString()

        if (resultId != null) {
            cacheMutex.withLock {
                imdbToTmdbCache[cacheKey] = resultId
                tmdbToImdbCache["$resultId:$normalizedType"] = imdbId
                trimMappings()
            }
        } else {
            log.d { "No TMDB ID found for $imdbId ($normalizedType)" }
        }

        return resultId
    }

    private suspend inline fun <reified T> fetch(
        endpoint: String,
        apiKey: String,
        query: Map<String, String> = emptyMap(),
    ): T? {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        return try {
            json.decodeFromString<T>(requestText(url))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w { "TMDB request failed for $endpoint: ${error.message}" }
            null
        }
    }

    private fun trimMappings() {
        while (imdbToTmdbCache.size > 2048) imdbToTmdbCache.remove(imdbToTmdbCache.keys.first())
        while (tmdbToImdbCache.size > 2048) tmdbToImdbCache.remove(tmdbToImdbCache.keys.first())
    }

    private fun normalizeMediaType(mediaType: String): String = TmdbService.normalizeMediaType(mediaType)

}

internal fun buildTmdbUrl(
    endpoint: String,
    apiKey: String,
    query: Map<String, String> = emptyMap(),
): String {
    val params = linkedMapOf("api_key" to apiKey)
    query.forEach { (key, value) ->
        if (value.isNotBlank()) {
            params[key] = value
        }
    }
    return buildString {
        append("https://api.themoviedb.org/3/")
        append(endpoint.removePrefix("/"))
        if (params.isNotEmpty()) {
            append("?")
            append(params.entries.joinToString("&") { (key, value) -> "$key=$value" })
        }
    }
}

@Serializable
private data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbExternalResult> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbExternalResult> = emptyList(),
)

@Serializable
private data class TmdbExternalResult(
    val id: Int,
)

@Serializable
private data class TmdbExternalIdsResponse(
    @SerialName("imdb_id") val imdbId: String? = null,
)
