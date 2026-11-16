package com.nuvio.app.features.details

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tmdb.TmdbPersonCombinedCreditsResponse
import com.nuvio.app.features.tmdb.TmdbPersonSearchResponse
import com.nuvio.app.features.tmdb.mapPersonFilmography
import com.nuvio.app.features.tmdb.mapPersonKnownFor
import com.nuvio.app.features.watched.watchedItemKeys
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.*

class PersonCreditsTest {
    private val today = LocalDate(2026, 11, 16)
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun knownForUsesProviderOrderAndOnlyTheRequestedPerson() {
        val response = json.decodeFromString<TmdbPersonSearchResponse>(
            """{"results":[
                {"id":17,"known_for":[{"id":7,"media_type":"movie","title":"Wrong person"}]},
                {"id":62,"known_for":[{"id":7,"media_type":"tv","name":"Series first"},
                    {"id":7,"media_type":"movie","title":"Movie second"},
                    {"id":7,"media_type":"movie","title":"Duplicate"}]}
            ]}""",
        )
        val results = assertNotNull(mapPersonKnownFor(response, 62, "en-US"))
        assertEquals(listOf("Series first", "Movie second"), results.map { it.name })
        assertEquals(listOf("series", "movie"), results.map { it.type })
        assertNull(mapPersonKnownFor(response, 61, "en-US"))
    }

    @Test fun websiteKnownForReadsAllEightInOrderAndIgnoresOtherSections() {
        val ids = listOf(745, 562, 18, 9741, 63, 187, 95, 680)
        val links = ids.joinToString("") { id ->
            """<li><a href="/movie/$id-title?language=en-US"></a><a href="/movie/$id-title">Title</a></li>"""
        }
        val html = """
            <a href="/movie/1-outside"></a>
            <div id="known_for_scroller" class="scroller_wrap">
                <ul class="horizontal_media_list scroller">$links</ul>
            </div>
            <ul><li><a href="/tv/2-outside"></a></li></ul>
        """
        assertEquals(ids.map { "movie" to it }, com.nuvio.app.features.tmdb.websiteKnownForKeys(html))
        assertEquals(emptyList(), com.nuvio.app.features.tmdb.websiteKnownForKeys("<a href='/movie/1-outside'></a>"))
    }

    @Test fun timelineUsesExplicitTypeSignalsAndDoesNotSumOverlappingRoleCounts() {
        val response = json.decodeFromString<TmdbPersonCombinedCreditsResponse>(
            """{"cast":[
                {"id":1,"media_type":"movie","title":"Journey to Safety","genre_ids":[],"video":false},
                {"id":2,"media_type":"movie","title":"Behind the Scenes","genre_ids":[],"video":true},
                {"id":3,"media_type":"movie","title":"Inferno","genre_ids":[99,10770],"video":true},
                {"id":4,"media_type":"tv","name":"The Emmy Awards","genre_ids":[],"episode_count":1},
                {"id":5,"media_type":"tv","name":"Late Show","credit_id":"a","episode_count":20,"character":"Guest"},
                {"id":5,"media_type":"tv","name":"Late Show","credit_id":"a","episode_count":20,"character":"Guest"},
                {"id":5,"media_type":"tv","name":"Late Show","credit_id":"b","episode_count":5,"character":"Self"},
                {"id":6,"media_type":"tv","name":"No episode data","episode_count":0},
                {"id":7,"media_type":"tv","name":"Repeated record","credit_id":"c","episode_count":2},
                {"id":7,"media_type":"tv","name":"Repeated record","credit_id":"c","episode_count":2}
            ]}""",
        )
        val byTitle = mapPersonFilmography(response, "en", emptyMap(), preferCrew = false)
            .actingCredits.associateBy { it.media.name }
        assertEquals(ActingCreditKind.Movie, byTitle.getValue("Journey to Safety").kind)
        assertEquals(ActingCreditKind.Video, byTitle.getValue("Behind the Scenes").kind)
        assertEquals(ActingCreditKind.TvMovie, byTitle.getValue("Inferno").kind)
        assertEquals(ActingCreditKind.TvSeries, byTitle.getValue("The Emmy Awards").kind)
        assertEquals(1, byTitle.getValue("The Emmy Awards").episodeCount)
        assertFalse(byTitle.getValue("The Emmy Awards").episodeCountIsMinimum)
        assertEquals(20, byTitle.getValue("Late Show").episodeCount)
        assertTrue(byTitle.getValue("Late Show").episodeCountIsMinimum)
        assertEquals(listOf("Guest", "Self"), byTitle.getValue("Late Show").characters)
        assertNull(byTitle.getValue("No episode data").episodeCount)
        assertEquals(2, byTitle.getValue("Repeated record").episodeCount)
        assertFalse(byTitle.getValue("Repeated record").episodeCountIsMinimum)
    }

