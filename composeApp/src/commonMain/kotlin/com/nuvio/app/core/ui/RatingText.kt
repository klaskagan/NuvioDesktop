package com.nuvio.app.core.ui

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * A rating string as shown on cards: one decimal ("6.8"). Ratings that went through the synced
 * library as a Float came back as "5.6504974365234375" and were printed raw on Library's hero,
 * lead cards and hover previews. Non-numeric text (an addon's own label) is kept as is.
 */
internal fun displayRatingText(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val value = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return text
    val tenths = (value * 10.0).roundToInt()
    return "${tenths / 10}.${(tenths % 10).absoluteValue}"
}
