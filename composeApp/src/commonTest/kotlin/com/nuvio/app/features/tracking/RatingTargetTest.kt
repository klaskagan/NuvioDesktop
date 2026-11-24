package com.nuvio.app.features.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatingTargetTest {
    private class Fake(
        override val providerId: TrackingProviderId,
        override val ratesSeasons: Boolean = true,
        private val episodes: Boolean = true,
    ) : TrackingRatingProvider {
        override val changes: Flow<Any?> = emptyFlow()
        override fun supports(media: TrackingMediaReference) = episodes || media.episode == null
        override fun rating(media: TrackingMediaReference): Double? = null
        override suspend fun rate(profileId: Int, media: TrackingMediaReference, rating: Int?) = TrackingMutationResult(1)
    }

    private val all = listOf(
        Fake(TrackingProviderId.TRAKT),
        Fake(TrackingProviderId.WETRAKR),
        Fake(TrackingProviderId.SIMKL, ratesSeasons = false, episodes = false),
        Fake(TrackingProviderId.MDBLIST),
    )
    private val everyone = TrackingProviderId.entries.toSet()
    private val show = TrackingMediaReference(kind = TrackingMediaKind.SHOW, ids = TrackingExternalIds(imdb = "tt1"))

    private fun target(source: TrackingProviderId?, connected: Set<TrackingProviderId> = everyone, media: TrackingMediaReference = show) =
        ratingTarget(all, connected, source, media)?.providerId

    @Test
    fun `rate goes only to the picked source, Trakt included`() {
        assertEquals(TrackingProviderId.TRAKT, target(TrackingProviderId.TRAKT))
        assertEquals(TrackingProviderId.WETRAKR, target(TrackingProviderId.WETRAKR))
    }

    @Test
    fun `off or a disconnected source hides rating`() {
        assertNull(target(null))
        assertNull(target(TrackingProviderId.TRAKT, connected = setOf(TrackingProviderId.WETRAKR)))
    }

    @Test
    fun `a source that can't rate seasons or episodes is skipped for them`() {
        assertNull(target(TrackingProviderId.SIMKL, media = show.copy(wholeSeason = 2)))
        assertNull(target(TrackingProviderId.SIMKL, media = show.copy(episode = TrackingEpisode(season = 2, number = 3))))
        assertEquals(TrackingProviderId.TRAKT, target(TrackingProviderId.TRAKT, media = show.copy(wholeSeason = 2)))
    }
}
