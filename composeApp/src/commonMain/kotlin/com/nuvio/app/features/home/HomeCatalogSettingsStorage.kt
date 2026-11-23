package com.nuvio.app.features.home

import com.nuvio.app.features.profiles.ProfileRepository

/** Profile-scoped storage; pass the profile explicitly when the caller's state belongs to one. */
internal expect object HomeCatalogSettingsStorage {
    fun loadPayload(profileId: Int = ProfileRepository.activeProfileId): String?
    fun savePayload(payload: String, profileId: Int = ProfileRepository.activeProfileId)
}
