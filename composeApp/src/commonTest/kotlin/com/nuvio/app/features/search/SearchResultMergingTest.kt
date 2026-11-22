package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.details.ActingCredit
import com.nuvio.app.features.details.PersonDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchResultMergingTest {
    @Test
    fun `same imdb id from two addons appears once with richer entry`() {
        val bare = meta(id = "tt0137523", name = "Fight Club", releaseInfo = "1999")
        val rich = meta(id = "tt0137523", name = "Fight Club", releaseInfo = "1999", poster = "https://img/fc.jpg")

        val merged = mergeSearchResults(listOf(section("a", bare), section("b", rich)), "fight club")

        assertEquals(1, merged.size)
        assertEquals("https://img/fc.jpg", merged.single().poster)
    }

    @Test
    fun `tmdb canonical key and title year fallback collapse duplicates`() {
        val tmdb = meta(id = "tmdb:550", name = "Fight Club", releaseInfo = "1999", canonicalMediaKey = "movie:tmdb:550")
        val imdbWithCanonical = meta(id = "tt0137523", name = "Fight Club", releaseInfo = "1999", canonicalMediaKey = "movie:tmdb:550")
        val otherProvider = meta(id = "kitsu:1", name = "Fight Club", releaseInfo = "1999-10-15")

        val merged = mergeSearchResults(
            listOf(section("a", tmdb), section("b", imdbWithCanonical), section("c", otherProvider)),
            "fight club",
        )

        assertEquals(1, merged.size)
    }

    @Test
    fun `movie and series with same name stay separate`() {
        val movie = meta(id = "tt1", name = "Fargo", type = "movie", releaseInfo = "1996")
        val series = meta(id = "tt2", name = "Fargo", type = "series", releaseInfo = "1996")

        val merged = mergeSearchResults(listOf(section("a", movie, series)), "fargo")

        assertEquals(2, merged.size)
    }

    @Test
    fun `exact title ranks above more popular partial match and popularity breaks ties`() {
        val popularPartial = meta(id = "tt1", name = "Dune: Part Two", popularity = 900.0)
        val exact = meta(id = "tt2", name = "Dune", popularity = 10.0)
        val lessPopularPartial = meta(id = "tt3", name = "Dune: Prophecy", popularity = 100.0)

        // Popularity overrides positions within one provider when title relevance ties.
        val merged = mergeSearchResults(
            listOf(section("a", lessPopularPartial, exact, popularPartial)),
            "dune",
        )

        assertEquals(listOf("tt2", "tt1", "tt3"), merged.map { it.id })
    }

    @Test
    fun `popular competing exact title leads even when provider ranks it second or returns it late`() {
        val obscure = meta(id = "tt1", name = "Stranger Things", popularity = 0.3)
        val popular = meta(id = "tt2", name = "Stranger Things", type = "series", popularity = 500.0)
        val weaker = meta(id = "tt3", name = "Stranger Things: Behind the Scenes", popularity = 1000.0)
        val ranked = mergeSearchResults(listOf(section("a", obscure, popular, weaker)), "stranger things")
        assertEquals(listOf(popular, obscure, weaker), ranked)
        assertEquals(ranked, publishLateSearchResults("stranger things", listOf(obscure, weaker), ranked))
    }

    @Test
    fun `provider order breaks ties without popularity`() {
        val first = meta(id = "tt1", name = "Alien Romulus")
        val second = meta(id = "tt2", name = "Alien Covenant")

        val merged = mergeSearchResults(listOf(section("a", first), section("b", second)), "alien")

        assertEquals(listOf("tt1", "tt2"), merged.map { it.id })
    }

    @Test
    fun `titles sharing no query word are dropped`() {
        val unrelatedAnime = meta(id = "kitsu:42", name = "Shingeki no Kyojin", type = "anime")
        val related = meta(id = "tt3", name = "Brad Pitt: Hollywood Rebel")

        val merged = mergeSearchResults(listOf(section("a", unrelatedAnime, related)), "Brad Pitt")

        assertEquals(listOf("tt3"), merged.map { it.id })
    }

    @Test
    fun `partial word matches drop once a title matches every word but stay for typos`() {
        val starWars = meta(id = "tt0076759", name = "Star Wars", releaseInfo = "1977")
        val narutaru = meta(id = "mal:838", name = "Shadow Star Narutaru", type = "anime")

        val merged = mergeSearchResults(listOf(section("a", starWars, narutaru)), "star wars")
        val typo = mergeSearchResults(listOf(section("a", narutaru)), "star wras")

        assertEquals(listOf("tt0076759"), merged.map { it.id })
        assertEquals(listOf("mal:838"), typo.map { it.id })
    }

    @Test
    fun `late provider results append without moving shown cards`() {
        val shown = listOf(meta(id = "tt1", name = "Maleficent"), meta(id = "tt2", name = "Salt"))
        val final = listOf(
            meta(id = "tt3", name = "Wanted"), // ranks first now, but was not on screen
            meta(id = "tmdb:9", name = "Salt", canonicalMediaKey = "movie:tmdb:9").copy(alternateIds = setOf("tt2")),
            meta(id = "tt1", name = "Maleficent"),
        )

        assertEquals(listOf("tt1", "tt2", "tt3"), appendNewSearchResults(shown, final).map { it.id })
    }

    @Test
    fun `relevance tiers use accent and punctuation insensitive matching`() {
        assertEquals(SearchRelevanceTier.Exact, searchRelevanceTier("Amélie", "amelie"))
        assertEquals(SearchRelevanceTier.Exact, searchRelevanceTier("Ocean's Eleven", "oceans eleven"))
        assertEquals(SearchRelevanceTier.Prefix, searchRelevanceTier("Matrix Reloaded", "matrix"))
        assertEquals(SearchRelevanceTier.Exact, searchRelevanceTier("The Matrix", "matrix"))
        assertEquals(SearchRelevanceTier.WordBoundary, searchRelevanceTier("Enter the Matrix", "matrix"))
        assertEquals(SearchRelevanceTier.Contains, searchRelevanceTier("Spiderman", "derma"))
        assertEquals(SearchRelevanceTier.AnyWord, searchRelevanceTier("Moneyball Pitt", "brad pitt"))
        assertEquals(SearchRelevanceTier.Other, searchRelevanceTier("Naruto", "brad pitt"))
    }

    @Test
    fun `partial provider failure still shows results without error`() {
        val state = resolveSearchUiState(
            query = "dune",
            sections = listOf(section("a", meta(id = "tt2", name = "Dune"))),
            people = emptyList(),
            completedProviders = 2,
            failedProviders = 1,
            firstFailure = "Timed out",
            isFinished = true,
            hasPendingAddonManifests = false,
        )

        assertFalse(state.isLoading)
        assertEquals(1, state.results.size)
        assertNull(state.emptyStateReason)
        assertNull(state.errorMessage)
    }

    @Test
    fun `all providers failing shows request failed state`() {
        val state = resolveSearchUiState(
            query = "dune",
            sections = emptyList(),
            people = emptyList(),
            completedProviders = 2,
            failedProviders = 2,
            firstFailure = "Timed out",
            isFinished = true,
            hasPendingAddonManifests = false,
        )

        assertEquals(SearchEmptyStateReason.RequestFailed, state.emptyStateReason)
        assertEquals("Timed out", state.errorMessage)
    }

    @Test
    fun `filtered out results without people show empty state`() {
        val state = resolveSearchUiState(
            query = "brad pitt",
            sections = listOf(section("a", meta(id = "kitsu:1", name = "Naruto"))),
            people = emptyList(),
            completedProviders = 1,
            failedProviders = 0,
            firstFailure = null,
            isFinished = true,
            hasPendingAddonManifests = false,
        )

        assertTrue(state.results.isEmpty())
        assertEquals(SearchEmptyStateReason.NoResults, state.emptyStateReason)
    }

    @Test
    fun `people only results do not show empty state`() {
        val state = resolveSearchUiState(
            query = "brad pitt",
            sections = emptyList(),
            people = listOf(SearchPerson(tmdbId = 287, name = "Brad Pitt", department = "Acting")),
            completedProviders = 1,
            failedProviders = 1,
            firstFailure = "No results",
            isFinished = true,
            hasPendingAddonManifests = false,
        )

        assertTrue(state.hasContent)
        assertNull(state.emptyStateReason)
        assertNull(state.errorMessage)
    }

    @Test
    fun `person match drops titles sharing only some query words`() {
        val documentary = meta(id = "tt13988290", name = "Leonardo DiCaprio: Most Wanted!")
        val partialAnime = meta(id = "mal:41158", name = "Leonardo Hakase to Kirin-mura no Nakama-tachi", type = "series")
        val sections = listOf(section("a", documentary, partialAnime))
        val person = SearchPerson(tmdbId = 6193, name = "Leonardo DiCaprio", department = "Acting")

        fun resolvedIds(people: List<SearchPerson>) = resolveSearchUiState(
            query = "leonardo dicaprio",
            sections = sections,
            people = people,
            completedProviders = 1,
            failedProviders = 0,
            firstFailure = null,
            isFinished = true,
            hasPendingAddonManifests = false,
        ).results.map { it.id }

        assertEquals(listOf("tt13988290"), resolvedIds(listOf(person)))
        // A title matching every word also drops partial matches without a person.
        assertEquals(listOf("tt13988290"), resolvedIds(emptyList()))
    }

    @Test
    fun `leading article title is an exact match`() {
        assertEquals(SearchRelevanceTier.Exact, searchRelevanceTier("The Simpsons", "simpsons"))
        assertEquals(SearchRelevanceTier.Prefix, searchRelevanceTier("The Simpsons Movie", "simpsons"))
        assertEquals(SearchRelevanceTier.Exact, searchRelevanceTier("Simpsons", "the simpsons"))
    }

    @Test
    fun `series without popularity is not buried under movies that have it`() {
        // Shapes from real addon responses: movie search returns popularity, series search does not.
        val movies = section(
            "movies",
            meta(id = "tt0462538", name = "The Simpsons Movie", popularity = 8.6),
            meta(id = "tt15829632", name = "The Simpsons in Plusaversary", popularity = 2.4),
            meta(id = "tt34791030", name = "The Simpsons: O C'mon All Ye Faithful", popularity = 1.8),
        )
        val series = section(
            "series",
            meta(id = "tt0096697", name = "The Simpsons", type = "series"),
            meta(id = "tt38821667", name = "Fortnite x The Simpsons", type = "series"),
        )

        val merged = mergeSearchResults(listOf(movies, series), "simpsons")

        assertEquals("tt0096697", merged.first().id)
        // Each provider's top result ranks ahead of the other provider's later results.
        assertTrue(merged.indexOfFirst { it.id == "tt0462538" } < merged.indexOfFirst { it.id == "tt34791030" })
    }

    @Test
    fun `featured person credits come before documentaries about them`() {
        val documentary = meta(id = "tt13988290", name = "Leonardo DiCaprio: Most Wanted!", releaseInfo = "2021")
        val partialAnime = meta(id = "mal:41158", name = "Leonardo Hakase", type = "series")
        val addonTitanic = meta(id = "tt0120338", name = "Titanic", releaseInfo = "1997", poster = "https://img/addon.jpg")
        // Already in filmography order (vote count).
        val credits = listOf(
            meta(id = "tmdb:27205", name = "Inception", releaseInfo = "2010", poster = "https://img/i.jpg", voteCount = 38_000),
            meta(id = "tmdb:597", name = "Titanic", releaseInfo = "1997", poster = "https://img/t.jpg", voteCount = 26_000),
        )

        val state = resolveSearchUiState(
            query = "leonardo dicaprio",
            sections = listOf(section("a", partialAnime, addonTitanic, documentary)),
            people = listOf(SearchPerson(tmdbId = 6193, name = "Leonardo DiCaprio", department = "Acting")),
            filmography = credits,
            completedProviders = 1,
            failedProviders = 0,
            firstFailure = null,
            isFinished = true,
            hasPendingAddonManifests = false,
        )

        // Credits in filmography order; the addon's Titanic entry (tt id) wins the duplicate; the
        // documentary about him follows; the partial-word anime is dropped.
        assertEquals(listOf("tmdb:27205", "tt0120338", "tt13988290"), state.results.map { it.id })
    }

    @Test
    fun `partial person match keeps credits after all title matches`() {
        val visionQuest = meta(id = "tt30234567", name = "VisionQuest", type = "series")
        val blurred = meta(id = "tt2", name = "Blurred Vision")
        val credit = meta(id = "tmdb:9", name = "The Eight Hundred", poster = "p", voteCount = 900)

        val state = resolveSearchUiState(
            query = "vision",
            sections = listOf(section("a", blurred, visionQuest)),
            people = listOf(SearchPerson(tmdbId = 1, name = "Vision Wei", profilePhoto = "p", department = "Acting")),
            filmography = listOf(credit),
            completedProviders = 1,
            failedProviders = 0,
            firstFailure = null,
            isFinished = true,
            hasPendingAddonManifests = false,
        )

        assertEquals(listOf("tt30234567", "tt2", "tmdb:9"), state.results.map { it.id })
    }

    @Test
    fun `exact title match stays ahead of featured person credits`() {
        val movie = meta(id = "tt0117500", name = "The Rock", releaseInfo = "1996")
        val credit = meta(id = "tmdb:1", name = "Jumanji", poster = "p", voteCount = 9_000)

        val merged = mergeSearchResults(listOf(section("a", movie)), "the rock", credits = listOf(credit))

        assertEquals(listOf("tt0117500", "tmdb:1"), merged.map { it.id })
    }

    @Test
    fun `franchise films the query names lead an obscure exact title and keep the addon entry`() {
        val obscure = meta(id = "tt100", name = "James Bond", releaseInfo = "2015", voteCount = 12)
        val addonSkyfall = meta(id = "tt1074638", name = "Skyfall", releaseInfo = "2012", canonicalMediaKey = "movie:tmdb:37724")
        val docs = meta(id = "tt200", name = "The James Bond Story", releaseInfo = "1999", voteCount = 30)
        val skyfall = meta(id = "tmdb:37724", name = "Skyfall", releaseInfo = "2012", poster = "p", voteCount = 17_000, canonicalMediaKey = "movie:tmdb:37724")
        val drNo = meta(id = "tmdb:646", name = "Dr. No", releaseInfo = "1962", poster = "p", voteCount = 3_500, canonicalMediaKey = "movie:tmdb:646")

        val merged = mergeSearchResults(
            sections = listOf(section("a", obscure, docs, addonSkyfall)),
            query = "james bond",
            franchise = listOf(drNo, skyfall),
        )

        assertEquals(listOf("tt1074638", "tmdb:646", "tt100", "tt200"), merged.map { it.id })
    }

    @Test
    fun `franchise matches only a collection the query fully names`() {
        assertTrue(isSearchFranchiseMatch("James Bond Collection", "james bond"))
        assertTrue(isSearchFranchiseMatch("The Matrix Collection", "matrix"))
        assertFalse(isSearchFranchiseMatch("James Bond Collection", "bond"))
        assertFalse(isSearchFranchiseMatch("Johnny English Collection", "james bond"))
    }

    @Test
    fun `filmography ranks by vote count and drops talk shows self roles docs and guest spots`() {
        val classic = meta(id = "tmdb:1", name = "Classic", poster = "p", voteCount = 9_000, popularity = 5.0)
        val trending = meta(id = "tmdb:2", name = "Trending", poster = "p", voteCount = 300, popularity = 90.0)
        val talkShow = meta(id = "tmdb:3", name = "Late Show", type = "series", poster = "p", voteCount = 20_000)
        val selfDoc = meta(id = "tmdb:4", name = "Making Of", poster = "p", voteCount = 15_000)
        val noPoster = meta(id = "tmdb:5", name = "No Poster", voteCount = 14_000)
        val guestSpot = meta(id = "tmdb:6", name = "Hit Show", type = "series", poster = "p", voteCount = 13_000)
        val mainSeries = meta(id = "tmdb:7", name = "Lead Role", type = "series", poster = "p", voteCount = 1_000)
        val aboutDoc = meta(id = "tmdb:8", name = "Portrait", poster = "p", voteCount = 12_000)
        val detail = PersonDetail(
            tmdbId = 1, name = "Someone", biography = null, birthday = null, deathday = null,
            placeOfBirth = null, profilePhoto = null, knownFor = "Acting",
            movieCredits = listOf(trending, classic, selfDoc, noPoster, aboutDoc),
            tvCredits = listOf(talkShow, guestSpot, mainSeries),
            creditGenreIds = mapOf("series:tmdb:3" to setOf(10767), "movie:tmdb:8" to setOf(99)),
            actingCredits = listOf(
                ActingCredit(selfDoc, listOf("Himself")),
                ActingCredit(classic, listOf("Jack")),
                ActingCredit(guestSpot, listOf("Guest"), episodeCount = 1),
                ActingCredit(mainSeries, listOf("Lead"), episodeCount = 40),
            ),
            directedKeys = setOf("movie:tmdb:1", "movie:tmdb:8"),
        )

        assertEquals(listOf("tmdb:1", "tmdb:7", "tmdb:2"), filterSearchFilmography(detail, isDirector = false).map { it.id })
        // Directors keep only titles they directed, documentaries included.
        assertEquals(listOf("tmdb:8", "tmdb:1"), filterSearchFilmography(detail, isDirector = true).map { it.id })
    }

    @Test
    fun `people rank by popularity and same-name stubs are dropped`() {
        fun person(id: Int, name: String, votes: Int, popularity: Double, photo: String? = "p") =
            SearchPerson(tmdbId = id, name = name, profilePhoto = photo, department = "Acting", popularity = popularity, knownForVotes = votes)
        val trending = person(1, "Brooke Lena Johnson", votes = 1_400, popularity = 60.0)
        val famous = person(2, "Dwayne Johnson", votes = 90_000, popularity = 20.0)
        val noPhoto = person(3, "Dwayne Johnson", votes = 500, popularity = 1.0, photo = null)
        val noCredits = person(4, "Kim Johnson", votes = 0, popularity = 5.0)

        val filtered = filterSearchPeople(listOf(trending, famous, noPhoto, noCredits), "johnson")

        assertEquals(listOf(1, 2), filtered.map { it.tmdbId })
    }

    @Test
    fun `playable filter mirrors details playback for every id kind`() = kotlinx.coroutines.test.runTest {
        // A Torrentio-like setup: only IMDb ids stream.
        val canPlay = { _: String, id: String -> id.startsWith("tt") }
        val lookups = mutableListOf<Int>()
        val resolve: suspend (Int, String) -> String? = { tmdbId, _ ->
            lookups += tmdbId
            if (tmdbId == 624527) "tt9999999" else null
        }
        val imdb = meta(id = "tt1", name = "Imdb")
        val animeWithImdb = MetaPreview(id = "mal:55813", type = "series", name = "Mashle", alternateIds = setOf("tt21209804"))
        val tmdbResolvable = meta(id = "tmdb:624527", name = "Vision")
        val tmdbUnresolvable = meta(id = "tmdb:255950", name = "VISION", type = "series")
        val tvdb = meta(id = "tvdb:311110", name = "Night Vision", type = "series")

        val kept = filterPlayableSearchResults(
            listOf(imdb, animeWithImdb, tmdbResolvable, tmdbUnresolvable, tvdb),
            canPlay,
            resolve,
        )

        assertEquals(listOf("tt1", "mal:55813", "tmdb:624527"), kept.map { it.id })
        // Ids that already play, or carry an IMDb id, need no network lookup.
        assertEquals(setOf(624527, 255950), lookups.toSet())
    }

    @Test
    fun `only undecided tmdb ids need an early imdb lookup`() {
        val canPlay = { _: String, id: String -> id.startsWith("tt") }

        assertNull(searchResultTmdbLookupId(meta(id = "tt1", name = "Plays"), canPlay))
        assertNull(searchResultTmdbLookupId(MetaPreview(id = "mal:1", type = "series", name = "A", alternateIds = setOf("tt2")), canPlay))
        assertNull(searchResultTmdbLookupId(meta(id = "tvdb:3", name = "No Lookup"), canPlay))
        assertEquals(624527, searchResultTmdbLookupId(meta(id = "tmdb:624527", name = "Vision"), canPlay))
    }

    @Test
    fun `filtering every result away without people shows no results`() {
        val state = SearchUiState(results = listOf(meta(id = "tmdb:1", name = "Vision")))

        val filtered = state.withFilteredResults(emptyList())

        assertTrue(filtered.results.isEmpty())
        assertEquals(SearchEmptyStateReason.NoResults, filtered.emptyStateReason)
        val withPerson = state.copy(people = listOf(SearchPerson(tmdbId = 1, name = "Vision Wei")))
        assertNull(withPerson.withFilteredResults(emptyList()).emptyStateReason)
    }

    @Test
    fun `stale search generation can no longer publish`() {
        val generations = SearchGeneration()
        val first = generations.next()
        assertTrue(generations.isCurrent(first))

        val second = generations.next()

        assertFalse(generations.isCurrent(first))
        assertTrue(generations.isCurrent(second))
    }

    private fun section(key: String, vararg items: MetaPreview): HomeCatalogSection =
        HomeCatalogSection(
            key = key,
            title = key,
            subtitle = key,
            addonName = key,
            target = CatalogTarget.Addon(
                manifestUrl = "https://$key.example/manifest.json",
                contentType = "movie",
                catalogId = key,
                supportsPagination = false,
            ),
            items = items.toList(),
        )

    private fun meta(
        id: String,
        name: String,
        type: String = "movie",
        releaseInfo: String? = null,
        poster: String? = null,
        popularity: Double? = null,
        canonicalMediaKey: String? = null,
        voteCount: Int? = null,
    ): MetaPreview = MetaPreview(
        id = id,
        type = type,
        name = name,
        releaseInfo = releaseInfo,
        poster = poster,
        popularity = popularity,
        canonicalMediaKey = canonicalMediaKey,
        voteCount = voteCount,
    )
}
