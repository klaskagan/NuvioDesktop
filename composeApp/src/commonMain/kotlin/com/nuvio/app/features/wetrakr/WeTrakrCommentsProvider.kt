package com.nuvio.app.features.wetrakr

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tracking.TrackingComment
import com.nuvio.app.features.tracking.TrackingCommentPostResult
import com.nuvio.app.features.tracking.TrackingCommentScope
import com.nuvio.app.features.tracking.TrackingCommentsCapabilities
import com.nuvio.app.features.tracking.TrackingCommentsPage
import com.nuvio.app.features.tracking.TrackingCommentsProvider
import com.nuvio.app.features.tracking.TrackingCommentsSort
import com.nuvio.app.features.tracking.TrackingProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * WeTrakr as a reviews source (api.wetrakr.com, read 2026-11-24): a title's comments by top or new,
 * likes, and posting through `/sync/comments`. WeTrakr opens comments per app: until it has for
 * Nuvio every comment route answers 403 COMMENTS_NOT_ENABLED, and this source steps aside quietly.
 * Every comment shown carries its author, WeTrakr as the platform and a link to it on wetrakr.com.
 */
internal class WeTrakrCommentsProvider(
    private val api: WeTrakrApiClient,
    private val store: WeTrakrAuthStore,
) : TrackingCommentsProvider {
    override val providerId = TrackingProviderId.WETRAKR
    // WeTrakr has no minimum length; texts of 200 words or more show as reviews.
    override val capabilities = TrackingCommentsCapabilities(canLike = true, canPost = true, minWords = 1, reviewWordThreshold = 200)

    private var enabled = true
    override val isAvailable: Boolean get() = enabled

    private val mutex = Mutex()
    private val ids = mutableMapOf<String, Long?>()

    override suspend fun page(meta: MetaDetails, sort: TrackingCommentsSort, page: Int): TrackingCommentsPage {
        val id = weTrakrId(meta) ?: return TrackingCommentsPage(emptyList(), page, 0, 0)
        val response = guarded { api.get(store.scope(), "/${pathType(meta)}/$id/comments", listOf("page" to page.toString(), "sort" to if (sort == TrackingCommentsSort.Newest) "new" else "top")) }
            ?: return TrackingCommentsPage(emptyList(), page, 0, 0)
        val items = (response.json() as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toComment() }
        return TrackingCommentsPage(
            items = items,
            page = page,
            pageCount = response.header("x-pagination-page-count")?.toIntOrNull() ?: page,
            itemCount = response.header("x-pagination-item-count")?.toIntOrNull() ?: items.size,
        )
    }

    override suspend fun setLiked(comment: TrackingComment, liked: Boolean): Boolean {
        val path = "/comments/${comment.id}/like"
        return guarded {
            if (liked) api.post(store.scope(), path, buildJsonObject {}) else api.delete(store.scope(), path, null)
        }?.status in 200..299
    }

    override suspend fun post(meta: MetaDetails, scope: TrackingCommentScope, text: String, spoiler: Boolean): TrackingCommentPostResult {
        if (text.isBlank()) return TrackingCommentPostResult.TooShort
        val target = targetFor(meta, scope) ?: return TrackingCommentPostResult.Failed(null)
        val body = buildJsonObject {
            put(target.first, target.second)
            put("text", text)
            put("spoiler", spoiler)
        }
        return try {
            val response = api.post(store.scope(), "/sync/comments", body)
            if (response.status in 200..299) TrackingCommentPostResult.Posted else TrackingCommentPostResult.Failed(response.errorMessage())
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeTrakrApiException) {
            if (error.errorCode.equals("COMMENTS_NOT_ENABLED", ignoreCase = true)) enabled = false
            TrackingCommentPostResult.Failed(error.message)
        } catch (error: Exception) {
            TrackingCommentPostResult.Failed(error.message)
        }
    }

    /** The JSON key and object a new comment is about: the title, or one of its seasons or episodes by WeTrakr id. */
    private suspend fun targetFor(meta: MetaDetails, scope: TrackingCommentScope): Pair<String, JsonObject>? {
        val isMovie = meta.type == "movie"
        if (isMovie || scope.season == null) {
            val titleIds = titleIds(meta) ?: return null
            return (if (isMovie) "movie" else "show") to buildJsonObject { put("ids", titleIds) }
        }
        val showId = weTrakrId(meta) ?: return null
        val tree = guarded { api.get(store.scope(), "/shows/$showId", listOf("append" to "seasons,episodes")) }?.json() as? JsonObject ?: return null
        val season = (tree["seasons"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { (it["number"] as? JsonPrimitive)?.intOrNull == scope.season } ?: return null
        if (scope.episode == null) {
            val id = (season["id"] as? JsonPrimitive)?.longOrNull ?: return null
            return "season" to buildJsonObject { put("id", id) }
        }
        val episode = (season["episodes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { (it["number"] as? JsonPrimitive)?.intOrNull == scope.episode } ?: return null
        val id = (episode["id"] as? JsonPrimitive)?.longOrNull ?: return null
        return "episode" to buildJsonObject { put("id", id) }
    }

    private fun titleIds(meta: MetaDetails): JsonObject? {
        val imdb = meta.imdbId ?: meta.id.takeIf { it.startsWith("tt") }?.substringBefore(':')
        val tmdb = meta.id.removePrefix("tmdb:").takeIf { meta.id.startsWith("tmdb:") }?.substringBefore(':')?.toLongOrNull()
        if (imdb == null && tmdb == null) return null
        return buildJsonObject {
            imdb?.let { put("imdb", it) }
            tmdb?.let { put("tmdb", it) }
        }
    }

    /** The WeTrakr id of the title, from its IMDb (or TMDB) id, looked up once. */
    private suspend fun weTrakrId(meta: MetaDetails): Long? {
        val key = "${meta.type}:${meta.id}"
        mutex.withLock { if (key in ids) return ids[key] }
        val imdb = meta.imdbId ?: meta.id.takeIf { it.startsWith("tt") }?.substringBefore(':')
        val tmdb = meta.id.removePrefix("tmdb:").takeIf { meta.id.startsWith("tmdb:") }?.substringBefore(':')
        val (source, external) = when {
            imdb != null -> "imdb" to imdb
            tmdb != null -> "tmdb" to tmdb
            else -> return null
        }
        val type = if (meta.type == "movie") "movie" else "show"
        val id = guarded { api.get(store.scope(), "/media/external/$source/$external", listOf("type" to type)) }
            ?.json()?.let { (it as? JsonObject)?.get("id") as? JsonPrimitive }?.longOrNull
        mutex.withLock { ids[key] = id }
        return id
    }

    private fun pathType(meta: MetaDetails) = if (meta.type == "movie") "movies" else "shows"

    /** The title's page on wetrakr.com ("/shows/{id}", "/movies/{id}"), for the Links tile. */
    internal suspend fun webUrl(meta: MetaDetails): String? =
        weTrakrId(meta)?.let { "${WeTrakrConfig.WEBSITE_URL}/${pathType(meta)}/$it" }

    /** A request that steps aside (null) on any failure; COMMENTS_NOT_ENABLED retires the source. */
    private suspend fun guarded(block: suspend () -> WeTrakrResponse): WeTrakrResponse? = try {
        block().takeIf { it.status in 200..299 }
    } catch (error: CancellationException) {
        throw error
    } catch (error: WeTrakrApiException) {
        if (error.status == 403 && error.errorCode.equals("COMMENTS_NOT_ENABLED", ignoreCase = true)) enabled = false
        null
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.toComment(): TrackingComment? {
        val id = (this["id"] as? JsonPrimitive)?.contentOrNull ?: return null
        val user = this["user"] as? JsonObject
        val info = user?.get("info") as? JsonObject
        val author = listOf(info, user).firstNotNullOfOrNull { obj ->
            (obj?.get("display_name") as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                ?: (obj?.get("username") as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        } ?: return null
        val interactions = this["interactions"] as? JsonObject
        val mine = interactions?.get("user") as? JsonObject
        val liked = listOf(this["liked"], mine?.get("liked"), mine?.get("like"), interactions?.get("liked"))
            .firstNotNullOfOrNull { it.flag() } ?: false
        val text = (this["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val source = (this["source"] as? JsonPrimitive)?.contentOrNull?.takeIf { !it.equals("wetrakr", ignoreCase = true) }
        return TrackingComment(
            id = id,
            authorName = author,
            authorUsername = (info?.get("username") ?: user?.get("username"))?.let { (it as? JsonPrimitive)?.contentOrNull },
            text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("</?(b|i|u|s|code|blockquote)>", RegexOption.IGNORE_CASE), ""),
            spoiler = (this["spoiler"] as? JsonPrimitive)?.booleanOrNull == true,
            containsInlineSpoilers = text.contains("[spoiler]", ignoreCase = true),
            review = (this["is_long"] as? JsonPrimitive)?.booleanOrNull == true,
            likes = (this["likes"] as? JsonPrimitive)?.intOrNull ?: 0,
            likedByMe = liked,
            rating = (this["rating"] as? JsonPrimitive)?.intOrNull,
            createdAt = (this["comment_added_at"] as? JsonPrimitive)?.contentOrNull,
            webUrl = "https://wetrakr.com/comments/$id",
            origin = source?.let { name -> "via ${name.replaceFirstChar(Char::uppercase)}" },
        )
    }

    private fun JsonElement?.flag(): Boolean? = when (this) {
        is JsonPrimitive -> booleanOrNull ?: contentOrNull?.let { it.isNotBlank() && it != "0" && it != "false" }
        is JsonObject -> true
        else -> null
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
