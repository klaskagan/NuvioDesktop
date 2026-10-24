package com.nuvio.app.features.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun tmdbImageLanguages(language: String): String {
    val normalizedLanguage = normalizeTmdbLanguage(language)
    return listOf(normalizedLanguage.substringBefore("-"), normalizedLanguage, "en", "null")
        .distinct()
        .joinToString(",")
}

internal fun tmdbArtworkDetailsQuery(language: String): Map<String, String> = mapOf(
    "language" to normalizeTmdbLanguage(language),
    "append_to_response" to "images",
    // Omitting this can silently exclude neutral images even without a language query.
    "include_image_language" to tmdbImageLanguages(language),
)

internal suspend fun TmdbImagesResponse.withFallbackLogos(
    language: String,
    loadAllImages: suspend () -> TmdbImagesResponse?,
): TmdbImagesResponse {
    if (selectLandscapeImagePaths(language).logo != null) return this
    val all = loadAllImages() ?: return this
    return copy(logos = all.logos, backdrops = (backdrops + all.backdrops).distinctBy { it.filePath })
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

internal fun TmdbImagesResponse.selectLandscapeImagePaths(language: String, originalLanguage: String? = null): TmdbLandscapeImagePaths {
    val normalized = normalizeTmdbLanguage(language)
    // Some series only have an original-language logo (e.g. Turkish or Spanish).
    // Prefer the selected language, English and neutral logos, then any real supplied logo.
    val preferredLanguages = setOf(normalized.substringBefore("-"), "en", null)
    val logo = logos.filter { it.iso6391 in preferredLanguages }.selectBestLocalizedImagePath(normalized)
        ?: logos.filter { it.iso6391 == originalLanguage }.selectBestLocalizedImagePath(normalized)
        ?: logos.selectBestLocalizedImagePath(normalized)
    val landscape = backdrops.filter { image ->
        image.aspectRatio?.let { it >= 1.4 } ?: true
    }
    // A textless backdrop plus a localized official logo avoids duplicated/foreign-language titles.
    val cleanBackdrop = if (logo != null) landscape.filter { it.iso6391 == null }
        .selectBestLocalizedImagePath(normalized) else null
    return TmdbLandscapeImagePaths(cleanBackdrop ?: landscape.selectBestLocalizedImagePath(normalized), logo)
}

/** Only language-neutral landscape images qualify for an added title; localized backdrops may already contain it. */
internal fun TmdbImagesResponse.selectCleanLandscapeBackdropPath(): String? = backdrops
    .firstOrNull { !it.filePath.isNullOrBlank() && it.iso6391 == null && (it.aspectRatio ?: 1.78) >= 1.4 }
    ?.filePath?.trim()
