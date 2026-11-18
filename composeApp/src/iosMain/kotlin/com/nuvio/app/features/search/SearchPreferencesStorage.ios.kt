package com.nuvio.app.features.search

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object SearchPreferencesStorage {
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
