package com.nuvio.app.features.trakt

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

private const val COMMENTS_SORT = "likes"
private const val COMMENTS_LIMIT = 100
private const val COMMENTS_CACHE_TTL_MS = 10 * 60_000L
private val INLINE_SPOILER_REGEX = Regex(
    "(?is)\\[spoiler\\].*?\\[/spoiler\\]"
)
private val INLINE_SPOILER_TAG_REGEX = Regex("\\[/?spoiler\\]", RegexOption.IGNORE_CASE)

private val commentsJson = Json { ignoreUnknownKeys = true }

object TraktCommentsRepository {
    private val log = Logger.withTag("TraktComments")

    private data class TimedCache(
        val pages: Map<Int, List<TraktCommentReview>>,
        val pageCount: Int,
        val itemCount: Int,
        val updatedAtMs: Long,
    )

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, TimedCache>()

    suspend fun getCommentsPage(
        meta: MetaDetails,
        page: Int = 1,
        forceRefresh: Boolean = false,
        sort: String = COMMENTS_SORT,
    ): TraktCommentsPage {
        val target = resolveCommentsTarget(meta)
            ?: return TraktCommentsPage(emptyList(), page, 0, 0)
        val cacheKey = "${target.type.apiValue}|${target.pathId}|$sort"

        if (forceRefresh) {
            cacheMutex.withLock { cache.remove(cacheKey) }
        }

        if (!forceRefresh) {
            cacheMutex.withLock {
                val cached = cache[cacheKey]
                if (
                    cached != null &&
                    currentTimeMillis() - cached.updatedAtMs <= COMMENTS_CACHE_TTL_MS &&
                    cached.pages.containsKey(page)
                ) {
                    return TraktCommentsPage(
                        items = cached.pages.getValue(page),
                        currentPage = page,
                        pageCount = cached.pageCount,
                        itemCount = cached.itemCount,
                    )
                }
            }
        }

        val headers = TraktAuthRepository.authorizedHeaders()
            ?: return TraktCommentsPage(emptyList(), page, 0, 0)

        val endpoint = when (target.type) {
            TraktCommentsType.MOVIE -> "movies"
            TraktCommentsType.SHOW -> "shows"
        }
        val url = "https://api.trakt.tv/$endpoint/${target.pathId}/comments/$sort?page=$page&limit=$COMMENTS_LIMIT"

        val response = try {
            httpRequestRaw(
                method = "GET",
                url = url,
                headers = headers,
                body = "",
            )
        } catch (e: Exception) {
            log.e(e) { "Failed to load comments from $url" }
            throw e
        }

        if (response.status == 404) {
            return TraktCommentsPage(emptyList(), page, 0, 0)
        }
        if (response.status !in 200..299) {
            throw IllegalStateException(
                getString(Res.string.details_comments_trakt_load_failed_with_code, response.status),
            )
        }

        val dtos = commentsJson.decodeFromString<List<TraktCommentDto>>(response.body)
        val pageCount = response.headers["X-Pagination-Page-Count"]?.toIntOrNull()
            ?: response.headers["x-pagination-page-count"]?.toIntOrNull()
            ?: page
        val itemCount = response.headers["X-Pagination-Item-Count"]?.toIntOrNull()
            ?: response.headers["x-pagination-item-count"]?.toIntOrNull()
            ?: dtos.size
        val selected = filterDisplayableComments(dtos).map(::toReviewModel)

        cacheMutex.withLock {
            val cached = cache[cacheKey]
            cache[cacheKey] = TimedCache(
                pages = (cached?.pages.orEmpty()) + (page to selected),
                pageCount = pageCount,
                itemCount = itemCount,
                updatedAtMs = currentTimeMillis(),
            )
        }

        return TraktCommentsPage(
            items = selected,
            currentPage = page,
            pageCount = pageCount,
            itemCount = itemCount,
        )
    }

    fun clearCache() {
        cache.clear()
        likedIds = null
    }

    // ---- Likes and posting (Settings → Tracking → Reviews source = Trakt).

    private var likedIds: MutableSet<Long>? = null

