package com.nuvio.app.features.search

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.*

internal actual object SearchPreferencesStorage {
    private const val countryKey = "search_streaming_country"

    actual fun deviceRegion(): String? = NSLocale.currentLocale.countryCode

    actual fun loadStreamingCountry(): String? = NSUserDefaults.standardUserDefaults.stringForKey(countryKey)

    actual fun saveStreamingCountry(country: String?) {
        if (country == null) NSUserDefaults.standardUserDefaults.removeObjectForKey(countryKey)
        else NSUserDefaults.standardUserDefaults.setObject(country, forKey = countryKey)
    }

    private const val landscapeKey = "search_landscape_posters"
    private const val showUnplayableKey = "search_show_unplayable"

    actual fun loadLandscapeEnabled(): Boolean = load(landscapeKey)

    actual fun saveLandscapeEnabled(enabled: Boolean) = save(landscapeKey, enabled)

    actual fun loadShowUnplayable(): Boolean = load(showUnplayableKey)

    actual fun saveShowUnplayable(show: Boolean) = save(showUnplayableKey, show)

    private fun load(key: String): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(ProfileScopedKey.of(key))

    private fun save(key: String, value: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(value, forKey = ProfileScopedKey.of(key))
    }
}
