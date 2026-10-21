package com.nuvio.app.features.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun tmdbImageLanguages(language: String): String {
    val normalizedLanguage = normalizeTmdbLanguage(language)
    return listOf(normalizedLanguage.substringBefore("-"), normalizedLanguage, "en", "null")
        .distinct()
        .joinToString(",")
}

internal fun List<TmdbImage>.selectBestLocalizedImagePath(normalizedLanguage: String): String? {
    val languageCode = normalizedLanguage.substringBefore("-")
    val regionCode = normalizedLanguage.substringAfter("-", "").uppercase().takeIf { it.length == 2 }
        ?: defaultLanguageRegions[languageCode]
    return filter { !it.filePath.isNullOrBlank() }.sortedWith(
        compareByDescending<TmdbImage> { it.iso6391 == languageCode && it.iso31661 == regionCode }
            .thenByDescending { it.iso6391 == languageCode && it.iso31661 == null }
            .thenByDescending { it.iso6391 == languageCode }
            .thenByDescending { it.iso6391 == "en" }
            .thenByDescending { it.iso6391 == null },
    ).firstOrNull()?.filePath?.trim()
}

private val defaultLanguageRegions = mapOf(
    "pt" to "PT",
    "es" to "ES",
)

@Serializable
internal data class TmdbImagesResponse(
    val logos: List<TmdbImage> = emptyList(),
    val backdrops: List<TmdbImage> = emptyList(),
)

@Serializable
internal data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val iso6391: String? = null,
    @SerialName("iso_3166_1") val iso31661: String? = null,
    @SerialName("aspect_ratio") val aspectRatio: Double? = null,
)

/** TMDB supplies separate backdrops and title logos, not a guaranteed branded-landscape category. */
internal data class TmdbLandscapeImagePaths(val backdrop: String?, val logo: String?)

internal fun TmdbImagesResponse.selectLandscapeImagePaths(language: String): TmdbLandscapeImagePaths {
    val normalized = normalizeTmdbLanguage(language)
    val supportedLanguages = setOf(normalized.substringBefore("-"), "en", null)
    val logo = logos.filter { it.iso6391 in supportedLanguages }.selectBestLocalizedImagePath(normalized)
    val landscape = backdrops.filter { image ->
        image.aspectRatio?.let { it >= 1.4 } ?: true
    }
    // A textless backdrop plus a localized official logo avoids duplicated/foreign-language titles.
    val cleanBackdrop = if (logo != null) landscape.filter { it.iso6391 == null }
        .selectBestLocalizedImagePath(normalized) else null
    return TmdbLandscapeImagePaths(cleanBackdrop ?: landscape.selectBestLocalizedImagePath(normalized), logo)
}
