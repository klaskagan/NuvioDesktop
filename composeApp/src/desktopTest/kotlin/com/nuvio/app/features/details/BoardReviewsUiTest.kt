package com.nuvio.app.features.details

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.components.BoardReviews
import com.nuvio.app.features.tracking.TrackingComment
import com.nuvio.app.features.tracking.TrackingCommentPostResult
import com.nuvio.app.features.tracking.TrackingCommentScope
import com.nuvio.app.features.tracking.TrackingCommentsCapabilities
import com.nuvio.app.features.tracking.TrackingCommentsPage
import com.nuvio.app.features.tracking.TrackingCommentsProvider
import com.nuvio.app.features.tracking.TrackingCommentsSort
import com.nuvio.app.features.tracking.TrackingProviderId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import org.junit.Test

/** Reviews on the board against a fake source: likes roll back on refusal, the reader steps, the composer posts. */
@OptIn(ExperimentalTestApi::class)
class BoardReviewsUiTest {
    private class FakeSource : TrackingCommentsProvider {
        override val providerId = TrackingProviderId.TRAKT
        override val capabilities = TrackingCommentsCapabilities(canLike = true, canPost = true)
        val sorts = mutableListOf<TrackingCommentsSort>()
        val posts = mutableListOf<Pair<TrackingCommentScope, String>>()
        var acceptLikes = false
        override suspend fun page(meta: MetaDetails, sort: TrackingCommentsSort, page: Int): TrackingCommentsPage {
            sorts += sort
            val items = (1..4).map { TrackingComment("$it", "Author $it", text = "Review number $it is a fine review of the show", likes = 10 * it, review = it == 1, rating = if (it == 1) 9 else null) }
            return TrackingCommentsPage(items, 1, 1, items.size)
        }
        override suspend fun setLiked(comment: TrackingComment, liked: Boolean) = acceptLikes
        override suspend fun post(meta: MetaDetails, scope: TrackingCommentScope, text: String, spoiler: Boolean): TrackingCommentPostResult {
            posts += scope to text
            return TrackingCommentPostResult.Posted
        }
    }

    @Test fun likesReaderAndComposer() = runDesktopComposeUiTest(width = 1360, height = 900) {
        val source = FakeSource()
        val meta = MetaDetails("reviews-fixture", "series", "Reviews fixture")
        setContent { AppEnvironment { BoardReviews(meta = meta, provider = source, episodes = listOf(1 to 1, 1 to 2, 2 to 1), currentEpisode = 2 to 1) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("detail_review_card").fetchSemanticsNodes().size == 4 }
        // Frame "Reviews": the source names the first sort; Hide spoilers is a switch, on.
        onNodeWithText("Trakt · Most liked").assertIsDisplayed()
        onNodeWithTag("detail_reviews_hide_spoilers").assertIsOn()
        // The ★ score sits at the card's right edge (16 dp padding), not after the author.
        val card = onAllNodesWithTag("detail_review_card")[0].getUnclippedBoundsInRoot()
        val score = onNodeWithTag("detail_review_score", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(card.right - score.right < 20.dp, "card ends at ${card.right}, score at ${score.right}")

        // A refused like puts the heart and count back.
        onAllNodesWithTag("detail_review_like")[0].performClick()
        waitForIdle()
        onAllNodesWithTag("detail_review_like_count", useUnmergedTree = true)[0].assertTextEquals("10")

        // The reader opens on the card, steps with →, and closes with Esc.
        onAllNodesWithTag("detail_review_card")[1].performClick()
        waitForIdle()
        onNodeWithTag("detail_review_reader_position", useUnmergedTree = true).assertTextEquals("2 of 4")
        // Frame "Review open": avatar, "20 likes" in the line under the author, and the keyboard hint.
        onNodeWithTag("detail_review_reader_avatar", useUnmergedTree = true).assertExists()
        onNodeWithTag("detail_review_reader_meta", useUnmergedTree = true).assertTextContains("20 likes", substring = true)
        onNodeWithTag("detail_review_reader_hint", useUnmergedTree = true).assertExists()
        onNodeWithTag("detail_review_reader").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag("detail_review_reader_position", useUnmergedTree = true).assertTextEquals("3 of 4")
        onNodeWithTag("detail_review_reader").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("detail_review_reader").assertDoesNotExist()

        // Reviews only keeps the long reviews, and the reader walks that list.
        onNodeWithTag("detail_reviews_sort_ReviewsOnly").performClick()
        waitForIdle()
        onAllNodesWithTag("detail_review_card").assertCountEquals(1)
        onAllNodesWithTag("detail_review_card")[0].performClick()
        waitForIdle()
        onNodeWithTag("detail_review_reader_position", useUnmergedTree = true).assertTextEquals("1 of 1")
        onNodeWithTag("detail_review_reader").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        // Write a review: too short, then enough for the season the board is on, then posted and shown by Newest.
        onNodeWithTag("detail_reviews_write").performClick()
        waitForIdle()
        onNodeWithTag("detail_review_text").performTextInput("Great")
        onNodeWithTag("detail_review_word_count", useUnmergedTree = true).assertTextContains("at least 5", substring = true)
        onNodeWithTag("detail_review_post").performClick()
        assertEquals(0, source.posts.size)
        onNodeWithTag("detail_review_text").performTextInput(" second season, really loved it")
        // The chips offer the episode the board is on (S2 E1), not Season 1 / S1 E1.
        onNodeWithText("S2 E1", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithTag("detail_review_scope_1").performClick()
        onNodeWithTag("detail_review_post").performClick()
        waitForIdle()
        assertEquals(listOf(TrackingCommentScope(season = 2) to "Great second season, really loved it"), source.posts)
        waitUntil(timeoutMillis = 5_000) { source.sorts.lastOrNull() == TrackingCommentsSort.Newest }
    }
}
