package com.nuvio.app.features.search

/** Search's own preferences, independent from Home and Calendar settings. Profile-scoped. */
internal expect object SearchPreferencesStorage {
    fun loadLandscapeEnabled(): Boolean
    fun saveLandscapeEnabled(enabled: Boolean)

    /** Stored inverted so a missing key means the default: unplayable results hidden. */
    fun loadShowUnplayable(): Boolean
    fun saveShowUnplayable(show: Boolean)
}
