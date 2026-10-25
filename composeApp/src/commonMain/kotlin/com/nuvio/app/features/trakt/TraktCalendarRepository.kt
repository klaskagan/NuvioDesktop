package com.nuvio.app.features.trakt

import com.nuvio.app.core.time.*
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val TRAKT_CALENDAR_KEY = "native:trakt:calendar"

enum class CalendarProblem { DISCONNECTED, AUTHENTICATION, NETWORK }
data class CalendarState(
    val loading: Boolean = false,
    val episodes: List<CalendarEpisode> = emptyList(),
    val movies: List<CalendarMovie> = emptyList(),
    val cards: List<CalendarCard> = emptyList(),
    val problem: CalendarProblem? = null,
    val partial: Boolean = false,
)
data class CalendarBatch(val episodes: List<CalendarEpisode> = emptyList(), val movies: List<CalendarMovie> = emptyList())
internal class CalendarAuthenticationException : Exception()

/** Injectable transport and clock allow isolated tests without a real account. */
class CalendarRepository(
    private val account: () -> String?,
    private val fetch: suspend (Boolean, String, Int) -> CalendarBatch,
    private val now: () -> Long = EpisodeReleaseDatePlatform::nowEpochMs,
    private val localDate: (Long) -> String? = EpisodeReleaseDatePlatform::localIsoDateAtEpochMs,
    private val timezone: () -> String = EpisodeReleaseDatePlatform::timezoneId,
) {
    private val mutableState = MutableStateFlow(CalendarState(problem = CalendarProblem.DISCONNECTED))
    val state: StateFlow<CalendarState> = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val publicationLock = SynchronizedObject()
    private val generation = kotlinx.atomicfu.atomic(0L)
    private var currentAccount: String? = null
    private var cachedWindow: String? = null
    private var updatedAt = 0L

    fun invalidate() = synchronized(publicationLock) {
        generation.incrementAndGet()
        currentAccount = account()
        cachedWindow = null
        updatedAt = 0L
        mutableState.value = CalendarState(problem = if (currentAccount == null) CalendarProblem.DISCONNECTED else null)
    }

    suspend fun load(days: Int = 14, force: Boolean = false, startDate: String? = null) = mutex.withLock {
        require(days in 1..93)
        val owner = account()
        if (owner != currentAccount) invalidate()
        if (owner == null) return@withLock
        val requestGeneration = generation.value
        val today = localDate(now()) ?: return@withLock
        val rangeStart = startDate?.let { requireNotNull(parseIsoCalendarDate(it)) } ?: today
        val window = "$today|$rangeStart|$days|${timezone()}"
        if (!force && window == cachedWindow && now() - updatedAt < 10 * 60_000L) return@withLock
        synchronized(publicationLock) {
            if (account() != owner || generation.value != requestGeneration) return@withLock
            mutableState.value = mutableState.value.copy(loading = true, problem = null)
        }
        val (start, count) = calendarUtcRequestWindow(rangeStart, days)
        suspend fun request(shows: Boolean): Result<CalendarBatch> = try {
            Result.success(fetch(shows, start, count))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(error) }
        try {
            val results = coroutineScope {
                val shows = async { request(true) }
                val movies = async { request(false) }
                shows.await() to movies.await()
            }
            if (account() != owner || currentAccount != owner || generation.value != requestGeneration) return@withLock
            val authFailure = listOf(results.first, results.second).any { it.exceptionOrNull() is CalendarAuthenticationException }
            if (authFailure) {
                synchronized(publicationLock) {
                    if (account() == owner && generation.value == requestGeneration) {
                        mutableState.value = CalendarState(problem = CalendarProblem.AUTHENTICATION)
                        cachedWindow = null
                    }
                }
                return@withLock
            }
            val episodes = results.first.getOrNull()?.episodes ?: mutableState.value.episodes
            val movies = results.second.getOrNull()?.movies ?: mutableState.value.movies
            val failed = results.first.isFailure || results.second.isFailure
            val nextState = CalendarState(
                episodes = episodes, movies = movies,
                cards = buildCalendarCards(episodes, movies, today, days, localDate, rangeStart),
                problem = if (failed) CalendarProblem.NETWORK else null,
                partial = failed && (results.first.isSuccess || results.second.isSuccess),
            )
            synchronized(publicationLock) {
                if (account() != owner || generation.value != requestGeneration) return@withLock
                mutableState.value = nextState
                cachedWindow = if (failed) null else window
                updatedAt = now()
            }
        } catch (error: CancellationException) {
            synchronized(publicationLock) {
                if (account() == owner && generation.value == requestGeneration)
                    mutableState.value = mutableState.value.copy(loading = false)
            }
            throw error
        }
    }
}

object TraktCalendarRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = CalendarTransportCache(
        account = { TraktAuthRepository.uiState.value.calendarAccountKey },
        fetch = { shows, start, days ->
            val owner = TraktAuthRepository.uiState.value.calendarAccountKey
            val headers = TraktAuthRepository.authorizedHeaders() ?: throw CalendarAuthenticationException()
            if (owner != TraktAuthRepository.uiState.value.calendarAccountKey) throw CancellationException()
            val response = httpRequestRaw("GET",
                "https://api.trakt.tv/calendars/my/${if (shows) "shows" else "movies"}/$start/$days?extended=full,images",
                mapOf("Accept" to "application/json") + headers, "")
            if (response.status == 401 || response.status == 403) throw CalendarAuthenticationException()
            check(response.status in 200..299) { "Calendar request failed (${response.status})" }
            decodeCalendarBatch(response.body, shows, json)
        },
    )
    private fun engine() = CalendarRepository(
        account = { TraktAuthRepository.uiState.value.calendarAccountKey }, fetch = cache::load)
    private val repository = engine()
    private val sessions = mutableSetOf<CalendarRepository>()
    private val lock = SynchronizedObject()
    val uiState: StateFlow<CalendarState> = repository.state
    /** Each retained screen owns its observable range; transport/cache are shared with Home. */
    fun createSession(): CalendarRepository = synchronized(lock) { engine().also { sessions.add(it) } }
    fun releaseSession(session: CalendarRepository) = synchronized(lock) { sessions.remove(session); session.invalidate() }
    fun invalidate() = synchronized(lock) {
        cache.invalidate()
        repository.invalidate()
        sessions.forEach { it.invalidate() }
    }
    fun expireCache() = cache.expire()
    suspend fun load(days: Int = 14, force: Boolean = false) {
        if (force) cache.expire()
        repository.load(days, force)
    }
    suspend fun loadRange(startDate: String, days: Int, force: Boolean = false) = repository.load(days, force, startDate)
}

@Serializable
internal data class CalendarMediaDto(
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val year: Int? = null,
    val ids: TraktExternalIds = TraktExternalIds(),
    val images: TraktImagesDto? = null,
) {
    fun media(type: String): CalendarMedia? {
        val id = normalizeTraktContentId(ids).takeIf { it.isNotBlank() } ?: return null
        return CalendarMedia(id, type, title?.takeIf { it.isNotBlank() } ?: originalTitle.orEmpty(), year,
            images.traktBestPosterUrl(), images.traktBestLandscapeUrl(), ids.trakt, ids.tmdb, ids.imdb, ids.tvdb)
    }
}
@Serializable
internal data class CalendarEpisodeDto(
    val ids: TraktExternalIds = TraktExternalIds(),
    val season: Int? = null,
    val number: Int? = null,
    val title: String? = null,
    @SerialName("episode_type") val episodeType: String? = null,
    val images: CalendarEpisodeImages? = null,
)
@Serializable
internal data class CalendarEpisodeImages(val screenshot: List<String>? = null)
@Serializable
internal data class CalendarReleaseDto(
    @SerialName("first_aired") val firstAired: String? = null,
    val released: String? = null,
    val show: CalendarMediaDto? = null,
    val episode: CalendarEpisodeDto? = null,
    val movie: CalendarMediaDto? = null,
)
internal fun decodeCalendarBatch(body: String, shows: Boolean, json: Json = Json { ignoreUnknownKeys = true }): CalendarBatch {
    val rows = json.decodeFromString<List<CalendarReleaseDto>>(body)
    if (!shows) return CalendarBatch(movies = rows.mapNotNull { row ->
        val movie = row.movie?.media("movie") ?: return@mapNotNull null
        val release = row.released ?: return@mapNotNull null
        if (parseIsoCalendarDate(release) == null && parseZonedIsoDateTimeToEpochMs(release) == null) return@mapNotNull null
        CalendarMovie(movie, release, parseZonedIsoDateTimeToEpochMs(release))
    })
    return CalendarBatch(episodes = rows.mapNotNull { row ->
        val show = row.show?.media("series") ?: return@mapNotNull null
        val episode = row.episode ?: return@mapNotNull null
        val season = episode.season ?: return@mapNotNull null
        val number = episode.number ?: return@mapNotNull null
        val release = row.firstAired ?: return@mapNotNull null
        val instant = parseZonedIsoDateTimeToEpochMs(release) ?: return@mapNotNull null
        val id = normalizeTraktContentId(episode.ids).ifBlank { "${show.id}:$season:$number" }
        CalendarEpisode(id, show, season, number, episode.title, release, instant,
            episode.episodeType, episode.images?.screenshot.firstTraktImageUrl(), episode.ids.trakt, episode.ids.tmdb, episode.ids.imdb, episode.ids.tvdb)
    })
}
