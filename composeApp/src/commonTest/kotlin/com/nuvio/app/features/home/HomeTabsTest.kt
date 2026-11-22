package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.library.LibrarySortOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun candidate(key: String, type: String, enabled: Boolean = true, forYou: Boolean = false) =
        HomeHeroSourceCandidate(key = key, type = type, enabled = enabled, forYouSource = forYou)

    private val heroCatalogs = listOf(
        candidate("airing-next", "series", forYou = true),
        candidate("trending-movies", "movie", forYou = true),
        candidate("popular-shows", "series"),
        candidate("acclaimed-movies", "movie"),
    )

    @Test
    fun aTabsOwnHeroPicksWinAndIgnoreCatalogsOfTheOtherType() {
        assertEquals(
            listOf("popular-shows"),
            resolveTabHeroSourceKeys(HomeTab.Shows, heroCatalogs, chosen = setOf("popular-shows", "trending-movies")),
        )
    }

    @Test
    fun withoutPicksATabUsesTheForYouHeroCatalogsOfItsType() {
        assertEquals(listOf("airing-next"), resolveTabHeroSourceKeys(HomeTab.Shows, heroCatalogs, chosen = emptySet()))
        assertEquals(listOf("trending-movies"), resolveTabHeroSourceKeys(HomeTab.Movies, heroCatalogs, chosen = emptySet()))
    }

    @Test
    fun withNoForYouCatalogOfItsTypeATabFallsBackToItsFirstVisibleCatalog() {
        val moviesOnlyHero = listOf(
            candidate("trending-movies", "movie", forYou = true),
            candidate("hidden-shows", "series", enabled = false),
            candidate("popular-shows", "series"),
        )
        assertEquals(listOf("popular-shows"), resolveTabHeroSourceKeys(HomeTab.Shows, moviesOnlyHero, chosen = emptySet()))
        assertTrue(resolveTabHeroSourceKeys(HomeTab.Shows, listOf(candidate("trending-movies", "movie")), emptySet()).isEmpty())
    }

    @Test
    fun aTabWithoutItsOwnHeroShowsTheForYouOne() {
        val forYou = items("movie", 2)
        val shows = items("series", 2)
        val state = HomeUiState(heroItems = forYou, showHeroItems = shows)
        assertEquals(shows, state.heroItemsFor(HomeTab.Shows))
        assertEquals(forYou, state.heroItemsFor(HomeTab.Movies))
        assertEquals(forYou, state.heroItemsFor(HomeTab.MyLists))
    }
}
