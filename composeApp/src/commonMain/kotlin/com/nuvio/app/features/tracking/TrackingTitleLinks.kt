package com.nuvio.app.features.tracking

import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A tracker's page for a title, for the Details tab's Links tile. */
interface TrackingTitleLinkProvider {
    val providerId: TrackingProviderId
    val label: String

    /** The title's page on the tracker's website, or null when it can't be found. */
    suspend fun url(meta: MetaDetails): String?

    /** The title's id on the tracker, listed under IMDb and TMDB in the Links tile (Trakt: its slug); null hides the row. */
    suspend fun id(meta: MetaDetails): String? = null
}

/**
 * Tracker links on Details: one per connected tracker that has pages for titles, in tracker order
 * (Trakt, then WeTrakr ahead of Simkl). IMDb and TMDB links come from the metadata, not from here.
 */
object TrackingTitleLinks {
    private val providers = MutableStateFlow<Map<TrackingProviderId, TrackingTitleLinkProvider>>(emptyMap())

    fun register(provider: TrackingTitleLinkProvider) {
        providers.update { it + (provider.providerId to provider) }
    }

    fun connected(connected: Set<TrackingProviderId> = TrackingProviderRegistry.connectedProviderIds.value): List<TrackingTitleLinkProvider> =
        providers.value.filterKeys { it in connected }.entries.sortedBy { it.key.ordinal }.map { it.value }
}

/** The IMDb id of a title, from its metadata or its own id ("tt…"). */
internal fun MetaDetails.imdbIdOrNull(): String? = imdbId?.takeIf { it.startsWith("tt") }
    ?: id.takeIf { it.startsWith("tt") }?.substringBefore(':')

/** The TMDB id when the title's own id is one ("tmdb:123"). */
internal fun MetaDetails.tmdbIdOrNull(): String? = id.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(':')
