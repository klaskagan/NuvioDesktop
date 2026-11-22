package com.nuvio.app.features.search

/** Search preferences, with profile-scoped result settings and a device-local watch region. */
internal expect object SearchPreferencesStorage {
    fun deviceRegion(): String?
    fun loadStreamingCountry(): String?
    fun saveStreamingCountry(country: String?)

    fun loadLandscapeEnabled(): Boolean
    fun saveLandscapeEnabled(enabled: Boolean)

    /** Stored inverted so a missing key means the default: unplayable results hidden. */
    fun loadShowUnplayable(): Boolean
    fun saveShowUnplayable(show: Boolean)
}
