package com.nuvio.app.core.format

import kotlin.math.roundToLong

/**
 * A dollar amount as the Details tab shows it: "$190M", "$714.8M", "$1.2B"; amounts under a
 * million keep every digit through [formatWholeUsd].
 */
internal fun formatCompactUsd(amount: Long, localeTag: String): String {
    fun scaled(unit: Long, suffix: String): String {
        val tenths = (amount.toDouble() / unit * 10).roundToLong()
        val whole = tenths / 10
        val fraction = tenths % 10
        return "$" + whole + (if (fraction != 0L) ".$fraction" else "") + suffix
    }
    return when {
        amount >= 1_000_000_000L -> scaled(1_000_000_000L, "B")
        amount >= 1_000_000L -> scaled(1_000_000L, "M")
        else -> formatWholeUsd(amount, localeTag)
    }
}