    /** The comments the signed-in user has liked, read once per session (Trakt has no per-comment flag). */
    internal suspend fun likedCommentIds(): Set<Long> = cacheMutex.withLock {
        likedIds?.let { return@withLock it.toSet() }
        val headers = TraktAuthRepository.authorizedHeaders() ?: return@withLock emptySet()
        val ids = mutableSetOf<Long>()
        var page = 1
        while (page <= 10) {
            val response = runCatching {
                httpRequestRaw("GET", "https://api.trakt.tv/users/me/likes/comments?page=$page&limit=100", headers, "")
            }.getOrNull() ?: break
            if (response.status !in 200..299) break
            val items = runCatching { commentsJson.decodeFromString<List<TraktLikedCommentDto>>(response.body) }.getOrDefault(emptyList())
            items.mapNotNullTo(ids) { it.comment?.id }
            val pages = response.headers.entries.firstOrNull { it.key.equals("X-Pagination-Page-Count", true) }?.value?.toIntOrNull() ?: 1
            if (items.isEmpty() || page >= pages) break
            page++
        }
        likedIds = ids
        ids.toSet()
    }

    internal suspend fun setLiked(commentId: Long, liked: Boolean): Boolean {
        val headers = TraktAuthRepository.authorizedHeaders() ?: return false
        val response = runCatching {
            httpRequestRaw(if (liked) "POST" else "DELETE", "https://api.trakt.tv/comments/$commentId/like", headers, "")
        }.getOrNull() ?: return false
        val ok = response.status in 200..299
        if (ok) cacheMutex.withLock {
            likedIds?.let { if (liked) it += commentId else it -= commentId }
            cache.clear()
        }
        return ok
    }

    /**
     * Posts as the signed-in user to the title, one of its seasons or one episode (their Trakt ids
     * are looked up from the show). 422 is Trakt's validation (too short, or not English).
     */
    internal suspend fun post(meta: MetaDetails, season: Int?, episode: Int?, text: String, spoiler: Boolean): Pair<Int, String?> {
        val headers = TraktAuthRepository.authorizedHeaders() ?: return 401 to null
        val target = resolveCommentsTarget(meta) ?: return 404 to null
        // Trakt refuses a bare {"ids":{"imdb":…}} item with 400 invalid_request_body: it wants the
        // item as its API returns it (title, year and ids), so the show or film is looked up first.
        val item = when {
            target.type == TraktCommentsType.MOVIE ->
                "movie" to (traktItem(headers, "https://api.trakt.tv/movies/${target.pathId}") ?: return 404 to null)
            season != null && episode != null -> {
                val id = traktChildId(headers, "https://api.trakt.tv/shows/${target.pathId}/seasons/$season/episodes/$episode") ?: return 404 to null
                "episode" to """{"ids":{"trakt":$id}}"""
            }
            season != null -> {
                val id = traktSeasonId(headers, target.pathId, season) ?: return 404 to null
                "season" to """{"ids":{"trakt":$id}}"""
            }
            else -> "show" to (traktItem(headers, "https://api.trakt.tv/shows/${target.pathId}") ?: return 404 to null)
        }
        val body = traktCommentBody(item.first, item.second, text, spoiler)
        val response = runCatching {
            httpRequestRaw("POST", "https://api.trakt.tv/comments", mapOf("Content-Type" to "application/json") + headers, body)
        }.onFailure { error -> log.w { "Trakt comment post failed to send: ${error.message}" } }.getOrNull() ?: return 0 to null
        if (response.status in 200..299) cacheMutex.withLock { cache.clear() }
        else log.w { "Trakt comment post refused: ${response.status} ${response.body.take(300)}" }
        return response.status to response.body.takeIf { response.status !in 200..299 }
    }

    /** The item as Trakt's summary returns it, cut to title, year and ids (the shape /comments expects). */
    private suspend fun traktItem(headers: Map<String, String>, url: String): String? = runCatching {
        val response = httpRequestRaw("GET", url, headers, "")
        if (response.status !in 200..299) return@runCatching null
        traktCommentItem(response.body)
    }.getOrNull()


    private suspend fun traktChildId(headers: Map<String, String>, url: String): Long? = runCatching {
        val response = httpRequestRaw("GET", url, headers, "")
        if (response.status !in 200..299) return@runCatching null
        commentsJson.decodeFromString<TraktIdsHolderDto>(response.body).ids?.trakt
    }.getOrNull()

    private suspend fun traktSeasonId(headers: Map<String, String>, showId: String, season: Int): Long? = runCatching {
        val response = httpRequestRaw("GET", "https://api.trakt.tv/shows/$showId/seasons", headers, "")
        if (response.status !in 200..299) return@runCatching null
        commentsJson.decodeFromString<List<TraktSeasonIdDto>>(response.body).firstOrNull { it.number == season }?.ids?.trakt
    }.getOrNull()

