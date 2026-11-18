package com.nuvio.app.features.tracking

import kotlinx.coroutines.flow.StateFlow

internal data class TrackingHistoryAccount(val profileId: Int, val generation: Long)

internal data class TrackingHistoryEvent(
    val id: String,
    val watchedAtMillis: Long,
    val media: TrackingMediaReference,
    val episodeScreenshot: String? = null,
    val landscapeArtwork: String? = null,
)

internal data class TrackingHistoryPage(
    val events: List<TrackingHistoryEvent>,
    val nextCursor: String?,
)

internal interface TrackingHistoryProvider {
    val providerId: TrackingProviderId
    val account: StateFlow<TrackingHistoryAccount?>

    /** Events are returned newest first. Cursors are meaningful only to their issuing reader. */
    suspend fun loadPage(profileId: Int, cursor: String?): TrackingHistoryPage

    /** Adapters may filter on the server; the default supports mixed-page readers. */
    suspend fun loadFilteredPage(profileId: Int, cursor: String?, kind: TrackingMediaKind?): TrackingHistoryPage {
        val page = loadPage(profileId, cursor)
        return page.copy(events = page.events.filter { kind == null || if (kind == TrackingMediaKind.SHOW) it.media.kind != TrackingMediaKind.MOVIE else it.media.kind == kind })
    }
}

internal data class TrackingHistorySource(
    val provider: TrackingHistoryProvider,
    val account: TrackingHistoryAccount,
)

/** Optional capability: removes one watch event, never every watch of its media. */
internal interface TrackingHistoryRemover {
    suspend fun removeEvent(profileId: Int, eventId: String)
}
