package com.nuvio.app.features.gallery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's own Kinopoisk Unofficial API key, per profile and device-local (never synced or logged). */
internal expect object KinopoiskKeyStorage {
    fun load(): String?
    fun save(key: String?)
}

/**
 * Settings → Integrations → Kinopoisk API key. With a key the Details gallery adds real film stills
 * and behind-the-scenes photos; without one, or when the key is invalid or over its daily quota, the
 * gallery quietly shows TMDB images only.
 */
internal object KinopoiskSettingsRepository {
    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _apiKey.value = KinopoiskKeyStorage.load()?.trim()?.takeIf(String::isNotBlank)
    }

    fun setApiKey(key: String?) {
        ensureLoaded()
        val normalized = key?.trim()?.takeIf(String::isNotBlank)
        KinopoiskKeyStorage.save(normalized)
        _apiKey.value = normalized
        KinopoiskGalleryRepository.clearCache()
    }

    /** The active profile changed: read its key. */
    fun onProfileChanged() {
        loaded = false
        ensureLoaded()
    }
}
