package com.nuvio.app.features.wetrakr

import com.nuvio.app.features.tracking.TrackingFavoritesProvider
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.watchprogress.WatchProgressClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * WeTrakr favorites (`/sync/favorites`): the user's favorite films and shows, read in compact pages
 * and kept for a few minutes per profile; a change updates that copy.
 */
internal class WeTrakrFavoritesProvider(
    private val api: WeTrakrApiClient,
    private val store: WeTrakrAuthStore,
) : TrackingFavoritesProvider {
    override val providerId = TrackingProviderId.WETRAKR
    private val mutex = Mutex()
    private var cached: Triple<Int, Long, MutableSet<String>>? = null

    override suspend fun isFavorite(profileId: Int, media: TrackingMediaReference): Boolean? {
        val keys = keysOf(media).ifEmpty { return null }
        val known = favorites(profileId) ?: return null
        return keys.any { it in known }
    }

    override suspend fun setFavorite(profileId: Int, media: TrackingMediaReference, favorite: Boolean): Boolean {
        val scope = store.scope().takeIf { it.profileId == profileId } ?: return false
        val ids = media.ids.toWeTrakrIds().takeIf { it.hasAny } ?: return false
        val body = buildJsonObject {
            put(if (media.kind == TrackingMediaKind.MOVIE) "movies" else "shows", buildJsonArray { add(buildJsonObject { put("ids", ids.toJson()) }) })
        }
        val ok = try {
            val response = api.post(scope, if (favorite) "/sync/favorites" else "/sync/favorites/remove", body)
            response.status in 200..299 && response.notFoundCount() == 0
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (ok) mutex.withLock {
            cached?.takeIf { it.first == profileId }?.third?.let { set -> keysOf(media).forEach { if (favorite) set += it else set -= it } }
        }
        return ok
    }

    // People are favorited by WeTrakr person id (`people` in /sync/favorites); a TMDB person id is
    // resolved once through /media/external/tmdb/{id}?type=person.
    override val supportsPeople: Boolean get() = true
    private var cachedPeople: Triple<Int, Long, MutableSet<String>>? = null
    private val personIds = mutableMapOf<Int, Long?>()

    override suspend fun isPersonFavorite(profileId: Int, tmdbPersonId: Int): Boolean? {
        val known = favoritePeople(profileId) ?: return null
        if ("tmdb:person:$tmdbPersonId" in known) return true
        val id = weTrakrPersonId(profileId, tmdbPersonId) ?: return false
        return "wt:$id" in known
    }

    override suspend fun setPersonFavorite(profileId: Int, tmdbPersonId: Int, favorite: Boolean): Boolean {
        val scope = store.scope().takeIf { it.profileId == profileId } ?: return false
        val id = weTrakrPersonId(profileId, tmdbPersonId) ?: return false
        val body = buildJsonObject { put("people", buildJsonArray { add(buildJsonObject { put("id", id) }) }) }
        val ok = try {
            val response = api.post(scope, if (favorite) "/sync/favorites" else "/sync/favorites/remove", body)
            response.status in 200..299 && response.notFoundCount() == 0
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (ok) mutex.withLock {
            cachedPeople?.takeIf { it.first == profileId }?.third?.let { set ->
                listOf("wt:$id", "tmdb:person:$tmdbPersonId").forEach { if (favorite) set += it else set -= it }
            }
        }
        return ok
    }

    private suspend fun weTrakrPersonId(profileId: Int, tmdbPersonId: Int): Long? {
        mutex.withLock { if (tmdbPersonId in personIds) return personIds[tmdbPersonId] }
        val scope = store.scope().takeIf { it.profileId == profileId } ?: return null
        val id = try {
            val response = api.get(scope, "/media/external/tmdb/$tmdbPersonId", listOf("type" to "person"))
            if (response.status !in 200..299) null else (response.json() as? JsonObject)?.weTrakrLong("id")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        mutex.withLock { personIds[tmdbPersonId] = id }
        return id
    }

    private suspend fun favoritePeople(profileId: Int): Set<String>? = mutex.withLock {
        val now = WatchProgressClock.nowEpochMs()
        cachedPeople?.takeIf { it.first == profileId && now - it.second < 5 * 60_000L }?.let { return@withLock it.third.toSet() }
        val scope = store.scope().takeIf { it.profileId == profileId } ?: return@withLock null
        val keys = try {
            api.compactList(scope, "/sync/favorites/people").flatMap { row ->
                listOfNotNull(
                    row.weTrakrLong("id")?.let { "wt:$it" },
                    (row["ids"] as? JsonObject)?.let { ids -> (ids["tmdb"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }?.let { "tmdb:person:$it" },
                )
            }.toMutableSet()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withLock null
        }
        cachedPeople = Triple(profileId, now, keys)
        keys.toSet()
    }

    private suspend fun favorites(profileId: Int): Set<String>? = mutex.withLock {
        val now = WatchProgressClock.nowEpochMs()
        cached?.takeIf { it.first == profileId && now - it.second < 5 * 60_000L }?.let { return@withLock it.third.toSet() }
        val scope = store.scope().takeIf { it.profileId == profileId } ?: return@withLock null
        val keys = try {
            listOf("movies" to "movie", "shows" to "show").flatMap { (path, kind) ->
                api.compactList(scope, "/sync/favorites/$path").flatMap { row -> row.idKeys(kind) }
            }.toMutableSet()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@withLock null
        }
        cached = Triple(profileId, now, keys)
        keys.toSet()
    }

    private fun keysOf(media: TrackingMediaReference): List<String> {
        val kind = if (media.kind == TrackingMediaKind.MOVIE) "movie" else "show"
        return listOfNotNull(
            media.ids.imdb?.takeIf { it.isNotBlank() }?.let { "imdb:$it" },
            media.ids.tmdb?.let { "tmdb:$kind:$it" },
        )
    }

    private fun JsonObject.idKeys(kind: String): List<String> {
        val ids = (this["ids"] as? JsonObject) ?: return emptyList()
        return listOfNotNull(
            (ids["imdb"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { "imdb:$it" },
            (ids["tmdb"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { "tmdb:$kind:$it" },
        )
    }
}
