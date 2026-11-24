package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbEntityRailPageResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingRowLayoutTest {
    private fun tag(name: String) = searchDiscoverySuggestions(name).first { it.name == name }
    private fun title(id: String, type: String = "series") = MetaPreview("tmdb:$id", type, id)

    private fun section(key: String, vararg items: MetaPreview) = HomeCatalogSection(
        key = key, title = key, subtitle = "", addonName = "",
        target = CatalogTarget.Addon("https://addon.example/manifest.json", "movie", key),
        items = items.toList(),
    )

    private fun chart(television: Boolean?) = SearchTopTenCatalog(
        SearchAddonCatalog("https://addon.example/manifest.json", "Addon", "series", "flixpatrol.netflix.gb.x", "Top 10"),
        television,
    )

    @Test
    fun movingAndHidingKeepEveryRowExactlyOnce() {
        val moved = StreamingRowLayout().moved(StreamingRowId.NewMovies.ordinal, 0)
        assertEquals(StreamingRowId.NewMovies, moved.order.first())
        assertEquals(StreamingRowId.entries.toSet(), moved.order.toSet())
        assertEquals(StreamingRowId.entries.size, moved.order.size)
        assertEquals(StreamingRowLayout(), StreamingRowLayout().moved(0, 99)) // out of range: unchanged

        val hidden = moved.withHidden(StreamingRowId.PopularShows, true)
        assertFalse(StreamingRowId.PopularShows in hidden.visibleOrder)
        assertEquals(moved, hidden.withHidden(StreamingRowId.PopularShows, false))
        assertEquals(hidden, StreamingRowLayout.decode(hidden.encode()))
    }

    @Test
    fun savedLayoutsSurviveRowsBeingAddedOrRemovedLater() {
        assertEquals(StreamingRowLayout(), StreamingRowLayout.decode(null))
        assertEquals(StreamingRowLayout(), StreamingRowLayout.decode("not json"))
        // Saved before Originals and the slot existed, with one id this build no longer knows.
        val saved = """{"order":["NewShows","Retired","Top10Shows","PopularShows"],"hidden":["Retired","NewShows"]}"""
        val layout = StreamingRowLayout.decode(saved)
        assertEquals(StreamingRowId.entries.toSet(), layout.order.toSet())
        assertEquals(setOf(StreamingRowId.NewShows), layout.hidden)
        // The user's order is kept, and each missing row lands after its default predecessor.
        assertEquals(
            listOf(StreamingRowId.NewShows, StreamingRowId.NewMovies, StreamingRowId.Top10Shows, StreamingRowId.Top10Movies),
            layout.order.take(4),
        )
        assertTrue(layout.order.indexOf(StreamingRowId.ServiceCollections) < layout.order.indexOf(StreamingRowId.PopularShows))
    }

    @Test
    fun onlyServicesGetOriginalsAndFilmOriginalsNeedADedicatedStudio() {
        fun originals(name: String) = shelfKeysFor(tag(name)).filter { it.sort == SearchShelfSort.Originals }.map { it.television }
        assertEquals(listOf(true, false), originals("Netflix"))
        assertEquals(listOf(true, false), originals("Apple TV"))
        assertEquals(listOf(true), originals("Disney+"))
        assertEquals(emptyList(), originals("Comedy"))
        assertEquals(SEARCH_SHELF_KEYS, shelfKeysFor(tag("Comedy")))
        assertEquals(mapOf("with_networks" to "213"), tag("Netflix").originalsFilters(television = true))
        // Probe title: The Gorge (tmdb 950396) lists Apple Studios (company 194232).
        assertEquals(mapOf("with_companies" to "194232"), tag("Apple TV").originalsFilters(television = false))
        assertNull(tag("Hulu").originalsFilters(television = false))
        // Every service in the collection is also a provider tag in Search.
        streamingProviderSpecs.forEach { spec -> assertEquals(spec.name, searchProviderDiscovery(spec.name)?.name) }
        assertEquals("Apple TV", searchProviderDiscovery("Apple TV+")?.name)
        assertNull(searchProviderDiscovery("Trakt"))
        assertNull(searchProviderDiscovery("Pixar")) // a studio tag, not a service
    }

    @Test
    fun heroWaitsForTheChartsInsteadOfSwappingFromPopular() {
        // The Netflix page showed Popular's first title, then swapped to the chart's in one frame.
        val shelves = SearchDiscoveryShelvesUi(
            rails = mapOf(SearchShelfKey(SearchShelfSort.Popular, television = true) to listOf(title("popular"))),
        )
        assertTrue(tagHeroItems(SearchTopTenUi(loading = true), shelves, SearchScope.All).isEmpty())
        val charts = SearchTopTenUi(sections = listOf(chart(television = true) to section("top", title("hit"))))
        assertEquals(listOf("tmdb:hit"), tagHeroItems(charts, shelves, SearchScope.All).map { it.id })
        // Without charts, Popular still leads once they have finished loading.
        assertEquals(listOf("tmdb:popular"), tagHeroItems(SearchTopTenUi(), shelves, SearchScope.All).map { it.id })
    }

    @Test
    fun rowsFollowTheLayoutAndTheSlotExpandsOnlyWhereAServiceHasCollections() {
        val shelves = SearchDiscoveryShelvesUi(
            rails = mapOf(
                SearchShelfKey(SearchShelfSort.Originals, true) to listOf(title("original")),
                SearchShelfKey(SearchShelfSort.Popular, true) to listOf(title("popular-show")),
                SearchShelfKey(SearchShelfSort.Popular, false) to listOf(title("popular-movie", "movie")),
                SearchShelfKey(SearchShelfSort.TopRated, true) to emptyList(),
            ),
        )
        val topTen = SearchTopTenUi(sections = listOf(chart(television = null) to section("top", title("hit"))))
        val marvel = section("marvel", title("loki"), title("avengers", "movie"))
        val pixar = section("pixar", title("coco", "movie"))
        fun keys(layout: StreamingRowLayout, rows: List<HomeCatalogSection>, scope: SearchScope = SearchScope.All) =
            tagPageRows(layout, shelves, topTen, rows, scope).map { it.key }

        // Default order; the combined chart takes the shows chart's place; empty rails are skipped.
        assertEquals(
            listOf(
                "tag-top10:top", "tag-rail:Originals:true", "tag-collection:marvel", "tag-collection:pixar",
                "tag-rail:Popular:true", "tag-rail:Popular:false",
            ),
            keys(StreamingRowLayout(), listOf(marvel, pixar)),
        )
        // A service without collections: the slot takes no space and nothing else moves.
        assertEquals(
            listOf("tag-top10:top", "tag-rail:Originals:true", "tag-rail:Popular:true", "tag-rail:Popular:false"),
            keys(StreamingRowLayout(), emptyList()),
        )
        // Moving the slot to the end and hiding a rail applies to both.
        val custom = StreamingRowLayout()
            .moved(StreamingRowId.ServiceCollections.ordinal, StreamingRowId.entries.lastIndex)
            .withHidden(StreamingRowId.PopularShows, true)
        assertEquals(
            listOf("tag-top10:top", "tag-rail:Originals:true", "tag-rail:Popular:false", "tag-collection:marvel", "tag-collection:pixar"),
            keys(custom, listOf(marvel, pixar)),
        )
        // Tabs filter each row's titles; a collection with nothing for the tab disappears.
        assertEquals(
            listOf("tag-rail:Originals:true", "tag-collection:marvel", "tag-rail:Popular:true"),
            keys(StreamingRowLayout(), listOf(marvel, pixar), SearchScope.Shows),
        )
        val movieRows = tagPageRows(StreamingRowLayout(), shelves, topTen, listOf(marvel, pixar), SearchScope.Movies)
        assertEquals(listOf("tag-collection:marvel", "tag-collection:pixar", "tag-rail:Popular:false"), movieRows.map { it.key })
        assertEquals(listOf("avengers"), (movieRows.first() as TagPageRow.Collection).items.map { it.name })
    }

    @Test
    fun aServiceWithNothingInTheStreamingCountryFallsBackToTheUnitedKingdomOnce() = runTest {
        val day = LocalDate.parse("2026-11-22")
        val netflix = tag("Netflix")
        val calls = mutableListOf<Pair<SearchShelfKey, String?>>()
        suspend fun load(catalogRegions: Set<String>, region: String?, failing: SearchShelfSort? = null): TagShelvesLoad {
            calls.clear()
            return loadTagShelves(
                selection = netflix, region = region, day = day,
                loadRail = { key, _, railRegion, _ ->
                    calls += key to railRegion
                    when {
                        key.sort == failing -> error("TMDB down")
                        key.sort == SearchShelfSort.Originals -> TmdbEntityRailPageResult(listOf(title("original")), hasMore = false)
                        railRegion in catalogRegions -> TmdbEntityRailPageResult(listOf(title("${key.sort}-$railRegion")), hasMore = false)
                        else -> TmdbEntityRailPageResult(emptyList(), hasMore = false)
                    }
                },
                filterResults = { it },
            )
        }

        val fallback = load(catalogRegions = setOf("GB"), region = "LT")
        assertTrue(fallback.usedFallback)
        assertEquals("GB", fallback.region)
        assertEquals(listOf("Popular-GB"), fallback.rails[SearchShelfKey(SearchShelfSort.Popular, true)]?.map { it.name })
        // Originals aren't region-filtered: loaded once, kept, and never reloaded for the fallback.
        assertEquals(listOf("original"), fallback.rails[SearchShelfKey(SearchShelfSort.Originals, true)]?.map { it.name })
        assertEquals(2, calls.count { it.first.sort == SearchShelfSort.Originals })
        assertEquals(SEARCH_SHELF_KEYS.size, calls.count { it.second == "GB" })

        val local = load(catalogRegions = setOf("LT", "GB"), region = "LT")
        assertFalse(local.usedFallback)
        assertEquals("LT", local.region)
        assertTrue(calls.none { it.second == "GB" })

        // Empty everywhere: one fallback attempt, then the streaming country stays the page's region.
        val nowhere = load(catalogRegions = emptySet(), region = "LT")
        assertFalse(nowhere.usedFallback)
        assertEquals("LT", nowhere.region)
        assertEquals(SEARCH_SHELF_KEYS.size, calls.count { it.second == "GB" })

        // A failed rail means "unknown", not "empty": no fallback.
        val failed = load(catalogRegions = setOf("GB"), region = "LT", failing = SearchShelfSort.Popular)
        assertFalse(failed.usedFallback)
        assertFalse(failed.failed)
        assertTrue(calls.none { it.second == "GB" })

        // Already in the fallback region, and non-service tags, never reload.
        load(catalogRegions = emptySet(), region = "GB")
        assertEquals(shelfKeysFor(netflix).size, calls.size)
        calls.clear()
        val comedy = loadTagShelves(tag("Comedy"), null, day, { key, _, railRegion, _ ->
            calls += key to railRegion
            TmdbEntityRailPageResult(emptyList(), hasMore = false)
        }, { it })
        assertFalse(comedy.usedFallback)
        assertEquals(SEARCH_SHELF_KEYS.size, calls.size)
    }
}
