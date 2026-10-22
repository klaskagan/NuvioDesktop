package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.RawHttpResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val API = "https://api.trakt.tv"
private val listJson = Json { ignoreUnknownKeys = true }

/** Discover owned and browsable lists separately: smart lists are not personal lists. */
internal suspend fun fetchTraktLibraryListTabs(
    request: suspend (String) -> RawHttpResponse,
): List<TraktListTab> {
    val personal = listJson.decodeFromString<List<LibraryListSummary>>(
        request("$API/users/me/lists").body,
    ).filter { it.type == null || it.type.equals("personal", ignoreCase = true) }
        .mapNotNull { it.toTab(TraktListType.PERSONAL) }
    val smart = listJson.decodeFromString<List<LibraryListSummary>>(
        request("$API/users/me/smart-lists").body,
    ).mapNotNull { it.toTab(TraktListType.SMART) }
    val liked = fetchTraktLibraryPages(
        fetchPage = { page -> request("$API/users/me/likes/lists?page=$page&limit=100") },
        decodeItems = { listJson.decodeFromString<List<LibraryLikedList>>(it) },
    ).mapNotNull { it.list?.toTab(TraktListType.LIKED) }
    // A user can like their own list. Keep the editable owned version in that case.
    return (personal + smart + liked).distinctBy {
        val namespace = if (it.type == TraktListType.SMART) "smart" else "list"
        "$namespace:${it.traktListId ?: it.key}"
    }
}

@Serializable
private data class LibraryLikedList(val list: LibraryListSummary? = null)

@Serializable
private data class LibraryListSummary(
    val name: String? = null,
    val description: String? = null,
    val privacy: String? = null,
    val type: String? = null,
    @SerialName("sort_by") val sortBy: String? = null,
    @SerialName("sort_how") val sortHow: String? = null,
    val ids: LibraryListIds? = null,
) {
    fun toTab(kind: TraktListType): TraktListTab? {
        val id = ids?.trakt
        // Public list items require a numeric ID; smart list slugs are owner scoped.
        val path = id?.toString() ?: if (kind == TraktListType.PERSONAL) ids?.slug else null
        if (path.isNullOrBlank()) return null
        val prefix = when (kind) {
            TraktListType.PERSONAL -> "trakt:list:"
            TraktListType.SMART -> "trakt:smart:"
            TraktListType.LIKED -> "trakt:liked:"
            TraktListType.WATCHLIST -> return null
        }
        return TraktListTab(
            key = "$prefix$path",
            title = name?.takeIf { it.isNotBlank() } ?: "List $path",
            type = kind,
            traktListId = id,
            slug = ids?.slug,
            description = description,
            privacy = TraktListPrivacy.fromApi(privacy),
            sortBy = sortBy,
            sortHow = sortHow,
        )
    }
}

@Serializable
private data class LibraryListIds(val trakt: Long? = null, val slug: String? = null)

internal fun traktLibraryListItemsUrl(tab: TraktListTab): String {
    val id = tab.traktListId?.toString() ?: tab.slug.orEmpty()
    return when (tab.type) {
        TraktListType.SMART -> "$API/smart-lists/$id/items"
        TraktListType.LIKED -> "$API/lists/$id/items"
        TraktListType.PERSONAL -> "$API/users/me/lists/$id/items"
        TraktListType.WATCHLIST -> error("Watchlist uses its own movie and show endpoints")
    }
}
