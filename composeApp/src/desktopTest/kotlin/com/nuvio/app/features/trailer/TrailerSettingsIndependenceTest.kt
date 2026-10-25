package com.nuvio.app.features.trailer

import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import kotlin.test.*

class TrailerSettingsIndependenceTest {
    @Test fun heroAndHoverPlaybackRemainIndependent() {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.setHeroTrailerPlayback(false)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(true)
        assertFalse(MetaScreenSettingsRepository.uiState.value.heroTrailerPlayback)
        assertTrue(PosterCardStyleRepository.uiState.value.hoverPreviewTrailerEnabled)
        MetaScreenSettingsRepository.setHeroTrailerPlayback(true)
        PosterCardStyleRepository.setHoverPreviewTrailerEnabled(false)
        assertTrue(MetaScreenSettingsRepository.uiState.value.heroTrailerPlayback)
        assertFalse(PosterCardStyleRepository.uiState.value.hoverPreviewTrailerEnabled)
        PosterCardStyleRepository.setHoverPreviewTrailerSoundEnabled(true)
        PosterCardStyleRepository.setHoverPreviewTrailerStartSeconds(3)
        assertTrue(MetaScreenSettingsRepository.uiState.value.heroTrailerPlayback)
        assertEquals(3, PosterCardStyleRepository.uiState.value.hoverPreviewTrailerStartSeconds)
    }
}
