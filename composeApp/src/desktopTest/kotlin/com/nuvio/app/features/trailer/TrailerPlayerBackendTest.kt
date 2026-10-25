package com.nuvio.app.features.trailer

import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenSettingsStorage
import com.nuvio.app.core.ui.PosterCardStyleRepository
import kotlin.test.*
import org.junit.Before

class TrailerPlayerBackendTest {
    @Before fun isolate() {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.clearLocalState()
        MetaScreenSettingsStorage.savePayload("{}")
    }

    @Test fun existingProfilesAndUnknownValuesDefaultToYoutube() {
        for (payload in listOf("{}", """{"hero_trailer_playback":true}""", """{"trailer_player_backend":"future"}""")) {
            MetaScreenSettingsRepository.clearLocalState()
            MetaScreenSettingsStorage.savePayload(payload)
            MetaScreenSettingsRepository.ensureLoaded()
            assertEquals(TrailerPlayerBackend.YouTube, MetaScreenSettingsRepository.uiState.value.trailerPlayerBackend)
        }
    }

    @Test fun choicePersistsThroughReloadAndSyncDoesNotOverwriteThisLocalPlayerPreference() {
        MetaScreenSettingsRepository.setHeroTrailerPlayback(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerSoundEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerStartSeconds(3)
        MetaScreenSettingsRepository.setTrailerPlayerBackend(TrailerPlayerBackend.Native)
        MetaScreenSettingsRepository.onProfileChanged()
        val saved = MetaScreenSettingsRepository.uiState.value
        assertEquals(TrailerPlayerBackend.Native, saved.trailerPlayerBackend)
        assertTrue(saved.heroTrailerPlayback)
        MetaScreenSettingsRepository.applyFromSync(saved.items, saved.cinematicBackground, heroTrailerPlayback = true, tabLayout = saved.tabLayout)
        assertEquals(TrailerPlayerBackend.Native, MetaScreenSettingsRepository.uiState.value.trailerPlayerBackend)
        assertTrue(PosterCardStyleRepository.uiState.value.hoverPreviewTrailerEnabled)
        assertTrue(PosterCardStyleRepository.uiState.value.hoverPreviewTrailerSoundEnabled)
        assertEquals(3, PosterCardStyleRepository.uiState.value.hoverPreviewTrailerStartSeconds)
        MetaScreenSettingsRepository.resetToDefaults()
        assertEquals(TrailerPlayerBackend.YouTube, MetaScreenSettingsRepository.uiState.value.trailerPlayerBackend)
    }

    @Test fun unsupportedPlatformsUseNativeWithoutChangingTheSavedPreference() {
        assertEquals(TrailerPlayerBackend.Native, effectiveTrailerPlayerBackend(TrailerPlayerBackend.YouTube, false))
        assertEquals(TrailerPlayerBackend.YouTube, effectiveTrailerPlayerBackend(TrailerPlayerBackend.YouTube, true))
        assertEquals(TrailerPlayerBackend.Native, effectiveTrailerPlayerBackend(TrailerPlayerBackend.Native, true))
    }
}
