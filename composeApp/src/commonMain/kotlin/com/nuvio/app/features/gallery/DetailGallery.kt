package com.nuvio.app.features.gallery

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tmdb.TmdbGalleryKind
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
 * episode stills from its own metadata, and, with the user's Kinopoisk key, real film stills and
 * behind-the-scenes photos. Kinopoisk failing (no key, a bad key, the daily quota) leaves TMDB only.
 */
object DetailGalleryRepository {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<GalleryImage>>()

    suspend fun load(meta: MetaDetails): List<GalleryImage> {
        val key = "${meta.type}:${meta.id}:${KinopoiskSettingsRepository.apiKey.value != null}"
        mutex.withLock { cache[key]?.let { return it } }
        val isSeries = meta.type == "series"
        val images = coroutineScope {
            val tmdb = async {
                val tmdbId = withTimeoutOrNull(4_000L) { TmdbService.ensureTmdbId(meta.id, meta.type, fallbackImdbId = meta.imdbId) }
                    ?: return@async emptyList()
                runCatchingNonCancel { TmdbMetadataService.galleryImages(tmdbId, if (isSeries) "tv" else "movie") }.orEmpty().map { image ->
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
            val kinopoisk = async {
                if (isSeries) emptyList() else meta.imdbId?.let { KinopoiskGalleryRepository.images(it) }.orEmpty()
            }
            val stills = if (isSeries) {
                meta.videos.filter { !it.thumbnail.isNullOrBlank() && (it.season ?: 0) > 0 }.map { video ->
                    GalleryImage(
                        url = video.thumbnail!!,
                        thumbnail = video.thumbnail,
                        kind = GalleryKind.EpisodeStill,
                        source = GallerySource.Episodes,
                        caption = listOfNotNull(
                            if (video.season != null && video.episode != null) "S${video.season} E${video.episode}" else null,
                            video.title.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                    )
                }
            } else {
                emptyList()
            }
            tmdb.await() + stills + kinopoisk.await()
        }
        mutex.withLock { cache[key] = images }
        return images
    }
}

/**
 * Kinopoisk Unofficial API (kinopoiskapiunofficial.tech): the film id from its IMDb id, then its
 * stills (STILL) and on-set photos (SHOOTING). One lookup per title, kept for the session; the
 * user's key is sent only to that API.
 */
internal object KinopoiskGalleryRepository {
    private const val BASE = "https://kinopoiskapiunofficial.tech/api/v2.2"
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
        val result = runCatchingNonCancel {
            withTimeoutOrNull(8_000L) {
                val headers = mapOf("X-API-KEY" to apiKey, "Accept" to "application/json")
                val search = httpRequestRaw("GET", "$BASE/films?imdbId=$imdbId", headers, "")
                if (search.status !in 200..299) return@withTimeoutOrNull null
                val filmId = json.decodeFromString<KinopoiskSearch>(search.body).items.firstOrNull()?.kinopoiskId
                    ?: return@withTimeoutOrNull emptyList()
                listOf("STILL" to GalleryKind.Still, "SHOOTING" to GalleryKind.BehindTheScenes).flatMap { (type, kind) ->
                    val page = httpRequestRaw("GET", "$BASE/films/$filmId/images?type=$type&page=1", headers, "")
                    if (page.status !in 200..299) emptyList()
                    else json.decodeFromString<KinopoiskImages>(page.body).items.mapNotNull { item ->
                        val url = item.imageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        GalleryImage(url = url, thumbnail = item.previewUrl ?: url, kind = kind, source = GallerySource.Kinopoisk)
                    }
                }
            }
        }
        // Over quota or a bad key: nothing now, and the next title tries again.
        val images = result ?: return emptyList()
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

private inline fun <T> runCatchingNonCancel(block: () -> T): T? = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}
