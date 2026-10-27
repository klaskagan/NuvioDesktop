package com.nuvio.app.features.collection

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object CollectionStorage {
    private const val preferencesName = "nuvio_collections"
    private const val payloadKey = "collections_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun seasonalSeedVersion(): Int = preferences?.getInt(ProfileScopedKey.of("seasonal_seed_version"), 0) ?: 0
    actual fun saveSeasonalSeedVersion(version: Int) { preferences?.edit()?.putInt(ProfileScopedKey.of("seasonal_seed_version"), version)?.apply() }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }
}
