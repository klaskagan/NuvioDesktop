package com.nuvio.app.features.home

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object HomeCatalogParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCatalog(
        payload: String,
        maxItems: Int? = null,
    ): List<MetaPreview> {
        return parseCatalogResponse(
            payload = payload,
            maxItems = maxItems,
        ).items
    }

    fun parseCatalogResponse(
        payload: String,
        maxItems: Int? = null,
    ): ParsedCatalogResponse {
        val root = json.parseToJsonElement(payload).jsonObject
        val metas = root.array("metas")
        val parsedItems = buildList {
            val seenKeys = mutableSetOf<String>()
            for (element in metas) {
                if (maxItems != null && size >= maxItems) break

                val meta = element as? JsonObject ?: continue
                val id = meta.string("id")
                val type = meta.string("type")
                val name = meta.string("name")

                if (id.isNullOrBlank() || type.isNullOrBlank() || name.isNullOrBlank()) {
                    continue
                }

                val landscapePoster = meta.string("landscapePoster")?.takeIf { it.isNotBlank() }
                val item = MetaPreview(
                    id = id,
                    type = type,
                    name = name,
                    poster = meta.string("poster")?.takeUnless(::isPlaceholderArtworkUrl),
                    banner = meta.string("banner") ?: meta.string("background") ?: landscapePoster,
                    logo = meta.string("logo"),
                    posterShape = meta.string("posterShape").toPosterShape(),
                    description = meta.string("description"),
                    releaseInfo = meta.string("releaseInfo"),
                    rawReleaseDate = meta.string("released"),
                    catalogRank = (meta.string("ranking") ?: meta.string("rank"))?.toIntOrNull()?.takeIf { it > 0 },
                    imdbRating = meta.string("imdbRating"),
                    genres = meta.array("genres").mapNotNull { genre ->
                        genre.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
                    },
                    landscapePoster = landscapePoster,
                    runtime = meta.string("runtime")?.trim()?.takeIf { it.isNotBlank() },
                    status = meta.string("status")?.trim()?.takeIf { it.isNotBlank() },
                    popularity = meta.string("popularity")?.toDoubleOrNull(),
                    voteCount = (meta.string("vote_count") ?: meta.string("voteCount"))?.toDoubleOrNull()?.toInt(),
                    // Lets Search match non-IMDb ids (mal:, tmdb:, tvdb:) to IMDb-only stream addons.
                    alternateIds = setOfNotNull(
                        (meta.string("imdb_id") ?: meta.string("_imdbId"))?.trim()?.takeIf { imdbIdPattern.matches(it) && it != id },
                    ),
                )
                if (seenKeys.add(item.stableKey())) {
                    add(item)
                }
            }
        }
        return ParsedCatalogResponse(
            items = parsedItems,
            rawItemCount = metas.size,
        )
    }

    private val imdbIdPattern = Regex("^tt\\d+$")

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.array(name: String): JsonArray =
        this[name] as? JsonArray ?: JsonArray(emptyList())

    private fun String?.toPosterShape(): PosterShape =
        when (this?.lowercase()) {
            "square" -> PosterShape.Square
            "landscape" -> PosterShape.Landscape
            else -> PosterShape.Poster
        }
}

data class ParsedCatalogResponse(
    val items: List<MetaPreview>,
    val rawItemCount: Int,
)

private val placeholderArtworkPattern = Regex("(?i)(unavailable|no|missing|placeholder)[-_]?(poster|image)")

/** Addon "no artwork" images (e.g. My Lists' unavailable-poster.svg) count as no poster, so other sources can fill in. */
internal fun isPlaceholderArtworkUrl(url: String): Boolean =
    url.isBlank() || placeholderArtworkPattern.containsMatchIn(url.substringAfterLast('/'))
