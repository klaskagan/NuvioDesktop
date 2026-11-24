package com.nuvio.app.features.gallery

/** The gallery is a desktop board feature; other platforms keep the key for the session only. */
internal actual object KinopoiskKeyStorage {
    private var key: String? = null

    actual fun load(): String? = key

    actual fun save(key: String?) {
        this.key = key
    }
}
