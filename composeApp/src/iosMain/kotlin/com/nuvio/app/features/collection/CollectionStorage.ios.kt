package com.nuvio.app.features.collection

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

actual object CollectionStorage {
    private const val payloadKey = "collections_payload"

    actual fun seasonalSeedVersion(): Int = NSUserDefaults.standardUserDefaults.integerForKey(ProfileScopedKey.of("seasonal_seed_version")).toInt()
    actual fun saveSeasonalSeedVersion(version: Int) { NSUserDefaults.standardUserDefaults.setInteger(version.toLong(), forKey = ProfileScopedKey.of("seasonal_seed_version")) }

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey))
    }
}
