package com.nuvio.app.features.home

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object HomeCatalogSettingsStorage {
    private val store = DesktopStorage.store("nuvio_home_catalog_settings")

    actual fun loadPayload(profileId: Int): String? =
        store.getString(ProfileScopedKey.of("home_catalog_settings", profileId))

    actual fun savePayload(payload: String, profileId: Int) {
        store.putString(ProfileScopedKey.of("home_catalog_settings", profileId), payload)
    }
}
