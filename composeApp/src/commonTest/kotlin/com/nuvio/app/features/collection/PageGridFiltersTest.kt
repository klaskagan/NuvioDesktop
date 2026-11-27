package com.nuvio.app.features.collection

import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageGridFiltersTest {
    private val dune = MetaPreview("dune", "movie", "Dune", releaseInfo = "2021", imdbRating = "8.0", genres = listOf("Sci-Fi"))
    private val arrival = MetaPreview("arrival", "movie", "Arrival", releaseInfo = "2016", imdbRating = "7.9", genres = listOf("Sci-Fi", "Drama"))
    private val silo = MetaPreview("silo", "series", "Silo", releaseInfo = "2023–", imdbRating = "8.1", genres = listOf("Drama"))
    private val items = listOf(dune, arrival, silo)

    @Test
    fun `catalog order is kept until a sort is picked`() {
        assertEquals(items, applyPageGridFilters(items, PageGridFilters()) { false })
        assertEquals(listOf(arrival, dune, silo), applyPageGridFilters(items, PageGridFilters(sort = PageGridSort.AToZ)) { false })
        assertEquals(listOf(silo, dune, arrival), applyPageGridFilters(items, PageGridFilters(sort = PageGridSort.Rating)) { false })
    }

    @Test
    fun `menus narrow by genre, decade, rating, type and watch status`() {
        assertEquals(listOf(arrival, silo), applyPageGridFilters(items, PageGridFilters(genre = "drama")) { false })
        assertEquals(listOf(dune, silo), applyPageGridFilters(items, PageGridFilters(decade = 2020)) { false })
        assertEquals(listOf(dune, silo), applyPageGridFilters(items, PageGridFilters(minRating = 8)) { false })
        assertEquals(listOf(silo), applyPageGridFilters(items, PageGridFilters(type = PageGridType.Shows)) { false })
        assertEquals(listOf(arrival, silo), applyPageGridFilters(items, PageGridFilters(watchStatus = PageGridWatchStatus.Unwatched)) { it == dune })
    }

    @Test
    fun `facets only offer what the items can answer`() {
        val facets = pageGridFacets(items)
        assertEquals(listOf("Drama", "Sci-Fi"), facets.genres)
        assertEquals(listOf(2020, 2010), facets.decades)
        assertTrue(facets.hasRatings)
        assertTrue(facets.mixesTypes)
        val bare = pageGridFacets(listOf(MetaPreview("x", "movie", "X")))
        assertTrue(bare.genres.isEmpty() && bare.decades.isEmpty())
        assertFalse(bare.hasRatings || bare.mixesTypes)
    }

    @Test
    fun `folder hero prefers titles with a backdrop`() {
        val withBackdrop = dune.copy(banner = "https://img/dune.jpg")
        assertEquals(listOf(withBackdrop), folderHeroItems(listOf(arrival, withBackdrop, silo)))
        assertEquals(listOf(arrival, silo), folderHeroItems(listOf(arrival, silo)))
    }
}
