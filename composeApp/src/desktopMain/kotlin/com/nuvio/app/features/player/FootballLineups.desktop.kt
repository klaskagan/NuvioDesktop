package com.nuvio.app.features.player

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.network.readBoundedResponseBody
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.util.prefs.Preferences
import java.time.LocalDate
import java.time.ZoneOffset

internal object FootballKeyStore {
    private val prefs get() = Preferences.userRoot().node("com/nuvio/football-local")
    // Separate slot: never transmit an old API-Football credential to PitchAPI.
    fun read(): String = prefs.get("pitchapi-key", "")
    fun save(key: String) {
        if (key.isBlank()) prefs.remove("pitchapi-key") else prefs.put("pitchapi-key", key.trim())
        prefs.remove("api-key")
    }
}
internal class PitchApiProvider(private val key: String) : FootballLineupProvider, AutoCloseable {
    private val client = createApiHttpClient()
    private suspend fun get(path: String): String {
        check(key.isNotBlank()) { "Set your PitchAPI key in Playback Settings." }
        val response = client.get("https://api.pitchapi.dev/v1/$path") {
            header("X-API-KEY", key)
            header("User-Agent", "NuvioDesktop/1.0")
            header("Accept", "application/json")
        }
        check(response.status.value in 200..299) { "Football data unavailable. Check your PitchAPI key or retry." }
        return readBoundedResponseBody(response.bodyAsChannel(), response.headers["Content-Length"]?.toLongOrNull())
    }
    private suspend fun fixtures(date: LocalDate) =
        FootballLineupDecoder.matches(get("date/$date?status=all"))

    suspend fun validate() { fixtures(LocalDate.now(ZoneOffset.UTC)) }
    override suspend fun matches(team: String, date: String): List<FootballMatch> {
        return fixtures(LocalDate.parse(date)).filter {
            it.home.contains(team, ignoreCase = true) || it.away.contains(team, ignoreCase = true)
        }
    }
    suspend fun resolve(title: String): List<FootballMatch> {
        fun normalize(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "").lowercase()
            .replace(Regex("man[ .-]+utd|man[ .-]+united"), "manchester united")
            .replace("&", " and ")
            .replace(Regex("""\bafc bournemouth\b"""), "bournemouth")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        val normalized = normalize(title)
        val explicit = Regex("""\d{4}-\d{2}-\d{2}""").find(title)?.value
        val dates = if (explicit != null) listOf(LocalDate.parse(explicit))
            else LocalDate.now(ZoneOffset.UTC).let { today -> (0L..14L).map(today::minusDays) }
        return dates.flatMap { date ->
            fixtures(date).filter {
                normalized.contains(normalize(it.home)) && normalized.contains(normalize(it.away))
            }
        }.distinctBy { it.id }
    }
    override suspend fun lineups(fixture: String): List<FootballTeam> {
        require(Regex("m_[A-Za-z0-9]+").matches(fixture))
        return FootballLineupDecoder.lineups(get("matches/$fixture/lineups"))
    }
    override fun close() = client.close()
}
