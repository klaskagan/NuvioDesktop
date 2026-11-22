package com.nuvio.app.features.search

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchDiscoveryTest {
    private val today = LocalDate.parse("2026-11-20")

    @Test
    fun recognizesPrefixesAndPreservesAmbiguity() {
        val expected = mapOf(
            "  Apple  " to DiscoveryKind.Provider,
            "time   travel" to DiscoveryKind.Subgenre,
            "feel good" to DiscoveryKind.Mood,
            "90s" to DiscoveryKind.Decade,
            "korean" to DiscoveryKind.Language,
            "pixar" to DiscoveryKind.Studio,
            "new releases" to DiscoveryKind.Recent,
        )
        expected.forEach { (query, kind) ->
            assertEquals(kind, searchDiscoverySuggestions(query).single().kind)
        }
        val comedy = listOf("Comedy", "Romantic comedy", "Dark comedy", "Sitcom")
        assertEquals(comedy, searchDiscoverySuggestions("com").map { it.name })
        assertEquals(comedy, searchDiscoverySuggestions("comedy").map { it.name })
        assertEquals("Alien invasion", searchDiscoverySuggestions("ali").single().name)
        assertTrue(searchDiscoverySuggestions("sci-fi").any { it.name == "Alien invasion" })
        assertTrue(searchDiscoverySuggestions("horror").any { it.name == "Found footage" })
        assertEquals("Netflix", searchDiscoverySuggestions("net").single().name)
        assertTrue(searchDiscoverySuggestions("co").isEmpty())
        assertEquals(listOf(DiscoveryKind.Provider, DiscoveryKind.Studio), searchDiscoverySuggestions("disney").map { it.kind })
        listOf("", "apple tree", "action movie", "oscar", "emmy", "awards").forEach { query ->
            assertTrue(searchDiscoverySuggestions(query).isEmpty(), query)
        }
    }

    @Test
    fun mapsDiscoveryToMediaSpecificTmdbFilters() = runTest {
        suspend fun filters(query: String, television: Boolean = false, country: String? = null) =
            searchDiscoverySuggestions(query).single().filters(television, country, today)

        assertEquals(null, filters("apple"))
        assertEquals(mapOf("with_watch_providers" to "350", "watch_region" to "LT",
            "with_watch_monetization_types" to "flatrate"), filters("apple", country = "LT"))
        assertEquals("9|119", filters("prime", country = "LT")?.get("with_watch_providers"))
        assertEquals(mapOf("with_genres" to "28"), filters("action"))
        assertEquals(mapOf("with_genres" to "10759"), filters("action", television = true))
        assertEquals(mapOf("with_genres" to "35,10749"), filters("romcom"))
        assertEquals(mapOf("with_genres" to "35", "with_keywords" to "9840"), filters("romcom", television = true))
        assertEquals(mapOf("with_genres" to "35", "with_keywords" to "10123"), filters("dark comedy"))
        assertEquals(mapOf("with_genres" to "35", "with_keywords" to "193171"), filters("sitcom", television = true))
        assertEquals(null, filters("sitcom"))
        assertEquals(mapOf("with_keywords" to "4379"), filters("time travel"))
        assertEquals(mapOf("with_keywords" to "14909"), filters("alien invasion", television = true))
        assertEquals(mapOf("with_keywords" to "12190"), filters("cyberpunk"))
        assertEquals(mapOf("with_keywords" to "6054", "with_genres" to "35|10751"), filters("feel good"))
        assertEquals(mapOf("first_air_date.gte" to "1990-01-01", "first_air_date.lte" to "1999-12-31"),
            filters("90s", television = true))
        assertEquals(mapOf("with_original_language" to "ko"), filters("korean"))
        assertEquals(mapOf("with_companies" to "3"), filters("pixar"))
        assertEquals(mapOf("primary_release_date.gte" to "2026-10-22", "primary_release_date.lte" to "2026-11-20",
            "sort_by" to "primary_release_date.desc"), filters("new releases"))
    }
}
