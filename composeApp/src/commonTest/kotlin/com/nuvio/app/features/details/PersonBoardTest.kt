package com.nuvio.app.features.details

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonBoardTest {
    private fun media(id: Int, name: String, type: String = "movie", date: String? = "2015-12-04") =
        MetaPreview(id = "tmdb:$id", type = type, name = name, rawReleaseDate = date)

    private fun person(
        movies: List<MetaPreview> = emptyList(),
        series: List<MetaPreview> = emptyList(),
        acting: List<ActingCredit> = emptyList(),
        portraits: List<String> = emptyList(),
        tagged: List<PersonTaggedImage> = emptyList(),
    ) = PersonDetail(
        tmdbId = 36801, name = "Adam Scott", biography = null, birthday = "1973-04-03", deathday = null,
        placeOfBirth = "Santa Cruz, California, USA", profilePhoto = null, knownFor = "Acting",
        movieCredits = movies, tvCredits = series, actingCredits = acting,
        careerCredits = if (acting.isEmpty()) emptyMap() else mapOf(CareerDepartment.Acting to acting),
        portraits = portraits, taggedImages = tagged,
    )

    @Test fun heightConvertsEveryWikidataUnitToCentimetres() {
        assertEquals(173, heightInCentimetres(1.73, "Q11573"))
        assertEquals(160, heightInCentimetres(160.0, "Q174728"))
        assertEquals(183, heightInCentimetres(6.0, "Q3710"))
        assertEquals(180, heightInCentimetres(71.0, "Q218593"))
        assertNull(heightInCentimetres(1.8, "Q99999"))
        // A height typed in the wrong unit (180 "metres") is a data error, not a giant.
        assertNull(heightInCentimetres(180.0, "Q11573"))
    }

    @Test fun heightShowsMetresOrFeetAndInchesForTheUnitedStates() {
        assertEquals("1.80 m", formatPersonHeight(180, "GB"))
        assertEquals("1.73 m", formatPersonHeight(173, null))
        assertEquals("5 ft 11 in", formatPersonHeight(180, "US"))
    }

    @Test fun wikidataParseReadsTheItemAndTheFirstUsableHeight() {
        val body = """{"results":{"bindings":[
            {"item":{"value":"http://www.wikidata.org/entity/Q23547"},"height":{"value":"+1.8"},"unit":{"value":"http://www.wikidata.org/entity/Q11573"}}
        ]}}"""
        assertEquals(PersonWikidata("Q23547", 180), PersonWikidataRepository.parse(body))
        val noHeight = """{"results":{"bindings":[{"item":{"value":"http://www.wikidata.org/entity/Q350719"}}]}}"""
        assertEquals(PersonWikidata("Q350719", null), PersonWikidataRepository.parse(noHeight))
        assertNull(PersonWikidataRepository.parse("""{"results":{"bindings":[]}}"""))
    }

    @Test fun tabsShowOnlyWhatThePersonHasAndAboutAlways() {
        val empty = person()
        assertEquals(listOf(PersonBoardTab.About), personBoardTabs(empty, knownFor = emptyList()))
        // While Known for loads, its tab holds its place so the row doesn't shift.
        assertEquals(listOf(PersonBoardTab.KnownFor, PersonBoardTab.About), personBoardTabs(empty, knownFor = null))
        val krampus = media(1, "Krampus")
        val full = person(
            movies = listOf(krampus),
            series = listOf(media(2, "Severance", "series", "2022-02-18")),
            acting = listOf(ActingCredit(krampus, listOf("Tom Engel"))),
            portraits = listOf("https://image.tmdb.org/t/p/original/a.jpg"),
        )
        assertEquals(PersonBoardTab.entries, personBoardTabs(full, knownFor = listOf(krampus)))
    }

    @Test fun cardLineIsYearAndRoleOrEpisodesForASeries() {
        val krampus = media(1, "Krampus")
        assertEquals("2015 · Tom Engel", personCardLine(krampus, ActingCredit(krampus, listOf("Tom Engel"))) { "$it episodes" })
        val severance = media(2, "Severance", "series", "2022-02-18")
        assertEquals(
            "19 episodes · Mark Scout",
            personCardLine(severance, ActingCredit(severance, listOf("Mark Scout"), episodeCount = 19)) { "$it episodes" },
        )
        assertEquals("2015", personCardLine(krampus, null) { "" })
    }

    @Test fun lastWatchedIsTheNewestMarkOnOneOfTheirTitles() {
        val severance = media(2, "Severance", "series", "2022-02-18")
        val krampus = media(1, "Krampus")
        val items = listOf(
            WatchedItem(id = "tmdb:1", type = "movie", name = "Krampus", markedAtEpochMs = 10),
            WatchedItem(id = "tt11280740", type = "series", name = "Severance", season = 2, episode = 3, markedAtEpochMs = 30),
            WatchedItem(id = "tmdb:99", type = "movie", name = "Not theirs", markedAtEpochMs = 50),
        )
        val (title, label) = personLastWatched(listOf(krampus, severance), items)!!
        assertEquals("Severance", title.name)
        assertEquals("Severance S2 E3", label)
        assertNull(personLastWatched(listOf(krampus), emptyList()))
    }

    @Test fun comingSoonIsTheNearestReleaseWithinSixMonths() {
        val today = LocalDate(2026, 11, 26)
        val soon = person(movies = listOf(media(1, "The Whisper Man", date = "2027-03-01"), media(2, "A Sprinkle of History", date = "2027-09-01"), media(3, "Krampus")))
        assertEquals("The Whisper Man", personComingSoon(soon, today)?.name)
        // A release more than six months away is not "coming soon": the tile gives way to Start with.
        assertNull(personComingSoon(person(movies = listOf(media(2, "The Saviors", date = "2027-09-01"))), today))
        assertNull(personComingSoon(person(movies = listOf(media(3, "Krampus"))), today))
    }

    @Test fun startWithPrefersKnownForAndSkipsStartedTitlesAndGuestSpots() {
        val today = LocalDate(2026, 11, 26)
        val severance = media(1, "Severance", "series", "2022-02-18").copy(popularity = 90.0)
        val lawAndOrder = media(5, "Law & Order", "series", "1990-09-13").copy(popularity = 400.0)
        val krampus = media(2, "Krampus").copy(popularity = 40.0)
        val mitty = media(3, "The Secret Life of Walter Mitty", date = "2013-12-25").copy(popularity = 60.0)
        val upcoming = media(4, "The Saviors", date = "2027-09-01").copy(popularity = 99.0)
        val titles = listOf(severance, lawAndOrder, krampus, mitty, upcoming)
        val credits = listOf(
            ActingCredit(severance, listOf("Mark Scout"), episodeCount = 19),
            ActingCredit(lawAndOrder, listOf("Robbie Howell"), episodeCount = 1),
        )
        // Known for leads; a started series is skipped.
        assertEquals("Krampus", personStartWith(listOf(severance, krampus), titles, credits, today) { it.name == "Severance" }?.name)
        // Without Known for: by popularity, but a one-episode guest spot never wins.
        assertEquals("Severance", personStartWith(null, titles, credits, today) { false }?.name)
        assertEquals("The Secret Life of Walter Mitty", personStartWith(null, titles, credits, today) { it.name == "Severance" }?.name)
        assertNull(personStartWith(null, titles, credits, today) { true })
    }

    @Test fun backdropSkipsGuestSpotsAndSelfAppearances() {
        val euphoria = media(1, "Euphoria", "series", "2019-06-16").copy(popularity = 120.0, banner = "euphoria.jpg")
        val familyGuy = media(2, "Family Guy", "series", "1999-01-31").copy(popularity = 800.0, banner = "family-guy.jpg")
        val kimmel = media(3, "Jimmy Kimmel Live!", "series", "2003-01-26").copy(popularity = 900.0, banner = "kimmel.jpg")
        val sweeney = person(
            series = listOf(euphoria, familyGuy, kimmel),
            acting = listOf(
                ActingCredit(euphoria, listOf("Cassie Howard"), episodeCount = 18),
                ActingCredit(familyGuy, listOf("Sydney Sweeney (voice)"), episodeCount = 1),
                ActingCredit(kimmel, listOf("Self - Guest"), episodeCount = 4),
            ),
        )
        assertEquals(setOf(familyGuy.stableKey(), kimmel.stableKey()), personGuestSeriesKeys(sweeney.actingCredits))
        assertEquals("euphoria.jpg", personBackdropArt(sweeney))
        // With only guest spots there is still a backdrop.
        assertEquals("family-guy.jpg", personBackdropArt(person(series = listOf(familyGuy), acting = listOf(ActingCredit(familyGuy, listOf("Herself"), episodeCount = 1)))))
    }

    @Test fun aTitleCountsAsStartedFromAnyWatchedEpisode() {
        val severance = media(1, "Severance", "series", "2022-02-18")
        val items = listOf(WatchedItem(id = "tt11280740", type = "series", name = "Severance", season = 1, episode = 1, markedAtEpochMs = 1))
        kotlin.test.assertTrue(personTitleStarted(severance, items))
        kotlin.test.assertFalse(personTitleStarted(media(2, "Krampus"), items))
    }
}
