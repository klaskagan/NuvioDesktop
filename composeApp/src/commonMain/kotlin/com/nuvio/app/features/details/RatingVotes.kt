package com.nuvio.app.features.details

import kotlin.math.roundToLong

internal fun formatRatingVotes(votes: Long?): String? {
    if (votes == null || votes <= 0) return null
    if (votes < 1_000) return votes.toString()
    val divisor = when {
        votes < 999_500 -> 1_000L
        votes < 999_500_000 -> 1_000_000L
        else -> 1_000_000_000L
    }
    val suffix = when (divisor) {
        1_000L -> "k"
        1_000_000L -> "m"
        else -> "b"
    }
    val tenths = (votes.toDouble() / divisor * 10).roundToLong()
    val number = if (tenths < 100 && tenths % 10 != 0L) {
        "${tenths / 10}.${tenths % 10}"
    } else {
        (votes.toDouble() / divisor).roundToLong().toString()
    }
    return number + suffix
}
