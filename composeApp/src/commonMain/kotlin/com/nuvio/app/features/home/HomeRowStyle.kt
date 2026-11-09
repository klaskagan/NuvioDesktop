package com.nuvio.app.features.home

import com.nuvio.app.features.collection.SeasonalCollection
import com.nuvio.app.features.trakt.TRAKT_CALENDAR_KEY

enum class HomeRowStyle(val id: String) {
    Automatic("automatic"),
    NumberedRanking("numbered-ranking");

    companion object {
        internal fun fromId(id: String?): HomeRowStyle = entries.firstOrNull { it.id == id } ?: Automatic
    }
}

internal fun supportsHomeRowStyle(key: String): Boolean =
    key != TRAKT_CALENDAR_KEY && key != SeasonalCollection.HOME_KEY
