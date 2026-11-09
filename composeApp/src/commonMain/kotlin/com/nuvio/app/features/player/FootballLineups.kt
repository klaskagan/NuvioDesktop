package com.nuvio.app.features.player

import kotlinx.serialization.json.*

/** Only pre-match information may cross this boundary. Never retain raw fixture JSON. */
data class FootballMatch(val id: String, val home: String, val away: String, val competition: String, val kickoff: String)
data class FootballPlayer(val name: String, val number: String, val grid: String? = null, val photo: String? = null,
    val pitchX: Float? = null, val pitchY: Float? = null)
data class FootballTeam(val name: String, val formation: String, val coach: String, val starters: List<FootballPlayer>, val bench: List<FootballPlayer>, val badge: String? = null)
interface FootballLineupProvider {
    suspend fun matches(team: String, date: String): List<FootballMatch>
    suspend fun lineups(fixture: String): List<FootballTeam>
}
object FootballLineupDecoder {
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.objectOrEmpty(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())
    private fun data(body: String): JsonObject {
        val o = Json.parseToJsonElement(body).jsonObject
        require(o["error"] == null) { "Football data unavailable. Check your PitchAPI key." }
        return o["data"] as? JsonObject ?: error("Football data unavailable.")
    }
    fun matches(body: String): List<FootballMatch> =
        (data(body)["matches"] as? JsonArray).orEmpty().map { element ->
            val o = element.jsonObject
            FootballMatch(o.text("id"), o.objectOrEmpty("home_team").text("name"),
                o.objectOrEmpty("away_team").text("name"), o.objectOrEmpty("league").text("name"), o.text("time_utc"))
        }
    fun lineups(body: String): List<FootballTeam> {
        val d = data(body)
        return listOf("home", "away").mapNotNull { side ->
            val o = d.objectOrEmpty(side)
            // Never silently substitute a predicted XI for the original team sheet.
            if (o["confirmed"] != JsonPrimitive(true)) return@mapNotNull null
            val team = d.objectOrEmpty("${side}_team")
            fun players(key: String) = (o[key] as? JsonArray).orEmpty().map {
                val player = it.jsonObject
                fun coordinate(key: String) = (player[key] as? JsonPrimitive)?.floatOrNull?.takeIf { n -> n.isFinite() && n in 0f..1f }
                FootballPlayer(player.text("name"), player.text("shirt_number"),
                    pitchX = coordinate("pitch_x"), pitchY = coordinate("pitch_y"))
            }
            FootballTeam(team.text("name"), o.text("formation"), o.objectOrEmpty("coach").text("name"),
                players("starters"), players("subs"), team.text("image_url").ifBlank { null })
        }
    }
}

internal fun supportsFootballLineups(contentType: String?): Boolean =
    contentType.equals("sport", ignoreCase = true) || contentType.equals("sports", ignoreCase = true)
