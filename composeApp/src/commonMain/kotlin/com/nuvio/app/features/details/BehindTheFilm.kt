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

/** A work a title is based on: "Dune", with "novel · Frank Herbert, 1965" under it. */
internal data class BehindTheFilmWork(val title: String, val form: String?, val author: String?, val year: Int?) {
    /** The muted line under the title, or null when Wikidata has none of it. */
    val detail: String?
        get() = listOfNotNull(form, listOfNotNull(author, year?.toString()).joinToString(", ").ifBlank { null })
            .joinToString(" · ").ifBlank { null }
}

/** "Behind the film" (design doc §9a): what Wikidata knows about how a title was made. */
internal data class BehindTheFilm(
    val wikidataId: String,
    val basedOn: List<BehindTheFilmWork>,
    val directedBy: List<BehindTheFilmPerson>,
    val music: List<BehindTheFilmPerson>,
    val cinematography: List<BehindTheFilmPerson>,
    val filmedIn: List<String>,
    /** Award categories won (P166) and nominated for but not won (P1411), as Wikidata labels them. */
    val awardsWon: List<String> = emptyList(),
    val awardsNominated: List<String> = emptyList(),
) {
    /** True when the Behind the film tile has a column to show (awards appear in More instead). */
    val hasTile: Boolean
        get() = basedOn.isNotEmpty() || directedBy.isNotEmpty() || music.isNotEmpty() || cinematography.isNotEmpty() || filmedIn.isNotEmpty()

    val isEmpty: Boolean
        get() = !hasTile && awardsWon.isEmpty() && awardsNominated.isEmpty()

    val url: String get() = "https://www.wikidata.org/wiki/$wikidataId"
}

