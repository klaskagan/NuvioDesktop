package com.nuvio.app.features.home

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object HomeCatalogSettingsStorage {
    private const val preferencesName = "nuvio_home_catalog_settings"
    private const val payloadKey = "catalog_settings_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(profileId: Int): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey, profileId), null)

    actual fun savePayload(payload: String, profileId: Int) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey, profileId), payload)
            ?.apply()
    }
}
