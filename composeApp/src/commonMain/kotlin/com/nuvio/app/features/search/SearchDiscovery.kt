package com.nuvio.app.features.search

import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.collection.seasonalKeywordIds
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal enum class DiscoveryKind { Provider, Genre, Subgenre, Mood, Decade, Language, Studio, Recent }

internal data class SearchDiscovery(
    val kind: DiscoveryKind,
    val name: String,
    val value: String = "",
    val televisionValue: String = value,
    val keywordId: Int? = null,
    val televisionKeywordId: Int? = keywordId,
) {
    suspend fun filters(television: Boolean, country: String?, today: LocalDate): Map<String, String>? {
        val startDate = if (television) "first_air_date" else "primary_release_date"
        return when (kind) {
            DiscoveryKind.Provider -> mapOf(
                "with_watch_providers" to value,
                "watch_region" to (country ?: return null),
                "with_watch_monetization_types" to "flatrate",
            )
            DiscoveryKind.Genre -> {
                val genre = if (television) televisionValue else value
                if (genre.isBlank()) return null
                buildMap {
                    put("with_genres", genre)
                    (if (television) televisionKeywordId else keywordId)?.let {
                        put("with_keywords", it.toString())
                    }
                }
            }
            DiscoveryKind.Subgenre, DiscoveryKind.Mood -> {
                val resolvedKeywordId = keywordId ?: discoveryKeywordId(value)
                buildMap {
                    put("with_keywords", resolvedKeywordId.toString())
                    if (kind == DiscoveryKind.Mood) put("with_genres", televisionValue)
                }
            }
            DiscoveryKind.Decade -> mapOf("$startDate.gte" to "$value-01-01", "$startDate.lte" to "${value.toInt() + 9}-12-31")
            DiscoveryKind.Language -> mapOf("with_original_language" to value)
            DiscoveryKind.Studio -> mapOf("with_companies" to value)
            DiscoveryKind.Recent -> mapOf(
                "$startDate.gte" to recentDiscoveryStart(today).toString(),
                "$startDate.lte" to today.toString(),
                "sort_by" to "$startDate.desc",
            )
        }
    }
}

private suspend fun discoveryKeywordId(keyword: String): Int =
    seasonalKeywordIds[keyword] ?: TmdbCollectionSourceResolver.searchKeywords(keyword).entries
        .firstOrNull { it.value.equals(keyword, ignoreCase = true) }?.key
        ?: error("TMDB keyword unavailable: $keyword")

internal fun discoveryToday() = LocalDate.parse(CurrentDateProvider.todayIsoDate())
internal fun recentDiscoveryStart(today: LocalDate) = today.minus(DatePeriod(days = 29))

private fun discovery(
    kind: DiscoveryKind,
    name: String,
    value: String,
    vararg aliases: String,
    televisionValue: String = value,
    keywordId: Int? = null,
    televisionKeywordId: Int? = keywordId,
) = (listOf(name.lowercase()) + aliases) to SearchDiscovery(
    kind, name, value, televisionValue, keywordId, televisionKeywordId,
)

