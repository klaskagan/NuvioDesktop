package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatingVotesTest {
    @Test
    fun absentAndNonPositiveCountsAreHidden() {
        listOf(null, 0L, -1L).forEach { assertNull(formatRatingVotes(it)) }
    }

    @Test
    fun compactCountsUseLowercaseSuffixesWithoutTrailingZeros() {
        mapOf(
            284L to "284", 999L to "999", 1_000L to "1k",
            6_900L to "6.9k", 9_999L to "10k", 10_500L to "11k",
            657_000L to "657k", 999_499L to "999k", 999_500L to "1m",
            1_200_000L to "1.2m", 657_000_000L to "657m",
            999_500_000L to "1b", 1_200_000_000L to "1.2b",
        ).forEach { (votes, expected) -> assertEquals(expected, formatRatingVotes(votes), "$votes votes") }
    }
}