    @Test fun crewPreferenceRetainsSeparateActingRolesAndArtworklessTitles() {
        val response = json.decodeFromString<TmdbPersonCombinedCreditsResponse>(
            """{"cast":[
                {"id":7,"media_type":"movie","title":"Acted","character":"Alice","genre_ids":[18],"vote_average":7,"vote_count":6900},
                {"id":7,"media_type":"movie","title":"Acted","character":"Bob"},
                {"id":7,"media_type":"movie","title":"Acted","character":" Alice "},
                {"id":7,"media_type":"tv","name":"Series","genre_ids":[35]}
            ],"crew":[{"id":8,"media_type":"movie","title":"Directed"}]}""",
        )
        val crew = mapPersonFilmography(response, "en-US", emptyMap(), preferCrew = true)
        val cast = mapPersonFilmography(response, "en-US", emptyMap(), preferCrew = false)
        assertEquals("Directed", crew.movies.single().name)
        assertEquals("Series", crew.series.single().name)
        assertEquals("Acted", cast.movies.single().name)
        assertNull(cast.movies.single().poster)
        assertEquals(7.0, cast.movies.single().sortRating)
        assertEquals(6900, cast.actingCredits.first { it.media.type == "movie" }.media.voteCount)
        assertEquals(listOf("Alice", "Bob"), crew.actingCredits.first { it.media.type == "movie" }.characters)
        assertEquals(setOf(18), crew.genreIds["movie:tmdb:7"])
        assertEquals("Series", crew.actingCredits.first { it.media.type == "series" }.media.name)
        assertEquals(setOf(35), crew.genreIds["series:tmdb:7"])
    }

    @Test fun latestAndGenreCanCombineWithFullyWatchedSeries() {
        val before = item("Older", "2026-11-15", type = "series")
        val now = item("Today", "2026-11-16", type = "series")
        val after = item("Future", "2026-11-17", type = "series")
        val undated = item("Undated", null, type = "series")
        val all = listOf(before, after, undated, now)
        val latest = PersonCreditQuery().withSort(PersonCreditSort.Latest)
        assertEquals(PersonReleaseFilter.Released, latest.release)
        assertEquals(listOf(now, before), filterPersonCredits(all, latest, today))
        assertEquals(PersonCreditSort.NewestYear, latest.withRelease(PersonReleaseFilter.Upcoming).sort)
        assertEquals(listOf(after), filterPersonCredits(all, latest.withRelease(PersonReleaseFilter.Upcoming), today))

        val query = latest.copy(watched = PersonWatchedFilter.Watched, genreId = 18)
        val watched = watchedItemKeys(type = now.type, id = now.id).toSet()
        assertEquals(listOf(now), filterPersonCredits(
            all, query, today, mapOf(now.stableKey() to setOf(18)), fullyWatchedSeriesKeys = watched,
        ))
        assertEquals(emptyList(), filterPersonCredits(
            all, query.copy(genreId = 35), today, mapOf(now.stableKey() to setOf(18)), fullyWatchedSeriesKeys = watched,
        ))
    }

    @Test fun yearAndRatingSortsKeepMissingValuesLast() {
        val earlier = item("Zebra", "2020-01-01", 3.0)
        val later = item("Aardvark", "2022-01-01", 8.0)
        val unknown = item("Unknown", "invalid")
        val all = listOf(later, unknown, earlier)
        fun order(sort: PersonCreditSort) = filterPersonCredits(all, PersonCreditQuery(sort), today)
        assertEquals(listOf(earlier, later, unknown), order(PersonCreditSort.LowestRated))
        assertEquals(listOf(later, earlier, unknown), order(PersonCreditSort.HighestRated))
        assertEquals(listOf(later, earlier, unknown), order(PersonCreditSort.NewestYear))
        assertEquals(listOf(earlier, later, unknown), order(PersonCreditSort.OldestYear))
        assertEquals(listOf(later, earlier, unknown), order(PersonCreditSort.Popularity))
    }

    @Test fun timelineGroupsUpcomingAndUnknownDatesAroundReleasedYears() {
        val movies = listOf(
            item("Older", "2020-01-01"),
            item("Unknown", null),
            item("Future", "2026-11-17"),
            item("Today", "2026-11-16"),
        ).map { ActingCredit(it, emptyList()) }
        assertEquals(
            listOf(ActingPeriod.Upcoming, ActingPeriod.Year(2026), ActingPeriod.Year(2020), ActingPeriod.Unknown),
            personActingTimeline(movies, today).keys.toList(),
        )
    }

    private fun item(name: String, date: String?, rating: Double? = null, type: String = "movie") =
        MetaPreview(id = name, name = name, type = type, rawReleaseDate = date, sortRating = rating, popularity = rating)
}
