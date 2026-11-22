package com.nuvio.app.features.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTopTenTest {
    private fun catalog(type: String, id: String, name: String = id) =
        SearchAddonCatalog("https://aio.example/manifest.json", "AIOMetadata", type, id, name)

    private val installed = listOf(
        catalog("movie", "tmdb.top"),
        catalog("movie", "flixpatrol.netflix.lt.movie", "Top 10 Movies on Netflix (Lithuania)"),
        catalog("series", "flixpatrol.netflix.lt.series", "Top 10 TV Shows on Netflix (Lithuania)"),
        catalog("movie", "flixpatrol.amazon-prime.gb.movie"),
        catalog("all", "flixpatrol.hbo-max.lt.all", "Top 10 on HBO Max (Lithuania)"),
        catalog("movie", "streaming.nfx", "Netflix (Movies)"),
    )

    private fun tag(name: String) = searchDiscoverySuggestions(name).first { it.name == name }

    @Test
    fun providerTagsPickTheirChartsShowsFirst() {
        val netflix = topTenCatalogsFor(tag("Netflix"), installed)
        assertEquals(listOf("flixpatrol.netflix.lt.series", "flixpatrol.netflix.lt.movie"), netflix.map { it.catalog.id })
        assertEquals(listOf(true, false), netflix.map { it.television })
        // The service slug differs from the tag name, and other services' charts don't leak in.
        assertEquals(listOf("flixpatrol.amazon-prime.gb.movie"), topTenCatalogsFor(tag("Prime Video"), installed).map { it.catalog.id })
    }

    @Test
    fun combinedChartsAndMissingChartsAreHandled() {
        val hbo = topTenCatalogsFor(tag("HBO Max"), installed).single()
        assertEquals(null, hbo.television) // one "all" chart, shown under All only
        assertTrue(topTenCatalogsFor(tag("Peacock"), installed).isEmpty())
        assertTrue(topTenCatalogsFor(tag("Comedy"), installed).isEmpty())
    }

    @Test
    fun eachChartPrefersTheStreamingCountryThenTheUnitedKingdom() {
        val charts = listOf(
            catalog("movie", "flixpatrol.netflix.us.movie"),
            catalog("movie", "flixpatrol.netflix.gb.movie"),
            catalog("movie", "flixpatrol.netflix.lt.movie"),
            catalog("series", "flixpatrol.netflix.us.series"),
            catalog("series", "flixpatrol.netflix.gb.series"),
            catalog("all", "flixpatrol.crunchyroll.us.all"),
        )
        fun ids(name: String, region: String?) = topTenCatalogsFor(tag(name), charts, region).map { it.catalog.id }
        // Lithuania has a movie chart but no series chart: series falls back to the UK, not the US.
        assertEquals(listOf("flixpatrol.netflix.gb.series", "flixpatrol.netflix.lt.movie"), ids("Netflix", "LT"))
        assertEquals(listOf("flixpatrol.netflix.us.series", "flixpatrol.netflix.us.movie"), ids("Netflix", "us"))
        // No region known, or a country without any chart: the UK chart.
        assertEquals(listOf("flixpatrol.netflix.gb.series", "flixpatrol.netflix.gb.movie"), ids("Netflix", null))
        assertEquals(listOf("flixpatrol.netflix.gb.series", "flixpatrol.netflix.gb.movie"), ids("Netflix", "FR"))
        // Neither the country nor the UK: whatever chart the addon lists first.
        assertEquals(listOf("flixpatrol.crunchyroll.us.all"), ids("Crunchyroll", "LT"))
    }
}
