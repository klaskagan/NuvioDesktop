package com.nuvio.app.features.home

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

actual object HomeCatalogSettingsStorage {
    private const val payloadKey = "catalog_settings_payload"

    actual fun loadPayload(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey, profileId))

    actual fun savePayload(payload: String, profileId: Int) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey, profileId))
    }
}
