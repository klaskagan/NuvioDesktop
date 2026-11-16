package com.nuvio.app.features.tmdb

import com.nuvio.app.features.details.ActingCredit
import com.nuvio.app.features.details.ActingCreditKind
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TV_MOVIE_GENRE_ID = 10770

internal data class TmdbPersonFilmography(
    val movies: List<MetaPreview>,
    val series: List<MetaPreview>,
    val actingCredits: List<ActingCredit>,
    val genreIds: Map<String, Set<Int>>,
    val allCredits: List<MetaPreview>,
)

internal fun mapPersonFilmography(
    credits: TmdbPersonCombinedCreditsResponse,
    language: String,
    englishTitles: Map<Pair<String?, Int>, String>,
    preferCrew: Boolean,
): TmdbPersonFilmography {
    val cast = credits.cast.mapNotNull { it.toPreview(language, englishTitles) }.distinctBy { it.stableKey() }
    val crew = credits.crew.mapNotNull { it.toPreview(language, englishTitles) }.distinctBy { it.stableKey() }
    fun preferred(type: String): List<MetaPreview> {
        val primary = if (preferCrew) crew else cast
        val fallback = if (preferCrew) cast else crew
        return primary.filter { it.type == type }.ifEmpty { fallback.filter { it.type == type } }
    }

    val roles = credits.cast.groupBy { "${it.appType}:tmdb:${it.id}" }
    val actingCredits = cast.map { media ->
        val titleCredits = roles[media.stableKey()].orEmpty()
        val characters = titleCredits.mapNotNull { it.character?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        val kind = when {
            media.type == "series" -> ActingCreditKind.TvSeries
            titleCredits.any { TV_MOVIE_GENRE_ID in it.genreIds } -> ActingCreditKind.TvMovie
            titleCredits.any { it.video == true } -> ActingCreditKind.Video
            else -> ActingCreditKind.Movie
        }
        val episodeCounts = if (kind == ActingCreditKind.TvSeries) {
            titleCredits.filter { (it.episodeCount ?: 0) > 0 }
                .distinctBy { it.creditId ?: it }
                .mapNotNull { it.episodeCount }
        } else emptyList()
        // Two role credits can count the same episode. The largest count is a safe lower bound.
        ActingCredit(media, characters, kind, episodeCounts.maxOrNull(), episodeCounts.size > 1)
    }
    val genreIds = (credits.cast + credits.crew).filter { it.appType != null }
        .groupBy { "${it.appType}:tmdb:${it.id}" }
        .mapValues { (_, matching) -> matching.flatMap { it.genreIds }.toSet() }

    return TmdbPersonFilmography(preferred("movie"), preferred("series"), actingCredits, genreIds, (cast + crew).distinctBy { it.stableKey() })
}

internal fun mapPersonKnownFor(
    response: TmdbPersonSearchResponse,
    personId: Int,
    language: String,
): List<MetaPreview>? = response.results.firstOrNull { it.id == personId }?.knownFor
    ?.mapNotNull { it.toPreview(language, emptyMap()) }
    ?.distinctBy { it.stableKey() }

private val TmdbPersonCredit.appType: String?
    get() = when (mediaType) {
        "movie" -> "movie"
        "tv" -> "series"
        else -> null
    }

internal fun TmdbPersonCredit.toPreview(
    language: String,
    englishTitles: Map<Pair<String?, Int>, String>,
): MetaPreview? {
    val type = appType ?: return null
    if (id <= 0) return null
    val isMovie = type == "movie"
    val resolvedTitle = resolvePersonName(
        localizedName = if (isMovie) title ?: name else name ?: title,
        originalName = if (isMovie) originalTitle ?: originalName else originalName ?: originalTitle,
        fallbackEnglishName = englishTitles[mediaType to id],
        preferredLanguage = language,
    ) ?: return null
    val date = if (isMovie) releaseDate else firstAirDate
    return MetaPreview(
        id = "tmdb:$id",
        type = type,
        name = resolvedTitle,
        poster = buildImageUrl(posterPath, "w500"),
        banner = buildImageUrl(backdropPath, "w780"),
        description = overview?.takeIf(String::isNotBlank),
        releaseInfo = date?.take(4),
        rawReleaseDate = date,
        popularity = popularity,
        sortRating = voteAverage?.takeIf { it > 0 },
        voteCount = voteCount,
    )
}

@Serializable
internal data class TmdbPersonCombinedCreditsResponse(
    val cast: List<TmdbPersonCredit> = emptyList(),
    val crew: List<TmdbPersonCredit> = emptyList(),
)

@Serializable
internal data class TmdbPersonSearchResponse(
    val results: List<TmdbPersonSearchResult> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
internal data class TmdbPersonSearchResult(
    val id: Int,
    @SerialName("known_for") val knownFor: List<TmdbPersonCredit> = emptyList(),
)

@Serializable
internal data class TmdbPersonCredit(
    val id: Int = 0,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val character: String? = null,
    val video: Boolean? = null,
    @SerialName("credit_id") val creditId: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
)



// Read only the website's curated shelf. IDs are resolved against localized API metadata.
private val knownForList = Regex("""<div\s+id=["']known_for_scroller["'][^>]*>\s*<ul\b[^>]*>([\s\S]*?)</ul>""")
private val knownForLink = Regex("""\bhref=["']/(movie|tv)/(\d+)(?=[-?/"'])""")

internal fun websiteKnownForKeys(html: String): List<Pair<String, Int>> {
    val shelf = knownForList.find(html)?.groupValues?.get(1) ?: return emptyList()
    return knownForLink.findAll(shelf).mapNotNull { link ->
        link.groupValues[2].toIntOrNull()?.takeIf { it > 0 }?.let { link.groupValues[1] to it }
    }.distinct().toList()
}
