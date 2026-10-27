package com.nuvio.app.features.collection

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object CollectionStorage {
    private val store = DesktopStorage.store("nuvio_collections")

    actual fun seasonalSeedVersion(): Int = store.getInt(ProfileScopedKey.of("seasonal_seed_version")) ?: 0
    actual fun saveSeasonalSeedVersion(version: Int) { store.putInt(ProfileScopedKey.of("seasonal_seed_version"), version) }

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("collections"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("collections"), payload)
    }
}
