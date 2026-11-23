package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatingTextTest {
    @Test
    fun syncedFloatRatingsShowOneDecimal() {
        // Library ratings came back from sync as Float strings and were printed raw.
        assertEquals("5.7", displayRatingText("5.6504974365234375"))
        assertEquals("6.9", displayRatingText("6.852910041809082"))
        assertEquals("8.0", displayRatingText("8"))
        assertEquals("7.2", displayRatingText(" 7.2 "))
    }

    @Test
    fun blankAndNonNumericRatings() {
        assertNull(displayRatingText(null))
        assertNull(displayRatingText("  "))
        assertEquals("N/A", displayRatingText("N/A"))
    }
}
