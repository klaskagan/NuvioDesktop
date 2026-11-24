package com.nuvio.app.features.gallery

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object KinopoiskKeyStorage {
    private val store = DesktopStorage.store("nuvio_kinopoisk")

    actual fun load(): String? = store.getString(ProfileScopedKey.of("kinopoisk_api_key"))

    actual fun save(key: String?) {
        val storageKey = ProfileScopedKey.of("kinopoisk_api_key")
        if (key == null) store.remove(storageKey) else store.putString(storageKey, key)
    }
}
