package com.nuvio.app.features.search

import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object SearchPreferencesStorage {
    private const val countryKey = "search_streaming_country"

    actual fun deviceRegion(): String? =
        android.content.res.Resources.getSystem().configuration.locales[0]?.country?.takeIf(String::isNotBlank)

    actual fun loadStreamingCountry(): String? = DiscoverSelectionStorage.preferences?.getString(countryKey, null)

    actual fun saveStreamingCountry(country: String?) {
        DiscoverSelectionStorage.preferences?.edit()?.putString(countryKey, country)?.apply()
    }

    private const val landscapeKey = "search_landscape_posters"
    private const val showUnplayableKey = "search_show_unplayable"

    actual fun loadLandscapeEnabled(): Boolean = load(landscapeKey)

    actual fun saveLandscapeEnabled(enabled: Boolean) = save(landscapeKey, enabled)

    actual fun loadShowUnplayable(): Boolean = load(showUnplayableKey)

    actual fun saveShowUnplayable(show: Boolean) = save(showUnplayableKey, show)

    private const val tagRowLayoutKey = "search_provider_row_layout"

    actual fun loadTagRowLayout(): String? =
        DiscoverSelectionStorage.preferences?.getString(ProfileScopedKey.of(tagRowLayoutKey), null)

    actual fun saveTagRowLayout(payload: String) {
        DiscoverSelectionStorage.preferences?.edit()?.putString(ProfileScopedKey.of(tagRowLayoutKey), payload)?.apply()
    }

    private fun load(key: String): Boolean =
        DiscoverSelectionStorage.preferences?.getBoolean(ProfileScopedKey.of(key), false) ?: false

    private fun save(key: String, value: Boolean) {
        DiscoverSelectionStorage.preferences?.edit()?.putBoolean(ProfileScopedKey.of(key), value)?.apply()
    }
}
