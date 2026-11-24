package com.nuvio.app.features.tracking

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.components.withLiked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackingCommentsTest {
    private class Fake(override val providerId: TrackingProviderId) : TrackingCommentsProvider {
        override val capabilities = TrackingCommentsCapabilities(canLike = true, canPost = true)
        override suspend fun page(meta: MetaDetails, sort: TrackingCommentsSort, page: Int) = TrackingCommentsPage(emptyList(), 1, 1, 0)
        override suspend fun setLiked(comment: TrackingComment, liked: Boolean) = true
        override suspend fun post(meta: MetaDetails, scope: TrackingCommentScope, text: String, spoiler: Boolean) = TrackingCommentPostResult.Posted
    }

    @Test fun theChosenSourceWinsWhileConnectedElseTheFirstConnected() {
        TrackingComments.register(Fake(TrackingProviderId.TRAKT))
        TrackingComments.register(Fake(TrackingProviderId.SIMKL))
        assertEquals(TrackingProviderId.SIMKL, TrackingComments.provider(TrackingProviderId.SIMKL, setOf(TrackingProviderId.TRAKT, TrackingProviderId.SIMKL))?.providerId)
        assertEquals(TrackingProviderId.TRAKT, TrackingComments.provider(TrackingProviderId.SIMKL, setOf(TrackingProviderId.TRAKT))?.providerId)
        assertEquals(TrackingProviderId.TRAKT, TrackingComments.provider(null, setOf(TrackingProviderId.SIMKL, TrackingProviderId.TRAKT))?.providerId)
        assertNull(TrackingComments.provider(null, emptySet()))
    }

    @Test fun wordsAreCountedTheWayTheRulesNeed() {
        assertEquals(0, TrackingComments.wordCount("   "))
        assertEquals(5, TrackingComments.wordCount("A truly great second season!"))
        assertEquals(3, TrackingComments.wordCount("wow — just wow"))
    }

    @Test fun aLikeChangesTheCountOnceAndUndoes() {
        val comment = TrackingComment("1", "Ann", text = "x", likes = 4)
        val liked = comment.withLiked(true)
        assertEquals(5, liked.likes)
        assertEquals(liked, liked.withLiked(true))
        assertEquals(comment, liked.withLiked(false))
    }
}
