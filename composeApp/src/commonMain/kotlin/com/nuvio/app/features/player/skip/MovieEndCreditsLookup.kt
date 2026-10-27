package com.nuvio.app.features.player.skip

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.network.readBoundedResponseBody
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** End-credit lookup shared with the player; each provider is bounded and reads require no API key. */
internal class MovieEndCreditsLookup(
    private val primary: suspend (String) -> IntroDbSegmentsResponse? = {
        SkipIntroApi.getIntroDbMovieSegments(it, "https://api.introdb.app", ::fetchMovieCreditText)
    },
    private val backup: suspend (String) -> String = ::fetchMovieCreditText,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<Pair<String, Long>, Long?>()

    suspend fun startMs(imdbId: String, durationMs: Long): Long? {
        val key = imdbId to durationMs
        if (cache.containsKey(key)) return cache[key]
        val primaryStart = attempt {
            primary(imdbId)?.movieSkipIntervals()?.firstOrNull { it.type == "movie-credits" }
                ?.startTime?.times(1000)?.toLong()?.takeIf { validCreditStart(it, durationMs) }
        }
        val start = primaryStart ?: attempt {
            val response = json.decodeFromString<TheIntroDbMovieCredits>(backup(theIntroDbMovieCreditsUrl(imdbId, durationMs)))
            response.startMs(durationMs)
        }
        if (cache.size >= 64) cache.clear()
        cache[key] = start
        return start
    }

    private suspend fun attempt(block: suspend () -> Long?): Long? = try {
        withTimeoutOrNull(5_000L) { block() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

internal fun theIntroDbMovieCreditsUrl(imdbId: String, durationMs: Long): String =
    "https://api.theintrodb.org/v4/media?imdb_id=$imdbId&duration_ms=$durationMs"

private fun validCreditStart(start: Long, durationMs: Long): Boolean =
    start >= 0 && (durationMs <= 0 || start < durationMs)

@Serializable
internal data class TheIntroDbMovieCredits(
    val type: String? = null,
    val credits: List<TheIntroDbCredit> = emptyList(),
) {
    fun startMs(durationMs: Long): Long? = credits.takeIf { type == "movie" }
        ?.mapNotNull { credit ->
            credit.startMs?.takeIf { start ->
                validCreditStart(start, durationMs) && (credit.endMs == null || credit.endMs > start)
            }
        }?.minOrNull()
}

@Serializable
internal data class TheIntroDbCredit(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
)

private val movieCreditsHttpClient by lazy { createApiHttpClient() }

/** Use the cancellable API transport: the desktop addon transport performs blocking reads. */
internal suspend fun fetchMovieCreditText(url: String): String {
    val response = movieCreditsHttpClient.get(url) {
        timeout {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 5_000
        }
    }
    check(response.status.value in 200..299) { "Credits lookup HTTP ${response.status.value}" }
    return readBoundedResponseBody(response.bodyAsChannel(), response.contentLength(), maxBytes = 128 * 1024)
}