/**
 * Looks a title up on Wikidata by its IMDb id, once per id (kept for the session): based on (P144,
 * with the work's form P7937, author P50 and first publication P577), director (P57), composer (P86), cinematographer (P344) and filming
 * location (P915), awards won (P166) and nominations (P1411), with each person's TMDB id (P4985). A failure or no match hides the tile quietly.
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
        SELECT ?item ?basedOnLabel ?authorLabel ?formLabel ?published ?role ?personLabel ?personTmdb ?placeLabel ?awardLabel ?awardKind WHERE {
          ?item wdt:P345 "$imdbId" .
          {
            ?item wdt:P144 ?basedOn . OPTIONAL { ?basedOn wdt:P50 ?author . }
            OPTIONAL { ?basedOn wdt:P7937 ?form . } OPTIONAL { ?basedOn wdt:P577 ?published . }
          } UNION {
            VALUES (?prop ?role) { (wdt:P57 "director") (wdt:P86 "music") (wdt:P344 "cinematography") }
            ?item ?prop ?person . OPTIONAL { ?person wdt:P4985 ?personTmdb . }
          } UNION {
            ?item wdt:P915 ?place .
          } UNION {
            ?item wdt:P166 ?award . BIND("won" AS ?awardKind)
          } UNION {
            ?item wdt:P1411 ?award . BIND("nominated" AS ?awardKind)
          }
          SERVICE wikibase:label { bd:serviceParam wikibase:language "$language,en". }
        } LIMIT 300
    """.trimIndent()

    /** Reads the SPARQL JSON results; null when the title is unknown or has nothing to show. */
    internal fun parse(body: String): BehindTheFilm? {
        val bindings = json.parseToJsonElement(body).jsonObject["results"]?.jsonObject?.get("bindings")?.jsonArray ?: return null
        var item: String? = null
        val basedOn = linkedMapOf<String, BehindTheFilmWork>()
        val people = linkedMapOf<String, LinkedHashMap<String, Int?>>()
        val places = linkedSetOf<String>()
        val won = linkedSetOf<String>()
        val nominated = linkedSetOf<String>()
        bindings.forEach { element ->
            val row = element.jsonObject
            fun value(name: String) = (row[name] as? JsonObject)?.get("value")?.jsonPrimitive?.content
            item = item ?: value("item")?.substringAfterLast('/')
            value("basedOnLabel")?.takeIf(::isLabel)?.let { title ->
                val seen = basedOn[title]
                // A work can have several publication dates (editions); the earliest is the one to show.
                val year = value("published")?.let { Regex("^-?\\d{1,4}").find(it.removePrefix("+"))?.value?.toIntOrNull() }
                basedOn[title] = BehindTheFilmWork(
                    title = title,
                    form = seen?.form ?: value("formLabel")?.takeIf(::isLabel),
                    author = seen?.author ?: value("authorLabel")?.takeIf(::isLabel),
                    year = listOfNotNull(seen?.year, year).minOrNull(),
                )
            }
            val role = value("role")
            val person = value("personLabel")?.takeIf(::isLabel)
            if (role != null && person != null) {
                val names = people.getOrPut(role) { linkedMapOf() }
                names[person] = names[person] ?: value("personTmdb")?.toIntOrNull()
            }
            value("placeLabel")?.takeIf(::isLabel)?.let(places::add)
            value("awardLabel")?.takeIf(::isLabel)?.let { award ->
                if (value("awardKind") == "won") won.add(award) else nominated.add(award)
            }
        }
        val wikidataId = item ?: return null
        fun credits(role: String) = people[role].orEmpty().map { (name, tmdb) -> BehindTheFilmPerson(name, tmdb) }.take(3)
        return BehindTheFilm(
            wikidataId = wikidataId,
            basedOn = basedOn.values.take(2),
            directedBy = credits("director"),
            music = credits("music"),
            cinematography = credits("cinematography"),
            filmedIn = places.take(4).toList(),
            awardsWon = won.toList(),
            awardsNominated = (nominated - won).toList(),
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

/** Award families that lead the summary, most prestigious first; others follow by count. */
private val leadingAwards = listOf(
    "Academy Award", "Primetime Emmy Award", "Golden Globe Award", "British Academy Film Award",
    "British Academy Television Award", "Palme d'Or", "Golden Lion", "Golden Bear", "Screen Actors Guild Award",
)

/** Short names, as people say them. */
private val awardShortNames = mapOf(
    "Academy Award" to "Oscar",
    "Primetime Emmy Award" to "Primetime Emmy",
    "British Academy Film Award" to "BAFTA",
    "British Academy Television Award" to "BAFTA TV Award",
    "Golden Globe Award" to "Golden Globe",
    "Screen Actors Guild Award" to "SAG Award",
)

/** "Academy Award for Best Actor" → "Academy Award"; a label with no category is its own family. */
internal fun awardFamily(label: String): String = label.substringBefore(" for ").trim()

/**
 * The Details tab's Awards line from Wikidata: wins per award family, the leading two
 * ("4 Oscars · 3 Black Reel Awards"), then other nominations ("1 nomination"). Null when there
 * are none. Wikidata coverage is uneven: big ceremonies are well recorded, festivals less so.
 */
internal fun summarizeAwards(won: List<String>, nominated: List<String>): String? {
    fun plural(count: Int, family: String): String {
        val name = awardShortNames[family] ?: family
        return "$count " + if (count == 1) name else if (name.endsWith("s")) name else name + "s"
    }
    val families = won.groupingBy(::awardFamily).eachCount().entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { leadingAwards.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenByDescending { it.value })
    val parts = families.take(2).map { plural(it.value, it.key) }.toMutableList()
    val otherWins = families.drop(2).sumOf { it.value }
    if (otherWins > 0) parts += if (otherWins == 1) "1 other win" else "$otherWins other wins"
    if (nominated.isNotEmpty()) parts += if (nominated.size == 1) "1 nomination" else "${nominated.size} nominations"
    return parts.joinToString(" · ").ifBlank { null }
}
