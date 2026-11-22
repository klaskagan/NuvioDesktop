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
}
