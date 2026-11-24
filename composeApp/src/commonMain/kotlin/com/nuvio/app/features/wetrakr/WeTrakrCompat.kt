package com.nuvio.app.features.wetrakr

import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The fork maps anime catalogue ids (Kitsu, MAL, AniList, AniDB) to IMDb numbering before writing to
 * WeTrakr; that mapper and AniList itself are not on this branch, so writes go out with the ids
 * they have. Anime that WeTrakr only knows by IMDb id will not resolve until the mapper is ported.
 */
internal object WeTrakrAnimeIds {
    fun toImdbReference(media: TrackingMediaReference): TrackingMediaReference = media
    fun toImdbReferences(items: Collection<TrackingMediaReference>): List<TrackingMediaReference> = items.toList()
    fun toImdbHistoryItems(items: Collection<TrackingHistoryItem>): List<TrackingHistoryItem> = items.toList()
}

/** Counts entries in a `{"movies": [...], "shows": [...]}` not-found object. */
internal fun JsonElement?.notFoundTotal(): Int =
    (this as? JsonObject)?.values?.sumOf { value ->
        when (value) {
            is JsonArray -> value.size
            is JsonPrimitive -> value.content.toIntOrNull() ?: 0
            else -> 0
        }
    } ?: 0