private val discoveryAliases = listOf(
    discovery(DiscoveryKind.Provider, "Apple TV", "350", "apple", "apple tv+", "apple tv plus"),
    discovery(DiscoveryKind.Provider, "Netflix", "8"),
    discovery(DiscoveryKind.Provider, "Disney+", "337", "disney", "disney plus"),
    discovery(DiscoveryKind.Provider, "Prime Video", "9|119", "prime", "amazon prime", "amazon prime video"),
    discovery(DiscoveryKind.Provider, "Hulu", "15"),
    discovery(DiscoveryKind.Provider, "HBO Max", "1899", "max", "hbo"),
    discovery(DiscoveryKind.Provider, "Paramount+", "531", "paramount plus"),
    discovery(DiscoveryKind.Provider, "Peacock", "386"),
    discovery(DiscoveryKind.Genre, "Comedy", "35"),
    discovery(DiscoveryKind.Genre, "Romantic comedy", "35,10749", "romcom", "rom-com", televisionValue = "35", televisionKeywordId = seasonalKeywordIds.getValue("romance")),
    discovery(DiscoveryKind.Genre, "Dark comedy", "35", "black comedy", keywordId = 10123),
    discovery(DiscoveryKind.Genre, "Sitcom", "", "sitcoms", televisionValue = "35", keywordId = 193171),
    discovery(DiscoveryKind.Genre, "Drama", "18"),
    discovery(DiscoveryKind.Genre, "Action", "28", televisionValue = "10759"),
    discovery(DiscoveryKind.Genre, "Adventure", "12", televisionValue = "10759"),
    discovery(DiscoveryKind.Genre, "Animation", "16"),
    discovery(DiscoveryKind.Genre, "Documentary", "99", "documentaries"),
    discovery(DiscoveryKind.Genre, "Crime", "80"),
    discovery(DiscoveryKind.Genre, "Family", "10751"),
    discovery(DiscoveryKind.Genre, "Fantasy", "14", televisionValue = "10765"),
    discovery(DiscoveryKind.Genre, "Science fiction", "878", "sci-fi", "scifi", televisionValue = "10765"),
    discovery(DiscoveryKind.Genre, "Horror", "27", televisionValue = ""),
    discovery(DiscoveryKind.Genre, "Thriller", "53", televisionValue = ""),
    discovery(DiscoveryKind.Genre, "Romance", "10749", televisionValue = ""),
    discovery(DiscoveryKind.Genre, "Mystery", "9648"),
    discovery(DiscoveryKind.Genre, "Western", "37"),
    discovery(DiscoveryKind.Subgenre, "Time travel", "time travel", "time-travel"),
    discovery(DiscoveryKind.Subgenre, "Alien invasion", "alien invasion", "aliens", keywordId = 14909),
    discovery(DiscoveryKind.Subgenre, "Space travel", "space travel", "outer space"),
    discovery(DiscoveryKind.Subgenre, "Cyberpunk", "cyberpunk", keywordId = 12190),
    discovery(DiscoveryKind.Subgenre, "Post-apocalyptic", "post-apocalyptic future", "post apocalypse", keywordId = 4458),
    discovery(DiscoveryKind.Subgenre, "Dystopia", "dystopia", "dystopian"),
    discovery(DiscoveryKind.Subgenre, "Heist", "heist", "heists", keywordId = 10051),
    discovery(DiscoveryKind.Subgenre, "Zombies", "zombie", "zombie"),
    discovery(DiscoveryKind.Subgenre, "Haunted house", "haunted house"),
    discovery(DiscoveryKind.Subgenre, "Found footage", "found footage"),
    discovery(DiscoveryKind.Subgenre, "Slasher", "slasher"),
    discovery(DiscoveryKind.Subgenre, "Vampires", "vampire", "vampire"),
    discovery(DiscoveryKind.Mood, "Feel-good", "friendship", "feel good", televisionValue = "35|10751"),
    discovery(DiscoveryKind.Mood, "Cozy mystery", "small town", "cosy mystery", televisionValue = "9648"),
    discovery(DiscoveryKind.Language, "Korean", "ko"),
    discovery(DiscoveryKind.Language, "Japanese", "ja"),
    discovery(DiscoveryKind.Language, "French", "fr"),
    discovery(DiscoveryKind.Language, "Spanish", "es"),
    discovery(DiscoveryKind.Language, "Hindi", "hi"),
    discovery(DiscoveryKind.Language, "English", "en"),
    discovery(DiscoveryKind.Studio, "Pixar", "3"),
    discovery(DiscoveryKind.Studio, "Disney", "2|6125", "walt disney"),
    discovery(DiscoveryKind.Recent, "New releases", "", "recent releases"),
) + (1900..discoveryToday().year step 10).map { year ->
    val short = "${year % 100}".padStart(2, '0')
    discovery(DiscoveryKind.Decade, "${year}s", year.toString(), "${short}s", "${short}’s", "${short}'s")
}

internal fun searchDiscoverySuggestions(query: String): List<SearchDiscovery> {
    val normalized = query.trim().lowercase().replace(Regex("\\s+"), " ")
    val exact = discoveryAliases.filter { normalized in it.first }.map { it.second }
    if (normalized.length < 3) return exact
    val prefixes = discoveryAliases.filter { (aliases, _) -> aliases.any { it.startsWith(normalized) } }.map { it.second }
    val comedyVariants = if (prefixes.any { it.kind == DiscoveryKind.Genre && it.name == "Comedy" }) {
        discoveryAliases.map { it.second }.filter { it.kind == DiscoveryKind.Genre && it.televisionKeywordId != null }
    } else emptyList()
    val relatedSubgenres = when {
        prefixes.any { it.kind == DiscoveryKind.Genre && it.name == "Science fiction" } ->
            setOf("time travel", "alien invasion", "space travel", "cyberpunk", "post-apocalyptic future", "dystopia")
        prefixes.any { it.kind == DiscoveryKind.Genre && it.name == "Horror" } ->
            setOf("zombie", "haunted house", "found footage", "slasher", "vampire")
        else -> emptySet()
    }
    val related = discoveryAliases.map { it.second }.filter {
        it.kind == DiscoveryKind.Subgenre && it.value in relatedSubgenres
    }
    return (exact + prefixes + comedyVariants + related).distinct().take(8)
}

internal suspend fun loadSearchDiscoveryMedium(
    television: Boolean,
    page: Int,
    discovery: SearchDiscovery,
    country: String?,
    today: LocalDate,
) = discovery.filters(television, country, today)?.let { filters ->
    TmdbMetadataService.searchDiscoveryPage(television, page, filters)
}