    private suspend fun resolveCommentsTarget(meta: MetaDetails): ResolvedCommentsTarget? {
        val type = resolveCommentsType(meta) ?: return null
        val directPathId = resolveDirectPathId(meta)
        if (!directPathId.isNullOrBlank()) {
            return ResolvedCommentsTarget(type, directPathId)
        }
        val tmdbId = resolveTmdbCandidate(meta) ?: return null
        return resolveViaTraktSearch(type, tmdbId)
    }

    private fun resolveCommentsType(meta: MetaDetails): TraktCommentsType? {
        val normalized = meta.type.trim().lowercase()
        return when (normalized) {
            "movie" -> TraktCommentsType.MOVIE
            "series", "show", "tv" -> TraktCommentsType.SHOW
            else -> null
        }
    }

    private fun resolveDirectPathId(meta: MetaDetails): String? {
        val id = meta.id.trim()
        if (id.startsWith("tt") && id.length >= 7) return id
        val parts = id.split(":")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("tt") && trimmed.length >= 7) return trimmed
        }
        return null
    }

    private fun resolveTmdbCandidate(meta: MetaDetails): Int? {
        val id = meta.id.trim()
        val parts = id.split(":")
        for (part in parts) {
            val trimmed = part.trim()
            if (!trimmed.startsWith("tt") && trimmed.all { it.isDigit() } && trimmed.isNotEmpty()) {
                return trimmed.toIntOrNull()
            }
        }
        return null
    }

    private suspend fun resolveViaTraktSearch(
        type: TraktCommentsType,
        tmdbId: Int,
    ): ResolvedCommentsTarget? {
        val headers = TraktAuthRepository.authorizedHeaders() ?: return null
        val url = "https://api.trakt.tv/search/tmdb/$tmdbId?type=${type.apiValue}"
        return try {
            val responseText = httpGetTextWithHeaders(url, headers)
            val results = commentsJson.decodeFromString<List<TraktCommentsSearchResultDto>>(responseText)
            val matched = results.firstOrNull {
                it.type.equals(type.apiValue, ignoreCase = true)
            }
            val ids = when (type) {
                TraktCommentsType.MOVIE -> matched?.movie?.ids
                TraktCommentsType.SHOW -> matched?.show?.ids
            }
            val pathId = ids?.bestPathId() ?: return null
            ResolvedCommentsTarget(type, pathId)
        } catch (e: Exception) {
            log.w(e) { "TMDB→Trakt lookup failed for tmdbId=$tmdbId" }
            null
        }
    }
}

private data class ResolvedCommentsTarget(
    val type: TraktCommentsType,
    val pathId: String,
)

private fun TraktCommentsIdsDto.bestPathId(): String? {
    return when {
        !imdb.isNullOrBlank() -> imdb
        trakt != null -> trakt.toString()
        !slug.isNullOrBlank() -> slug
        else -> null
    }
}

private fun filterDisplayableComments(comments: List<TraktCommentDto>): List<TraktCommentDto> {
    return comments.filter { !it.comment.isNullOrBlank() }
}

private fun containsInlineSpoilers(comment: String?): Boolean {
    if (comment.isNullOrBlank()) return false
    return INLINE_SPOILER_REGEX.containsMatchIn(comment)
}

