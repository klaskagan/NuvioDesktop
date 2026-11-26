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
import kotlin.math.roundToInt

/** What Wikidata adds to the person page's About tab: its item, and a height when it has one. */
internal data class PersonWikidata(
    val wikidataId: String,
    val heightCm: Int?,
)

/**
 * Looks a person up on Wikidata by their TMDB person id (P4985), once per id for the session, and
 * reads their height (P2048) in whatever unit it is stored. Only about 6% of people with a TMDB id
 * have one, so the About tab shows the row only when it is there. A failure hides it quietly.
 */
internal object PersonWikidataRepository {
    private const val ENDPOINT = "https://query.wikidata.org/sparql"
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<Int, PersonWikidata?>()

    suspend fun load(tmdbPersonId: Int): PersonWikidata? {
        if (tmdbPersonId <= 0) return null
        mutex.withLock { if (tmdbPersonId in cache) return cache[tmdbPersonId] }
        val result = try {
            withTimeoutOrNull(8_000L) {
                val response = httpRequestRaw(
                    method = "GET",
                    url = "$ENDPOINT?format=json&query=${encodeQuery(query(tmdbPersonId))}",
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
        mutex.withLock { cache[tmdbPersonId] = result }
        return result
    }

    internal fun query(tmdbPersonId: Int): String = """
        SELECT ?item ?height ?unit WHERE {
          ?item wdt:P4985 "$tmdbPersonId" .
          OPTIONAL { ?item p:P2048/psv:P2048 [ wikibase:quantityAmount ?height ; wikibase:quantityUnit ?unit ] . }
        } LIMIT 5
    """.trimIndent()

    /** Reads the SPARQL results: the item, and the first height that converts to centimetres. */
    internal fun parse(body: String): PersonWikidata? {
        val bindings = json.parseToJsonElement(body).jsonObject["results"]?.jsonObject?.get("bindings")?.jsonArray ?: return null
        var item: String? = null
        var height: Int? = null
        bindings.forEach { element ->
            val row = element.jsonObject
            fun value(name: String) = (row[name] as? JsonObject)?.get("value")?.jsonPrimitive?.content
            item = item ?: value("item")?.substringAfterLast('/')
            if (height == null) {
                val amount = value("height")?.removePrefix("+")?.toDoubleOrNull()
                val unit = value("unit")?.substringAfterLast('/')
                height = amount?.let { heightInCentimetres(it, unit) }
            }
        }
        return item?.let { PersonWikidata(it, height) }
    }

    private fun encodeQuery(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-_.~") append(c) else append('%').append(((byte.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
        }
    }
}

/** Wikidata units seen on P2048 for people: metre, centimetre, foot, inch. Unknown units are dropped. */
internal fun heightInCentimetres(amount: Double, unitQid: String?): Int? {
    val cm = when (unitQid) {
        "Q11573" -> amount * 100.0
        "Q174728" -> amount
        "Q3710" -> amount * 30.48
        "Q218593" -> amount * 2.54
        else -> return null
    }
    // Anything outside a person's range is a data error (a unit typed as metres, say).
    return cm.roundToInt().takeIf { it in 50..260 }
}

/** "1.80 m", or "5 ft 11 in" for the United States, Liberia and Myanmar. */
internal fun formatPersonHeight(cm: Int, regionCode: String?): String =
    if (regionCode?.uppercase() in setOf("US", "LR", "MM")) {
        val totalInches = (cm / 2.54).roundToInt()
        "${totalInches / 12} ft ${totalInches % 12} in"
    } else {
        val metres = cm / 100.0
        val whole = metres.toInt()
        val hundredths = ((metres - whole) * 100).roundToInt()
        "$whole.${hundredths.toString().padStart(2, '0')} m"
    }
