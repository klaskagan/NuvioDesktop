package com.nuvio.app.features.details

import androidx.compose.ui.input.key.Key
import com.nuvio.app.features.details.components.boardCredits
import com.nuvio.app.features.details.components.rateStripKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardCreditsTest {
    private val cast = listOf(
        MetaPerson("Dan Erickson", "Creator"),
        MetaPerson("Ben Stiller", "Director"),
        MetaPerson("Adam Scott", "Mark S."),
        MetaPerson("Britt Lower", "Helly R."),
        MetaPerson("Zach Cherry", "Dylan G."),
        MetaPerson("John Turturro", "Irving B."),
    )

    @Test fun seriesNameTheirCreatorsAndTopThreeCast() {
        val credits = boardCredits(MetaDetails("s", "series", "Severance", cast = cast), isSeries = true, creatorRole = "Creator", directorRole = "Director", writerRole = "Writer")
        assertEquals(listOf("Dan Erickson"), credits.lead.map { it.name })
        assertEquals(listOf("Adam Scott", "Britt Lower", "Zach Cherry"), credits.starring.map { it.name })
    }

    @Test fun filmsNameTheirDirectorFromCrewOrTheDirectorField() {
        val fromCrew = boardCredits(MetaDetails("m", "movie", "Film", cast = cast), isSeries = false, creatorRole = "Creator", directorRole = "Director", writerRole = "Writer")
        assertEquals(listOf("Ben Stiller"), fromCrew.lead.map { it.name })
        val fromField = boardCredits(MetaDetails("m", "movie", "Film", director = listOf("Denis Villeneuve")), isSeries = false, creatorRole = "Creator", directorRole = "Director", writerRole = "Writer")
        assertEquals(listOf("Denis Villeneuve"), fromField.lead.map { it.name })
        assertEquals(emptyList(), fromField.starring)
    }

    @Test fun rateStripKeysStepAndPick() {
        assertEquals(7, rateStripKeyValue(Key.DirectionLeft, 8))
        assertEquals(10, rateStripKeyValue(Key.DirectionRight, 10))
        assertEquals(1, rateStripKeyValue(Key.DirectionLeft, 1))
        assertEquals(10, rateStripKeyValue(Key.Zero, 3))
        assertEquals(9, rateStripKeyValue(Key.Nine, 3))
        assertNull(rateStripKeyValue(Key.A, 3))
    }
}
