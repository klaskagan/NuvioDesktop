package com.nuvio.app.features.tracking

import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A comment or review from whichever tracker is the reviews source; nothing in it names a tracker. */
data class TrackingComment(
    val id: String,
    val authorName: String,
    val authorUsername: String? = null,
    val text: String,
    val spoiler: Boolean = false,
    val containsInlineSpoilers: Boolean = false,
    val review: Boolean = false,
    val likes: Int = 0,
    val likedByMe: Boolean = false,
    val rating: Int? = null,
    val createdAt: String? = null,
    /** The comment on the tracker's website, for "Open on …". */
    val webUrl: String? = null,
    /** Where an imported comment was first written ("TV Time"), shown with its author. */
    val origin: String? = null,
) {
    val hasSpoilerContent: Boolean get() = spoiler || containsInlineSpoilers
}

data class TrackingCommentsPage(
    val items: List<TrackingComment>,
    val page: Int,
    val pageCount: Int,
    val itemCount: Int,
)

enum class TrackingCommentsSort { MostLiked, Newest }

/** What a reviews source can do; the UI hides whatever it can't. */
data class TrackingCommentsCapabilities(
    val canLike: Boolean,
    val canPost: Boolean,
    /** Fewest words a comment may have. */
    val minWords: Int = 5,
    /** From this many words a comment counts as a review. */
    val reviewWordThreshold: Int = 200,
)

/** Where a new comment goes: the whole title, one season, or one episode. */
data class TrackingCommentScope(val season: Int? = null, val episode: Int? = null)

sealed interface TrackingCommentPostResult {
    data object Posted : TrackingCommentPostResult
    data object TooShort : TrackingCommentPostResult
    data class Failed(val message: String?) : TrackingCommentPostResult
}

/**
 * A tracker that has comments and reviews (Trakt today; WeTrakr next). Reviews on Details come from
 * the one chosen as Settings → Tracking → Reviews source, else the first connected one.
 */
interface TrackingCommentsProvider {
    val providerId: TrackingProviderId
    val capabilities: TrackingCommentsCapabilities

    /** False once the tracker said comments aren't open to this app; the next source is used. */
    val isAvailable: Boolean get() = true

    suspend fun page(meta: MetaDetails, sort: TrackingCommentsSort, page: Int): TrackingCommentsPage

    /** False when the tracker refused; the caller rolls its optimistic change back. */
    suspend fun setLiked(comment: TrackingComment, liked: Boolean): Boolean

    suspend fun post(meta: MetaDetails, scope: TrackingCommentScope, text: String, spoiler: Boolean): TrackingCommentPostResult
}

object TrackingComments {
    private val providers = MutableStateFlow<Map<TrackingProviderId, TrackingCommentsProvider>>(emptyMap())
    val registered: StateFlow<Map<TrackingProviderId, TrackingCommentsProvider>> = providers.asStateFlow()

    fun register(provider: TrackingCommentsProvider) {
        providers.update { it + (provider.providerId to provider) }
    }

    /** The reviews source: the chosen one while connected, else the first connected in tracker order. */
    fun provider(
        chosen: TrackingProviderId? = TrackingSettingsRepository.uiState.value.reviewsSource,
        connected: Set<TrackingProviderId> = TrackingProviderRegistry.connectedProviderIds.value,
    ): TrackingCommentsProvider? {
        val available = providers.value.filter { (id, provider) -> id in connected && provider.isAvailable }
        return chosen?.let(available::get) ?: available.entries.minByOrNull { it.key.ordinal }?.value
    }

    /** Words as the trackers count them, for the composer's minimum and review threshold. */
    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
}
