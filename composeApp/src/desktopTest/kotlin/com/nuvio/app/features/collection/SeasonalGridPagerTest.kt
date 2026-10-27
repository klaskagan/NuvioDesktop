package com.nuvio.app.features.collection

import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SeasonalGridPagerTest {
    private fun source(title: String) = CollectionSource(provider = "tmdb", title = title, tmdbSourceType = "DISCOVER", mediaType = "movie")
    private fun movie(id: String, rating: Double) = MetaPreview(id, "movie", id, imdbRating = rating.toString(), sortRating = rating)

    @Test fun ratingSortIsSentToServerAndMergedAcrossPageBoundaries() = runBlocking {
        val requests = mutableListOf<Pair<String, Int>>()
        val pager = SeasonalGridPager(listOf(source("Slashers"), source("Haunted Houses")),
            SeasonalGridQuery(sort = SeasonalSort.HIGHEST_RATED), load = { source, page ->
                assertEquals("vote_average.desc", source.sortBy)
                requests += source.title!! to page
                val ratings = if (source.title == "Slashers") listOf(9.9, 9.7, 9.5) else listOf(9.8, 9.6, 9.4)
                CatalogPage(listOf(movie("${source.title}-$page", ratings[page - 1])), 1, if (page < 3) page + 1 else null)
            })
        assertEquals(listOf(9.9, 9.8), pager.nextPage(2).map { it.sortRating })
        assertEquals(listOf(9.7, 9.6), pager.nextPage(2).map { it.sortRating })
        assertEquals(listOf(9.5, 9.4), pager.nextPage(2).map { it.sortRating })
        assertTrue(requests.contains("Slashers" to 3))
        assertFalse(pager.hasMore)
    }

    @Test fun selectedCatalogSortDoesNotFetchOtherCatalogs() = runBlocking {
        val requests = mutableListOf<String>()
        val pager = SeasonalGridPager(listOf(source("Slashers")), SeasonalGridQuery(sort = SeasonalSort.LOWEST_RATED),
            load = { source, page ->
                requests += source.title!!
                assertEquals("vote_average.asc", source.sortBy)
                CatalogPage(listOf(movie("$page", page.toDouble())), 1, if (page == 1) 2 else null)
            })
        assertEquals(listOf(1.0, 2.0), pager.nextPage().map { it.sortRating })
        assertEquals(setOf("Slashers"), requests.toSet())
    }

    @Test fun watchedFilterContinuesBeyondEmptyFirstPageAndKeepsSort() = runBlocking {
        val pager = SeasonalGridPager(listOf(source("Slashers")), SeasonalGridQuery(watched = true, sort = SeasonalSort.HIGHEST_RATED),
            load = { _, page -> CatalogPage(listOf(movie("$page", 10.0 - page)), 1, if (page < 3) page + 1 else null) },
            watched = { it.id == "3" })
        assertEquals(listOf("3"), pager.nextPage().map { it.id })
        assertFalse(pager.hasMore)
    }

    @Test fun failedPageCanRetryWithoutLosingConsumedResults() = runBlocking {
        var failed = false
        val pager = SeasonalGridPager(listOf(source("Slashers")), SeasonalGridQuery(sort = SeasonalSort.HIGHEST_RATED),
            load = { _, page ->
                if (page == 2 && !failed) { failed = true; error("Transient failure") }
                CatalogPage(listOf(movie("$page", 10.0 - page)), 1, if (page < 3) page + 1 else null)
            })
        assertFailsWith<IllegalStateException> { pager.nextPage() }
        assertEquals(listOf("1", "2", "3"), pager.nextPage().map { it.id })
    }

    @Test fun duplicateMediaIsRemovedAcrossSourcesButSeriesWithSameIdIsKept() = runBlocking {
        val pager = SeasonalGridPager(listOf(source("A"), source("B")), SeasonalGridQuery(), load = { source, _ ->
            CatalogPage(listOf(movie("shared", 8.0), MetaPreview("shared", "series", "Show")), 2, null)
        })
        assertEquals(2, pager.nextPage().size)
    }

    @Test fun unsupportedAddonDoesNotSilentlySortLoadedPage() = runBlocking {
        var loaded = false
        val pager = SeasonalGridPager(listOf(CollectionSource(addonId = "addon", type = "movie", catalogId = "slashers")),
            SeasonalGridQuery(sort = SeasonalSort.HIGHEST_RATED), load = { _, _ -> loaded = true; CatalogPage(emptyList(), 0, null) })
        assertFailsWith<IllegalStateException> { pager.nextPage() }
        assertFalse(loaded)
    }

    @Test fun requestFiltersPreserveSourceConstraints() {
        val source = source("Slashers").copy(filters = TmdbCollectionFilters(withGenres = "27", voteAverageGte = 7.0,
            voteCountGte = 100, releaseDateLte = "2000-12-31", withKeywords = "123"))
        val requested = SeasonalGridPager.requestSource(source, SeasonalGridQuery(genreId = 35, year = 1990,
            minimumRating = 6.0, sort = SeasonalSort.HIGHEST_RATED))
        assertEquals("27,35", requested.filters?.withGenres)
        assertEquals(7.0, requested.filters?.voteAverageGte)
        assertEquals("123", requested.filters?.withKeywords)
        val query = TmdbCollectionSourceResolver.buildDiscoverQuery(requested, TmdbCollectionSourceType.DISCOVER,
            TmdbCollectionMediaType.MOVIE, "en-US", 2, requested.filters!!)
        assertEquals("vote_average.desc", query["sort_by"])
        assertEquals("2", query["page"])
        assertEquals("1990", query["year"])
        assertEquals("2000-12-31", query["primary_release_date.lte"])
    }

    @Test fun bothMediaTypesAreIndependentSortedServerStreams() = runBlocking {
        val types = mutableSetOf<String?>()
        val pager = SeasonalGridPager(listOf(source("Slashers").copy(mediaType = "BOTH")), SeasonalGridQuery(sort = SeasonalSort.HIGHEST_RATED),
            load = { source, _ -> types += source.mediaType
                CatalogPage(listOf(MetaPreview(source.mediaType!!, if (source.mediaType == "tv") "series" else "movie", "Fixture", sortRating = 8.0)), 1, null) })
        assertEquals(2, pager.nextPage().size)
        assertEquals(setOf<String?>("movie", "tv"), types)
    }
}