private fun stripInlineSpoilerMarkup(comment: String?): String {
    if (comment.isNullOrBlank()) return ""
    return comment
        .replace(INLINE_SPOILER_TAG_REGEX, "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun toReviewModel(dto: TraktCommentDto): TraktCommentReview {
    val authorDisplayName = dto.user?.name
        ?.takeIf { it.isNotBlank() }
        ?: dto.user?.username?.takeIf { it.isNotBlank() }
        ?: runBlocking { getString(Res.string.trakt_user_fallback) }

    return TraktCommentReview(
        id = dto.id,
        authorDisplayName = authorDisplayName,
        authorUsername = dto.user?.username?.takeIf { it.isNotBlank() },
        comment = stripInlineSpoilerMarkup(dto.comment),
        spoiler = dto.spoiler == true,
        containsInlineSpoilers = containsInlineSpoilers(dto.comment),
        review = dto.review == true,
        likes = dto.likes ?: 0,
        rating = dto.userStats?.rating,
        createdAt = dto.createdAt,
        updatedAt = dto.updatedAt,
    )
}

private fun currentTimeMillis(): Long = TraktPlatformClock.nowEpochMs()

@kotlinx.serialization.Serializable
private data class TraktLikedCommentDto(val comment: TraktLikedCommentRef? = null)

@kotlinx.serialization.Serializable
private data class TraktLikedCommentRef(val id: Long? = null)

@kotlinx.serialization.Serializable
private data class TraktTraktIdDto(val trakt: Long? = null)

@kotlinx.serialization.Serializable
private data class TraktIdsHolderDto(val ids: TraktTraktIdDto? = null)

@kotlinx.serialization.Serializable
private data class TraktSeasonIdDto(val number: Int? = null, val ids: TraktTraktIdDto? = null)

/** Trakt as a reviews source: comments by most liked or newest, likes, and posting. */
object TraktCommentsProvider : com.nuvio.app.features.tracking.TrackingCommentsProvider {
    override val providerId = com.nuvio.app.features.tracking.TrackingProviderId.TRAKT
    override val capabilities = com.nuvio.app.features.tracking.TrackingCommentsCapabilities(canLike = true, canPost = true)

    override suspend fun page(
        meta: MetaDetails,
        sort: com.nuvio.app.features.tracking.TrackingCommentsSort,
        page: Int,
    ): com.nuvio.app.features.tracking.TrackingCommentsPage {
        val result = TraktCommentsRepository.getCommentsPage(
            meta,
            page = page,
            sort = if (sort == com.nuvio.app.features.tracking.TrackingCommentsSort.Newest) "newest" else "likes",
        )
        val liked = TraktCommentsRepository.likedCommentIds()
        return com.nuvio.app.features.tracking.TrackingCommentsPage(
            items = result.items.map { it.toTrackingComment(it.id in liked) },
            page = result.currentPage,
            pageCount = result.pageCount,
            itemCount = result.itemCount,
        )
    }

    override suspend fun setLiked(comment: com.nuvio.app.features.tracking.TrackingComment, liked: Boolean): Boolean =
        comment.id.toLongOrNull()?.let { TraktCommentsRepository.setLiked(it, liked) } ?: false

    override suspend fun post(
        meta: MetaDetails,
        scope: com.nuvio.app.features.tracking.TrackingCommentScope,
        text: String,
        spoiler: Boolean,
    ): com.nuvio.app.features.tracking.TrackingCommentPostResult {
        if (com.nuvio.app.features.tracking.TrackingComments.wordCount(text) < capabilities.minWords) {
            return com.nuvio.app.features.tracking.TrackingCommentPostResult.TooShort
        }
        val (status, body) = TraktCommentsRepository.post(meta, scope.season, scope.episode, text, spoiler)
        return when (status) {
            in 200..299 -> com.nuvio.app.features.tracking.TrackingCommentPostResult.Posted
            422 -> com.nuvio.app.features.tracking.TrackingCommentPostResult.TooShort
            else -> com.nuvio.app.features.tracking.TrackingCommentPostResult.Failed(body?.take(200))
        }
    }
}

internal fun TraktCommentReview.toTrackingComment(likedByMe: Boolean = false) = com.nuvio.app.features.tracking.TrackingComment(
    id = id.toString(),
    authorName = authorDisplayName,
    authorUsername = authorUsername,
    text = comment,
    spoiler = spoiler,
    containsInlineSpoilers = containsInlineSpoilers,
    review = review,
    likes = likes,
    likedByMe = likedByMe,
    rating = rating,
    createdAt = createdAt,
    webUrl = "https://trakt.tv/comments/$id",
)

/** Keeps title, year and ids from a Trakt summary: the item object POST /comments accepts. */
internal fun traktCommentItem(summaryJson: String): String? {
    val summary = commentsJson.parseToJsonElement(summaryJson) as? kotlinx.serialization.json.JsonObject ?: return null
    val ids = summary["ids"] as? kotlinx.serialization.json.JsonObject ?: return null
    return kotlinx.serialization.json.JsonObject(
        listOfNotNull(summary["title"]?.let { "title" to it }, summary["year"]?.let { "year" to it }, "ids" to ids).toMap(),
    ).toString()
}

/** The POST /comments body: the item under its type key, the text and the spoiler flag. */
internal fun traktCommentBody(type: String, itemJson: String, text: String, spoiler: Boolean): String =
    """{"$type":$itemJson,"comment":${kotlinx.serialization.json.JsonPrimitive(text)},"spoiler":$spoiler}"""
