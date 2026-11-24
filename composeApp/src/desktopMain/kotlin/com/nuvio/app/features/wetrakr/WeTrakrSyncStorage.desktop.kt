package com.nuvio.app.features.wetrakr

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.MAX_PROFILES

/** The WeTrakr sync snapshot per profile, so the library, progress and ratings show before a refresh. */
internal actual object PlatformWeTrakrSyncStorage : WeTrakrSyncStorage {
    private const val payloadKey = "wetrakr_sync_snapshot"
    private val store = DesktopStorage.store("nuvio_wetrakr_sync")

    actual override suspend fun load(profileId: Int): String? =
        store.getString(ProfileScopedKey.of(payloadKey, profileId))

    actual override suspend fun save(profileId: Int, payload: String, checkScope: () -> Unit) {
        checkScope()
        store.putString(ProfileScopedKey.of(payloadKey, profileId), payload)
    }

    actual override suspend fun remove(profileId: Int, checkScope: () -> Unit) {
        checkScope()
        store.remove(ProfileScopedKey.of(payloadKey, profileId))
    }

    actual fun clearAll() {
        store.removeAll((1..MAX_PROFILES).map { ProfileScopedKey.of(payloadKey, it) })
    }
}
