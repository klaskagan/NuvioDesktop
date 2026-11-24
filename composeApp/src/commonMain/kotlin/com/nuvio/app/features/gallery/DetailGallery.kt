package com.nuvio.app.features.gallery

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tmdb.TmdbGalleryKind
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What an image in the gallery is, which is also its filter. */
enum class GalleryKind { Backdrop, EpisodeStill, Poster, Still, BehindTheScenes }

enum class GallerySource(val label: String) { Tmdb("TMDB"), Kinopoisk("Kinopoisk"), Episodes("TMDB") }

data class GalleryImage(
    val url: String,
    val thumbnail: String,
    val kind: GalleryKind,
    val source: GallerySource,
    val width: Int? = null,
    val height: Int? = null,
    val vote: Double = 0.0,
    /** No text on it (TMDB's language-less images; stills and photos always). */
    val textFree: Boolean = true,
    /** "S2 E3 · Who Is Alive?" for an episode still. */
    val caption: String? = null,
)

/** The gallery's filters: kind, or All; and whether images with text on them are left out. */
data class GalleryFilter(val kind: GalleryKind? = null, val textFreeOnly: Boolean = true)

/** The tab shows when the title has at least this many images. */
const val GalleryMinimumImages = 3

/** Kinds present in [images], in the tab's order (episode stills only exist for series). */
fun galleryKinds(images: List<GalleryImage>): List<GalleryKind> =
    GalleryKind.entries.filter { kind -> images.any { it.kind == kind } }

/** The images a filter shows: duplicates removed, best voted first within each kind. */
fun galleryVisible(images: List<GalleryImage>, filter: GalleryFilter): List<GalleryImage> =
    images
        .distinctBy { it.url }
        .filter { filter.kind == null || it.kind == filter.kind }
        .filter { !filter.textFreeOnly || it.textFree }
        .sortedWith(compareBy<GalleryImage> { it.kind.ordinal }.thenByDescending { it.vote })

/**
 * The Details gallery (design doc "Gallery"): TMDB backdrops and posters for the title, a series'
 * episode stills from TMDB, and, with the user's Kinopoisk key, stills and behind-the-scenes photos
 * for films and series. Kinopoisk failing (no key, a bad key, the daily quota) leaves TMDB only.
 */
object DetailGalleryRepository {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<GalleryImage>>()

    suspend fun load(meta: MetaDetails): List<GalleryImage> {
        val key = "${meta.type}:${meta.id}:${KinopoiskSettingsRepository.apiKey.value != null}"
        mutex.withLock { cache[key]?.let { return it } }
        val isSeries = meta.type == "series"
        val images = coroutineScope {
            val tmdbId = async { withTimeoutOrNull(4_000L) { TmdbService.ensureTmdbId(meta.id, meta.type, fallbackImdbId = meta.imdbId) } }
            val tmdb = async {
                val id = tmdbId.await() ?: return@async emptyList()
                runCatchingNonCancel { TmdbMetadataService.galleryImages(id, if (isSeries) "tv" else "movie") }.orEmpty().map { image ->
                    GalleryImage(
                        url = image.url,
                        thumbnail = image.thumbnail ?: image.url,
                        kind = if (image.kind == TmdbGalleryKind.Poster) GalleryKind.Poster else GalleryKind.Backdrop,
                        source = GallerySource.Tmdb,
                        width = image.width,
                        height = image.height,
                        vote = image.vote,
                        textFree = image.textFree,
                    )
                }
            }
            // Kinopoisk covers films and series alike; it matches either by IMDb id.
            val kinopoisk = async {
                val imdb = meta.imdbId ?: meta.id.takeIf { it.startsWith("tt") }
                if (imdb == null) Logger.withTag("KinopoiskGallery").i { "No IMDb id for ${meta.id}; Kinopoisk skipped" }
                imdb?.let { KinopoiskGalleryRepository.images(it) }.orEmpty()
            }
            val stills = async { if (isSeries) episodeStills(meta, tmdbId.await()) else emptyList() }
            tmdb.await() + stills.await() + kinopoisk.await()
        }
        mutex.withLock { cache[key] = images }
        return images
    }
}

/** Most episodes whose TMDB stills are fetched; later ones keep the image Details already has. */
private const val EpisodeStillLookups = 60

/**
 * A series' episode stills: every still TMDB has for each episode (in episode order), captioned
 * "S2 E3 · Title". Without a TMDB id, or for an episode TMDB has none for, the episode's own
 * thumbnail from Details stands in.
 */
