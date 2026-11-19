package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.ProfileRepository
import platform.Foundation.NSUserDefaults

/**
 * Night Mode for the iOS mpv player, mirroring NativePlayerController on desktop: the choice is
 * remembered per profile and re-applied once mpv has an audio output for the next file.
 */
internal class IosNightMode(private val bridge: NuvioPlayerBridge) {
    private val profileId = ProfileRepository.activeProfileId
    private var restorePending = false

    fun isActive(): Boolean = nightModeAudioFilterInChain(bridge.getProperty("af"))

    /** Call after a new file is loaded. */
    fun onSourceLoaded() {
        val enabled = load()
        restorePending = enabled && !isActive()
        if (!enabled && isActive()) apply(false)
    }

    /** Call from the snapshot poll; applies a pending restore once audio output exists. */
    fun poll() {
        if (restorePending && audioOutputReady()) {
            restorePending = false
            apply(true)
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val applied = apply(enabled)
        if (applied) {
            restorePending = false
            save(enabled)
        }
        return applied
    }

    private fun apply(enabled: Boolean): Boolean {
        val accepted = isActive() == enabled || if (enabled) {
            // mpv can accept an unvalidated graph before an audio output exists.
            audioOutputReady() && bridge.runCommand(listOf("af", "add", NIGHT_MODE_AUDIO_FILTER))
        } else {
            bridge.runCommand(listOf("af", "remove", NIGHT_MODE_AUDIO_FILTER_LABEL))
        }
        return (accepted && isActive() == enabled).also { applied ->
            if (!applied) log.w { "mpv could not set Night Mode to $enabled" }
        }
    }

    private fun audioOutputReady(): Boolean = bridge.getProperty("audio-out-params/channel-count").isNotBlank()

    private fun key(): String = ProfileScopedKey.of(NIGHT_MODE_KEY, profileId)

    private fun load(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(key())

    private fun save(enabled: Boolean) = NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = key())

    private companion object {
        const val NIGHT_MODE_KEY = "night_mode_enabled"
        val log = Logger.withTag("IosNightMode")
    }
}
