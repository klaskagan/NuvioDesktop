package com.nuvio.app.features.details

import com.nuvio.app.features.home.MetaPreview

data class PersonDetail(
    val tmdbId: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val profilePhoto: String?,
    val knownFor: String?,
    val movieCredits: List<MetaPreview>,
    val tvCredits: List<MetaPreview>,
    val creditGenreIds: Map<String, Set<Int>> = emptyMap(),
    val actingCredits: List<ActingCredit> = emptyList(),
    val allCredits: List<MetaPreview> = emptyList(),
    /** Stable keys of titles this person directed. */
    val directedKeys: Set<String> = emptySet(),
    /** Timeline credits per department. Crew credits carry their jobs in [ActingCredit.characters]. */
    val careerCredits: Map<CareerDepartment, List<ActingCredit>> = emptyMap(),
    val imdbId: String? = null,
    val alsoKnownAs: List<String> = emptyList(),
    /** TMDB profile photos, best first, as full-size URLs. */
    val portraits: List<String> = emptyList(),
    /** Stills and backdrops TMDB has tagged the person in. */
    val taggedImages: List<PersonTaggedImage> = emptyList(),
    /** How many tagged images TMDB has in all; [taggedImages] holds the first page. */
    val taggedImageTotal: Int = 0,
)

/** An image the person is tagged in, with the title it comes from. */
data class PersonTaggedImage(
    val url: String,
    val thumbnail: String,
    val title: String?,
    /** Width over height; posters are about 0.67, stills and backdrops about 1.78. */
    val aspectRatio: Float,
)

/** Timeline tabs. [Crew] merges every department without its own tab; [Self] is cast credits as themselves. */
enum class CareerDepartment { Acting, Directing, Writing, Production, Creator, Crew, Self }

enum class ActingCreditKind { Movie, TvMovie, Video, TvSeries }

data class ActingCredit(
    val media: MetaPreview,
    val characters: List<String>,
    val kind: ActingCreditKind = if (media.type == "series") ActingCreditKind.TvSeries else ActingCreditKind.Movie,
    val episodeCount: Int? = null,
    val episodeCountIsMinimum: Boolean = false,
)

/** The title a person page was opened from: its "In …" tile names the role there. */
@kotlinx.serialization.Serializable
data class PersonTitleContext(
    val title: String,
    val artwork: String?,
    val role: String?,
    val episodeCount: Int?,
    val isSeries: Boolean,
)
