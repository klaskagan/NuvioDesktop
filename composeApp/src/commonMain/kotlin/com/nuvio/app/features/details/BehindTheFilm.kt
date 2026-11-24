package com.nuvio.app.features.details

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A person Wikidata names, with their TMDB id when Wikidata knows it (so they can open their page). */
internal data class BehindTheFilmPerson(val name: String, val tmdbId: Int?)

/** "Behind the film" (design doc §9a): what Wikidata knows about how a title was made. */
internal data class BehindTheFilm(
    val wikidataId: String,
    val basedOn: List<String>,
    val directedBy: List<BehindTheFilmPerson>,
    val music: List<BehindTheFilmPerson>,
    val cinematography: List<BehindTheFilmPerson>,
    val filmedIn: List<String>,
) {
    val isEmpty: Boolean
        get() = basedOn.isEmpty() && directedBy.isEmpty() && music.isEmpty() && cinematography.isEmpty() && filmedIn.isEmpty()

    val url: String get() = "https://www.wikidata.org/wiki/$wikidataId"
}

/**
 * Looks a title up on Wikidata by its IMDb id, once per id (kept for the session): based on (P144,
 * with the work's author P50), director (P57), composer (P86), cinematographer (P344) and filming
 * location (P915), with each person's TMDB id (P4985). A failure or no match hides the tile quietly.
 */
internal object BehindTheFilmRepository {
    private const val ENDPOINT = "https://query.wikidata.org/sparql"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, BehindTheFilm?>()

    suspend fun load(imdbId: String, language: String = "en"): BehindTheFilm? {
        val id = imdbId.trim().takeIf { it.matches(Regex("tt\\d+")) } ?: return null
        mutex.withLock { if (id in cache) return cache[id] }
        val result = try {
            withTimeoutOrNull(8_000L) {
                val response = httpRequestRaw(
                    method = "GET",
                    url = "$ENDPOINT?format=json&query=${encode(query(id, language))}",
                    // Wikidata asks automated clients to identify themselves; no personal details.
                    headers = mapOf("Accept" to "application/sparql-results+json", "User-Agent" to "Nuvio/1.0 (desktop media app)"),
                    body = "",
                )
                if (response.status !in 200..299) null else parse(response.body)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        mutex.withLock { cache[id] = result }
        return result
    }

    internal fun query(imdbId: String, language: String): String = """
        SELECT ?item ?basedOnLabel ?authorLabel ?role ?personLabel ?personTmdb ?placeLabel WHERE {
          ?item wdt:P345 "$imdbId" .
          {
            ?item wdt:P144 ?basedOn . OPTIONAL { ?basedOn wdt:P50 ?author . }
          } UNION {
            VALUES (?prop ?role) { (wdt:P57 "director") (wdt:P86 "music") (wdt:P344 "cinematography") }
            ?item ?prop ?person . OPTIONAL { ?person wdt:P4985 ?personTmdb . }
          } UNION {
            ?item wdt:P915 ?place .
          }
          SERVICE wikibase:label { bd:serviceParam wikibase:language "$language,en". }
        } LIMIT 80
    """.trimIndent()

    /** Reads the SPARQL JSON results; null when the title is unknown or has nothing to show. */
    internal fun parse(body: String): BehindTheFilm? {
        val bindings = json.parseToJsonElement(body).jsonObject["results"]?.jsonObject?.get("bindings")?.jsonArray ?: return null
        var item: String? = null
        val basedOn = linkedMapOf<String, String?>()
        val people = linkedMapOf<String, LinkedHashMap<String, Int?>>()
        val places = linkedSetOf<String>()
        bindings.forEach { element ->
            val row = element.jsonObject
            fun value(name: String) = (row[name] as? JsonObject)?.get("value")?.jsonPrimitive?.content
            item = item ?: value("item")?.substringAfterLast('/')
            value("basedOnLabel")?.takeIf(::isLabel)?.let { work -> basedOn[work] = basedOn[work] ?: value("authorLabel")?.takeIf(::isLabel) }
            val role = value("role")
            val person = value("personLabel")?.takeIf(::isLabel)
            if (role != null && person != null) {
                val names = people.getOrPut(role) { linkedMapOf() }
                names[person] = names[person] ?: value("personTmdb")?.toIntOrNull()
            }
            value("placeLabel")?.takeIf(::isLabel)?.let(places::add)
        }
        val wikidataId = item ?: return null
        fun credits(role: String) = people[role].orEmpty().map { (name, tmdb) -> BehindTheFilmPerson(name, tmdb) }.take(3)
        return BehindTheFilm(
            wikidataId = wikidataId,
            basedOn = basedOn.map { (work, author) -> if (author != null) "$work · $author" else work }.take(2),
            directedBy = credits("director"),
            music = credits("music"),
            cinematography = credits("cinematography"),
            filmedIn = places.take(4).toList(),
        ).takeUnless { it.isEmpty }
    }

    /** An unlabelled entity comes back as its bare Q-id; it is not a name to show. */
    private fun isLabel(value: String) = value.isNotBlank() && !value.matches(Regex("Q\\d+"))

    private fun encode(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-_.~") append(c) else append('%').append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
        }
    }
}
