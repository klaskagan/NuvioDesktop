package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.library.LibrarySortOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeTabsTest {
    private fun items(type: String, count: Int) = List(count) { MetaPreview(id = "$type$it", type = type, name = "$type $it") }

    private fun addonSection(key: String, type: String, count: Int = 12, itemType: String = type) = HomeCatalogSection(
        key = key,
        title = key,
        subtitle = "",
        addonName = "Addon",
        target = CatalogTarget.Addon(manifestUrl = "https://example.test/manifest.json", contentType = type, catalogId = key),
        items = items(itemType, count),
    )

    private val movies = addonSection("popular-movies", "movie")
    private val shows = addonSection("popular-shows", "series")
    private val library = HomeCatalogSection(
        key = "library",
        title = "Library",
        subtitle = "",
        addonName = "",
        target = CatalogTarget.Library(contentType = "movie", sectionType = "saved", sortOption = LibrarySortOption.DEFAULT),
        items = items("movie", 3),
    )

    @Test
    fun catalogsFollowTheirTypeAndForYouShowsEverything() {
        assertTrue(HomeTab.entries.filter { movies.belongsTo(it) } == listOf(HomeTab.ForYou, HomeTab.Movies))
        assertTrue(HomeTab.entries.filter { shows.belongsTo(it) } == listOf(HomeTab.ForYou, HomeTab.Shows))
        assertTrue(library.belongsTo(HomeTab.MyLists))
        assertFalse(movies.belongsTo(HomeTab.MyLists))
    }

    @Test
    fun untypedCatalogGoesByMostOfItsItems() {
        val mixed = addonSection("mixed", type = "other", itemType = "series")
        assertEquals(HomeTab.Shows, mixed.homeMediaTab())
        assertTrue(mixed.belongsTo(HomeTab.Shows))
        assertFalse(mixed.belongsTo(HomeTab.Movies))
    }

    @Test
    fun firstLongAddonCatalogOfTheTabLeadsAsRanking() {
        val short = addonSection("short-movies", "movie", count = 4)
        val ordered = listOf(library, short, shows, movies)
        assertEquals("popular-shows", homeLeadRankingKey(HomeTab.ForYou, ordered, emptyMap()))
        assertEquals("popular-movies", homeLeadRankingKey(HomeTab.Movies, ordered, emptyMap()))
        assertNull(homeLeadRankingKey(HomeTab.MyLists, ordered, emptyMap()))
    }

    @Test
    fun aRowStyleTheUserChoseIsNotOverridden() {
        val chosen = mapOf("popular-movies" to HomeRowStyle.Posters)
        assertNull(homeLeadRankingKey(HomeTab.Movies, listOf(movies), chosen))
    }
}
