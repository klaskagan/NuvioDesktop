package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.http.fromHttpToGmtDate

internal data class AudienceEpisodeKey(val season: Int, val episode: Int) {
    override fun toString(): String = "S${season}E${episode}"
}

@Serializable
internal data class TraktAudienceEpisode(
    val season: Int? = null,
    val number: Int,
    val title: String? = null,
    @SerialName("first_aired") val firstAired: String? = null,
    val rating: Double? = null,
    val votes: Long? = null,
    val ids: TraktExternalIds? = null,
)

@Serializable
internal data class TraktAudienceSeason(val number: Int, val episodes: List<TraktAudienceEpisode> = emptyList())

@Serializable
internal data class TraktAudienceStats(
    val watchers: Long? = null,
    val plays: Long? = null,
    val comments: Long? = null,
    val votes: Long? = null,
)

@Serializable
private data class AudienceShow(val ids: TraktExternalIds)

@Serializable
private data class AudienceSearchResult(val show: AudienceShow? = null)

internal class AudienceRequestException(val status: Int) : Exception("Trakt audience request failed: $status")

internal object TraktAudienceRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Any?>()
    private val cacheLock = Mutex()
    private val lanes = List(32) { Mutex() }
    private val requests = Semaphore(2)
    private var resumeAt = 0L

    val available: Boolean get() = TraktConfig.CLIENT_ID.isNotBlank()

    suspend fun resolveShow(meta: MetaDetails): Int? {
        val ids = parseTraktContentIds(meta.id)
        if (meta.id.startsWith("trakt:", ignoreCase = true)) return ids.trakt
        val imdb = (meta.imdbId ?: ids.imdb)?.takeIf { it.matches(Regex("tt[0-9]+")) }
        return when {
            imdb != null -> get<AudienceShow>("/shows/$imdb")?.ids?.trakt
            ids.tmdb != null -> get<List<AudienceSearchResult>>("/search/tmdb/${ids.tmdb}?type=show")
                ?.mapNotNull { it.show?.ids?.trakt }?.distinct()?.singleOrNull()
            else -> null
        }
    }

    suspend fun seasons(show: Int): List<TraktAudienceSeason> =
        get<List<TraktAudienceSeason>>("/shows/$show/seasons?extended=episodes,full").orEmpty()

    suspend fun showStats(show: Int): TraktAudienceStats? = get("/shows/$show/stats")

    suspend fun episodeStats(show: Int, episode: AudienceEpisodeKey): TraktAudienceStats? =
        get("/shows/$show/seasons/${episode.season}/episodes/${episode.episode}/stats")

    // Each endpoint has one response type. Null is a cached 404.
    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <reified T> get(path: String): T? =
        lanes[(path.hashCode() and Int.MAX_VALUE) % lanes.size].withLock {
            cacheLock.withLock { if (path in cache) return cache[path] as T? }
            val payload = request(path)
            val parsed = payload?.let { withContext(Dispatchers.Default) { json.decodeFromString<T>(it) } }
            cacheLock.withLock { cache[path] = parsed }
            parsed
        }

    private suspend fun request(path: String): String? {
        var retried = false
        while (true) {
            val wait = cacheLock.withLock { resumeAt - WatchProgressClock.nowEpochMs() }
            if (wait > 0) delay(wait)
            requests.withPermit {
                if (cacheLock.withLock { resumeAt > WatchProgressClock.nowEpochMs() }) return@withPermit
                val response = httpRequestRaw(
                    method = "GET",
                    url = "https://api.trakt.tv$path",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Content-Type" to "application/json",
                        "trakt-api-version" to "2",
                        "trakt-api-key" to TraktConfig.CLIENT_ID,
                    ),
                    body = "",
                    maxResponseBodyBytes = 8 * 1024 * 1024,
                )
                when (response.status) {
                    in 200..299 -> return response.body
                    404 -> return null
                    429 -> {
                        val retryAfter = response.headers.entries
                            .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value
                        val now = WatchProgressClock.nowEpochMs()
                        val deadline = retryAfter?.let { audienceRetryDeadline(it, now) }
                            ?: throw AudienceRequestException(429)
                        cacheLock.withLock { resumeAt = maxOf(resumeAt, deadline) }
                        if (retried) throw AudienceRequestException(429)
                        retried = true
                    }
                    else -> throw AudienceRequestException(response.status)
                }
            }
        }
    }
}


internal fun audienceRetryDeadline(value: String, now: Long): Long? {
    val seconds = value.trim().toLongOrNull()
    if (seconds != null) {
        return seconds.takeIf { it >= 0 && it <= (Long.MAX_VALUE - now) / 1000 }
            ?.let { now + it * 1000 }
    }
    return runCatching { value.fromHttpToGmtDate().timestamp }.getOrNull()
}
