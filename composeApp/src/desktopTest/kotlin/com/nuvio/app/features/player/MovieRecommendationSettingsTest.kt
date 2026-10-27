package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assume.assumeTrue
import kotlin.test.*

class MovieRecommendationSettingsTest {
    @Test fun defaultsPersistenceResetAndSyncRoundTrip() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val store = DesktopStorage.store("nuvio_player_settings")
        val key = ProfileScopedKey.of("movie_recommendations_enabled")
        val timingKey = ProfileScopedKey.of("movie_recommendation_timing")
        store.remove(timingKey)
        store.remove(key)
        PlayerSettingsRepository.clearLocalState()
        PlayerSettingsRepository.ensureLoaded()
        assertEquals(MovieRecommendationTiming.INTRODB, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
        assertTrue(PlayerSettingsRepository.uiState.value.movieRecommendationsEnabled)
        PlayerSettingsRepository.setMovieRecommendationTiming(MovieRecommendationTiming.FINAL_FOUR_MINUTES)
        PlayerSettingsRepository.setMovieRecommendationsEnabled(false)
        assertEquals(false, PlayerSettingsStorage.loadMovieRecommendationsEnabled())
        PlayerSettingsRepository.clearLocalState()
        PlayerSettingsRepository.ensureLoaded()
        assertEquals(MovieRecommendationTiming.FINAL_FOUR_MINUTES, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
        assertFalse(PlayerSettingsRepository.uiState.value.movieRecommendationsEnabled)
        val payload = PlayerSettingsStorage.exportToSyncPayload()
        assertTrue(payload.containsKey("movie_recommendations_enabled"))
        assertTrue(payload.containsKey("movie_recommendation_timing"))
        val otherProfileKey = ProfileScopedKey.of("movie_recommendations_enabled", ProfileRepository.activeProfileId + 1)
        store.putBoolean(otherProfileKey, true)
        PlayerSettingsStorage.replaceFromSyncPayload(buildJsonObject {})
        assertNull(PlayerSettingsStorage.loadMovieRecommendationsEnabled())
        assertNull(PlayerSettingsStorage.loadMovieRecommendationTiming())
        assertEquals(true, store.getBoolean(otherProfileKey))
        PlayerSettingsRepository.onProfileChanged()
        assertTrue(PlayerSettingsRepository.uiState.value.movieRecommendationsEnabled)
        PlayerSettingsStorage.replaceFromSyncPayload(payload)
        PlayerSettingsRepository.onProfileChanged()
        assertFalse(PlayerSettingsRepository.uiState.value.movieRecommendationsEnabled)
        assertEquals(MovieRecommendationTiming.FINAL_FOUR_MINUTES, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
        PlayerSettingsStorage.saveMovieRecommendationTiming("unknown-future-mode")
        PlayerSettingsRepository.onProfileChanged()
        assertEquals(MovieRecommendationTiming.INTRODB, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
        store.remove(timingKey)
        store.remove(key)
        store.remove(otherProfileKey)
        PlayerSettingsRepository.clearLocalState()
    }
}
