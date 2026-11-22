package com.nuvio.app.features.search

/** A studio row seeded into a service's folder: that studio's titles available on the service. */
internal data class StreamingBrandRow(val title: String, val companies: String)

/**
 * What a streaming service page needs beyond its watch-provider id.
 *
 * TMDB ids were checked on themoviedb.org (network/<id>, company/<id>) on 2026-11-22:
 * networks 213 Netflix, 2552 Apple TV, 2739 Disney+, 1024 Prime Video, 453 Hulu, 49 HBO,
 * 3186 HBO Max, 6783 Max, 4330 Paramount+, 3353 Peacock, 1112 Crunchyroll, 4353 discovery+;
 * companies 178464 Netflix, 194232 Apple Studios, 420 Marvel Studios, 3 Pixar, 1 Lucasfilm Ltd.,
 * 184898 DC Studios, 128064 DC Films, 9993 DC Entertainment.
 */
internal data class StreamingProviderSpec(
    /** The provider tag's name in [SearchDiscovery]. */
    val name: String,
    /** FlixPatrol's slug for the service; also the stable suffix of its streaming folder id. */
    val slug: String,
    /** TMDB networks whose shows are the service's originals (`|` = any of). */
    val originalsNetworks: String,
    /** Set only where the studio makes nothing but originals for this service. */
    val originalsFilmCompanies: String? = null,
    val brandRows: List<StreamingBrandRow> = emptyList(),
)

/** Used when the user's streaming country has no catalog for a service (e.g. Lithuania). */
internal const val STREAMING_FALLBACK_REGION = "GB"

/** In the order the services appear in the streaming collection. */
internal val streamingProviderSpecs = listOf(
    StreamingProviderSpec("Netflix", "netflix", "213", originalsFilmCompanies = "178464"),
    StreamingProviderSpec(
        "Disney+", "disney", "2739",
        brandRows = listOf(
            StreamingBrandRow("Marvel", "420"),
            StreamingBrandRow("Pixar", "3"),
            StreamingBrandRow("Lucasfilm", "1"),
        ),
    ),
    StreamingProviderSpec("Prime Video", "amazon-prime", "1024"),
    StreamingProviderSpec("Apple TV", "apple-tv", "2552", originalsFilmCompanies = "194232"),
    StreamingProviderSpec(
        "HBO Max", "hbo-max", "49|3186|6783",
        brandRows = listOf(StreamingBrandRow("DC", "184898|128064|9993")),
    ),
    StreamingProviderSpec("Hulu", "hulu", "453"),
    StreamingProviderSpec("Paramount+", "paramount", "4330"),
    StreamingProviderSpec("Peacock", "peacock", "3353"),
    StreamingProviderSpec("Crunchyroll", "crunchyroll", "1112"),
    StreamingProviderSpec("Discovery+", "discovery-plus", "4353"),
)

internal fun streamingProviderSpec(name: String): StreamingProviderSpec? =
    streamingProviderSpecs.firstOrNull { it.name == name }

/** Filters for a service's "Originals" rail; null when the tag has none for that medium. */
internal fun SearchDiscovery.originalsFilters(television: Boolean): Map<String, String>? {
    if (kind != DiscoveryKind.Provider) return null
    val spec = streamingProviderSpec(name) ?: return null
    return if (television) mapOf("with_networks" to spec.originalsNetworks)
    else spec.originalsFilmCompanies?.let { mapOf("with_companies" to it) }
}

/**
 * The region a service page ended up using, so "View all" and the folder's own rows query the
 * same catalog as the page (the user's streaming country, or the fallback when that was empty).
 */
internal object StreamingCatalogRegion {
    private val used = mutableMapOf<String, String>()

    fun record(provider: String, region: String) {
        used[provider] = region
    }

    suspend fun forProvider(provider: String): String =
        used[provider]
            ?: runCatching {
                SearchStreamingCountry.effective(
                    SearchStreamingCountry.override.value,
                    SearchPreferencesStorage.deviceRegion(),
                    SearchStreamingCountry.regions(),
                )
            }.getOrNull()
            ?: STREAMING_FALLBACK_REGION
}
