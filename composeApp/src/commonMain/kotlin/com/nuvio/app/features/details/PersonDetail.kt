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
