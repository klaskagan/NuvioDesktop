package com.nuvio.app.features.wetrakr

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.MAX_PROFILES

/** WeTrakr sign-in per profile, stored the way the other desktop trackers keep theirs (MDBList). */
internal actual object PlatformWeTrakrAuthPersistence : WeTrakrAuthPersistence {
    private const val payloadKey = "wetrakr_auth_payload"
    private val store = DesktopStorage.store("nuvio_wetrakr_auth")

    actual override fun read(profileId: Int): String? =
        store.getString(ProfileScopedKey.of(payloadKey, profileId))

    actual override fun write(profileId: Int, value: String?) {
        store.putString(ProfileScopedKey.of(payloadKey, profileId), value)
    }

    actual override fun clear() {
        store.removeAll((1..MAX_PROFILES).map { ProfileScopedKey.of(payloadKey, it) })
    }
}
