package com.nuvio.app.features.search

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object SearchPreferencesStorage {
    private val store = DesktopStorage.store("nuvio_discover_selection")
    private const val landscapeKey = "search_landscape_posters"
    private const val showUnplayableKey = "search_show_unplayable"

    actual fun loadLandscapeEnabled(): Boolean = load(landscapeKey)

    actual fun saveLandscapeEnabled(enabled: Boolean) = save(landscapeKey, enabled)

    actual fun loadShowUnplayable(): Boolean = load(showUnplayableKey)

    actual fun saveShowUnplayable(show: Boolean) = save(showUnplayableKey, show)

    private fun load(key: String): Boolean = store.getString(ProfileScopedKey.of(key)) == "true"

    private fun save(key: String, value: Boolean) {
        store.putString(ProfileScopedKey.of(key), value.toString())
    }
}
