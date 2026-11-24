package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BehindTheFilmParserTest {
    // Wikidata's answer for Dune: Part Two (tt15239678), saved on 2026-11-24.
    private val dune = "{\"results\": {\"bindings\": [{\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"basedOnLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Dune\"}, \"authorLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Frank Herbert\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Italy\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Jordan\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Abu Dhabi\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"placeLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Budapest\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"role\": {\"type\": \"literal\", \"value\": \"music\"}, \"personTmdb\": {\"type\": \"literal\", \"value\": \"947\"}, \"personLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Hans Zimmer\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"role\": {\"type\": \"literal\", \"value\": \"director\"}, \"personTmdb\": {\"type\": \"literal\", \"value\": \"137427\"}, \"personLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Denis Villeneuve\"}}, {\"item\": {\"type\": \"uri\", \"value\": \"http://www.wikidata.org/entity/Q109228991\"}, \"role\": {\"type\": \"literal\", \"value\": \"cinematography\"}, \"personTmdb\": {\"type\": \"literal\", \"value\": \"67113\"}, \"personLabel\": {\"xml:lang\": \"en\", \"type\": \"literal\", \"value\": \"Greig Fraser\"}}]}}"

    @Test fun readsEveryColumnWithTmdbIdsForPeople() {
        val info = BehindTheFilmRepository.parse(dune)!!
        assertEquals(listOf("Dune · Frank Herbert"), info.basedOn)
        assertEquals(listOf(BehindTheFilmPerson("Hans Zimmer", 947)), info.music)
        assertEquals(listOf(BehindTheFilmPerson("Greig Fraser", 67113)), info.cinematography)
        assertEquals(listOf(BehindTheFilmPerson("Denis Villeneuve", 137427)), info.directedBy)
        assertEquals(listOf("Italy", "Jordan", "Abu Dhabi", "Budapest"), info.filmedIn)
        assertEquals("https://www.wikidata.org/wiki/" + info.wikidataId, info.url)
    }

    @Test fun noMatchOrOnlyUnlabelledEntitiesHidesTheTile() {
        assertNull(BehindTheFilmRepository.parse("{\"results\":{\"bindings\":[]}}"))
        assertNull(BehindTheFilmRepository.parse("{\"results\":{\"bindings\":[{\"item\":{\"value\":\"http://www.wikidata.org/entity/Q1\"},\"placeLabel\":{\"value\":\"Q123\"}}]}}"))
    }
}
