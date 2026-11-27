package com.nuvio.app.features.search

import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A service row's grid menus: Sort, Genre, Year, Rating and Watch status over the loaded titles. */
class StreamingGridFiltersTest {
    private val drama = MetaPreview("tmdb:1", "series", "Beta", rawReleaseDate = "2021-03-01", popularity = 50.0, sortRating = 8.4, tmdbGenreIds = listOf(18))
    private val comedy = MetaPreview("tmdb:2", "series", "Alpha", rawReleaseDate = "2015-06-01", popularity = 90.0, sortRating = 6.9, tmdbGenreIds = listOf(35))
    private val crimeDrama = MetaPreview("tmdb:3", "series", "Gamma", rawReleaseDate = "2024-01-01", popularity = 10.0, sortRating = 7.5, tmdbGenreIds = listOf(80, 18))
    private val items = listOf(drama, comedy, crimeDrama)
    private val base = StreamingGridFilters(sort = GridSort.Popular, defaultSort = GridSort.Popular)
    private val unwatched: (MetaPreview) -> GridWatch = { GridWatch.Unwatched }

    @Test fun defaultsKeepEveryTitleInTheRowsOrder() {
        assertEquals(listOf(comedy, drama, crimeDrama), base.apply(items, unwatched))
        assertFalse(base.isActive)
    }

    @Test fun genreYearAndRatingNarrowTheGrid() {
        assertEquals(listOf(drama, crimeDrama), base.copy(genres = setOf(18)).apply(items, unwatched))
        assertEquals(listOf(drama, crimeDrama), base.copy(decades = setOf(2020)).apply(items, unwatched))
        assertEquals(listOf(drama, crimeDrama), base.copy(minRating = 7).apply(items, unwatched))
        assertTrue(base.copy(minRating = 7).narrows)
    }

    @Test fun sortAloneIsActiveButKeepsEveryTitle() {
        val byTitle = base.copy(sort = GridSort.Title)
        assertEquals(listOf(comedy, drama, crimeDrama), byTitle.apply(items, unwatched))
        assertTrue(byTitle.isActive)
        assertFalse(byTitle.narrows)
        assertEquals(base, byTitle.cleared())
    }

    @Test fun watchStatusUsesTheGivenState() {
        val state = mapOf(drama to GridWatch.Watched, comedy to GridWatch.InProgress, crimeDrama to GridWatch.Unwatched)
        assertEquals(listOf(comedy), base.copy(watch = setOf(GridWatch.InProgress)).apply(items) { state.getValue(it) })
        assertEquals(listOf(comedy, crimeDrama), base.copy(watch = setOf(GridWatch.InProgress, GridWatch.Unwatched)).apply(items) { state.getValue(it) })
    }

    @Test fun menusOfferOnlyWhatTheLoadedTitlesHave() {
        assertEquals(listOf("Comedy", "Crime", "Drama"), gridGenreOptions(items).map { it.second })
        assertEquals(listOf(2020, 2010), gridDecadeOptions(items))
        assertEquals(GridSort.Newest, GridSort.forShelf(SearchShelfSort.New))
    }
}