private suspend fun episodeStills(meta: MetaDetails, tmdbId: String?): List<GalleryImage> = coroutineScope {
    val episodes = meta.videos.filter { (it.season ?: 0) > 0 && it.episode != null }
        .sortedWith(compareBy({ it.season }, { it.episode }))
    val gate = Semaphore(6)
    episodes.mapIndexed { index, video ->
        async {
            val caption = listOfNotNull(
                "S${video.season} E${video.episode}",
                video.title.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            val fetched = if (tmdbId != null && index < EpisodeStillLookups) {
                gate.withPermit {
                    runCatchingNonCancel { TmdbMetadataService.episodeStills(tmdbId, video.season!!, video.episode!!) }.orEmpty()
                }
            } else emptyList()
            fetched.map { still ->
                GalleryImage(
                    url = still.url,
                    thumbnail = still.thumbnail ?: still.url,
                    kind = GalleryKind.EpisodeStill,
                    source = GallerySource.Episodes,
                    width = still.width,
                    height = still.height,
                    caption = caption,
                )
            }.ifEmpty {
                listOfNotNull(video.thumbnail?.takeIf { it.isNotBlank() }?.let {
                    GalleryImage(url = it, thumbnail = it, kind = GalleryKind.EpisodeStill, source = GallerySource.Episodes, caption = caption)
                })
            }
        }
    }.awaitAll().flatten()
}

/**
 * Kinopoisk Unofficial API (kinopoiskapiunofficial.tech): the film id from its IMDb id, then its
 * stills (STILL) and on-set photos (SHOOTING). One lookup per title, kept for the session; the
 * user's key is sent only to that API.
 */
internal object KinopoiskGalleryRepository {
    private const val BASE = "https://kinopoiskapiunofficial.tech/api/v2.2"
    private val log = Logger.withTag("KinopoiskGallery")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<GalleryImage>>()

    fun clearCache() {
        cache.clear()
    }

    suspend fun images(imdbId: String): List<GalleryImage> {
        KinopoiskSettingsRepository.ensureLoaded()
        val apiKey = KinopoiskSettingsRepository.apiKey.value ?: return emptyList()
        mutex.withLock { cache[imdbId]?.let { return it } }
        val result = runCatchingNonCancel(onError = { log.w { "Lookup failed for $imdbId: ${it::class.simpleName}: ${it.message}" } }) {
            withTimeoutOrNull(8_000L) {
                val headers = mapOf("X-API-KEY" to apiKey, "Accept" to "application/json")
                val search = httpRequestRaw("GET", "$BASE/films?imdbId=$imdbId", headers, "")
                if (search.status !in 200..299) {
                    log.w { "Search for $imdbId answered HTTP ${search.status}" }
                    return@withTimeoutOrNull null
                }
                val filmId = json.decodeFromString<KinopoiskSearch>(search.body).items.firstOrNull()?.kinopoiskId
                    ?: return@withTimeoutOrNull emptyList()
                listOf("STILL" to GalleryKind.Still, "SHOOTING" to GalleryKind.BehindTheScenes).flatMap { (type, kind) ->
                    val page = httpRequestRaw("GET", "$BASE/films/$filmId/images?type=$type&page=1", headers, "")
                    if (page.status !in 200..299) emptyList<GalleryImage>().also { log.w { "$type images answered HTTP ${page.status}" } }
                    else json.decodeFromString<KinopoiskImages>(page.body).items.mapNotNull { item ->
                        val url = item.imageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        GalleryImage(url = url, thumbnail = item.previewUrl ?: url, kind = kind, source = GallerySource.Kinopoisk)
                    }
                }
            }
        }
        // Over quota, a bad key or a timeout: nothing now, and the next title tries again.
        val images = result ?: return emptyList<GalleryImage>().also { log.w { "No Kinopoisk images for $imdbId (failed or timed out)" } }
        mutex.withLock { cache[imdbId] = images }
        return images
    }

    @Serializable
    private data class KinopoiskSearch(val items: List<KinopoiskFilm> = emptyList())

    @Serializable
    private data class KinopoiskFilm(@SerialName("kinopoiskId") val kinopoiskId: Long? = null)

    @Serializable
    private data class KinopoiskImages(val items: List<KinopoiskImage> = emptyList())

    @Serializable
    private data class KinopoiskImage(val imageUrl: String? = null, val previewUrl: String? = null)
}

private inline fun <T> runCatchingNonCancel(onError: (Exception) -> Unit = {}, block: () -> T): T? = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    onError(error)
    null
}
