package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BehindTheFilmParserTest {
    // Wikidata's answer for Dune: Part Two (tt15239678), saved on 2026-11-24.
    private val dune = "{\"results\": {\"bindings\": [{\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"basedOnLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Dune\"}, \"authorLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Frank Herbert\"}, \"formLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"novel\"}, \"published\": {\"type\": \"literal\", \"value\": \"1966-01-01T00:00:00Z\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"basedOnLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Dune\"}, \"published\": {\"type\": \"literal\", \"value\": \"1965-08-01T00:00:00Z\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Italy\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Jordan\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Abu Dhabi\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Budapest\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"role\": {\"type\": \"literal\", \"value\": \"music\"}, \"personTmdb\": {\"type\": \"literal\", \"value\": \"947\"}, \"personLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Hans Zimmer\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"role\": {\"type\": \"literal\", \"value\": \"director\"}, \"personTmdb\": {\"type\": \"literal\", \"value\": \"137427\"}, \"personLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Denis Villeneuve\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"role\": {\"type\": \"literal\", \"value\": \"cinematography\"}, \"personTmdb\": {\"type\": \"literal\", \"value\": \"67113\"}, \"personLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Greig Fraser\"}}]}}"

    @Test fun readsEveryColumnWithTmdbIdsForPeople() {
        val info = BehindTheFilmRepository.parse(dune)!!
        // Two editions of the novel: the earliest publication year wins.
        assertEquals(listOf(BehindTheFilmWork("Dune", "novel", "Frank Herbert", 1965)), info.basedOn)
        assertEquals("novel · Frank Herbert, 1965", info.basedOn.single().detail)
        assertEquals(listOf(BehindTheFilmPerson("Hans Zimmer", 947)), info.music)
        assertEquals(listOf(BehindTheFilmPerson("Greig Fraser", 67113)), info.cinematography)
        assertEquals(listOf(BehindTheFilmPerson("Denis Villeneuve", 137427)), info.directedBy)
        assertEquals(listOf("Italy", "Jordan", "Abu Dhabi", "Budapest"), info.filmedIn)
        assertEquals("https://www.wikidata.org/wiki/" + info.wikidataId, info.url)
    }

    @Test fun aWorkWithoutFormOrYearShowsWhatItHas() {
        assertEquals("Stephen King", BehindTheFilmWork("It", null, "Stephen King", null).detail)
        assertNull(BehindTheFilmWork("It", null, null, null).detail)
    }

    @Test fun awardsComeFromWinsAndNominationsAndAloneHideTheTile() {
        fun row(label: String, kind: String) =
            "{\"item\":{\"value\":\"http://www.wikidata.org/entity/Q1\"},\"awardLabel\":{\"value\":\"$label\"},\"awardKind\":{\"value\":\"$kind\"}}"
        val body = "{\"results\":{\"bindings\":[" + listOf(
            row("Primetime Emmy Award for Outstanding Main Title Design", "won"),
            row("Golden Globe Award for Best Television Series – Drama", "nominated"),
            row("Primetime Emmy Award for Outstanding Main Title Design", "nominated"),
        ).joinToString(",") + "]}}"
        val info = BehindTheFilmRepository.parse(body)!!
        assertEquals(listOf("Primetime Emmy Award for Outstanding Main Title Design"), info.awardsWon)
        // A category it won isn't also counted as a nomination.
        assertEquals(listOf("Golden Globe Award for Best Television Series – Drama"), info.awardsNominated)
        assertFalse(info.hasTile)
    }

    @Test fun awardsSummaryLeadsWithTheBigCeremonies() {
        val sinners = listOf(
            "Black Reel Award for Best Film", "Academy Award for Best Actor", "Academy Award for Best Cinematography",
            "Black Reel Award for Outstanding Ensemble", "Academy Award for Best Original Score",
            "Academy Award for Best Writing, Original Screenplay", "Black Reel Award for Best Screenplay, Adapted or Original",
            "Hugo Award for Best Dramatic Presentation, Long Form",
        )
        assertEquals("4 Oscars · 3 Black Reel Awards · 1 other win · 1 nomination", summarizeAwards(sinners, listOf("FIPRESCI Grand Prix")))
        assertEquals("1 Primetime Emmy · 1 nomination", summarizeAwards(listOf("Primetime Emmy Award for Outstanding Main Title Design"), listOf("Golden Globe Award for Best Television Series – Drama")))
        assertEquals("2 nominations", summarizeAwards(emptyList(), listOf("A", "B")))
        assertNull(summarizeAwards(emptyList(), emptyList()))
    }

    @Test fun noMatchOrOnlyUnlabelledEntitiesHidesTheTile() {
        assertNull(BehindTheFilmRepository.parse("{\"results\":{\"bindings\":[]}}"))
        assertNull(BehindTheFilmRepository.parse("{\"results\":{\"bindings\":[{\"item\":{\"value\":\"http://www.wikidata.org/entity/Q1\"},\"placeLabel\":{\"value\":\"Q123\"}}]}}"))
    }
}
