package com.nuvio.app.features.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.settings.playbackSettingsContent
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MovieRecommendationTimingUiTest {
    @Test fun playbackSelectorPersistsBothTimingChoices() = runDesktopComposeUiTest(width = 1000, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PlayerSettingsRepository.setMovieRecommendationTiming(MovieRecommendationTiming.INTRODB)
        setContent {
            AppEnvironment {
                val settings by PlayerSettingsRepository.uiState.collectAsState()
                LazyColumn { playbackSettingsContent(
                    isTablet = true,
                    showLoadingOverlay = settings.showLoadingOverlay,
                    holdToSpeedEnabled = settings.holdToSpeedEnabled,
                    holdToSpeedValue = settings.holdToSpeedValue,
                    touchGesturesEnabled = settings.touchGesturesEnabled,
                    preferredAudioLanguage = settings.preferredAudioLanguage,
                    secondaryPreferredAudioLanguage = settings.secondaryPreferredAudioLanguage,
                    preferredSubtitleLanguage = settings.preferredSubtitleLanguage,
                    secondaryPreferredSubtitleLanguage = settings.secondaryPreferredSubtitleLanguage,
                    streamReuseLastLinkEnabled = settings.streamReuseLastLinkEnabled,
                    streamReuseLastLinkCacheHours = settings.streamReuseLastLinkCacheHours,
                    androidPlaybackEngine = settings.androidPlaybackEngine,
                    androidLibmpvVideoOutput = settings.androidLibmpvVideoOutput,
                    androidLibmpvHardwareDecodingEnabled = settings.androidLibmpvHardwareDecodingEnabled,
                    androidLibmpvYuv420pEnabled = settings.androidLibmpvYuv420pEnabled,
                    decoderPriority = settings.decoderPriority,
                    mapDV7ToHevc = settings.mapDV7ToHevc,
                    tunnelingEnabled = settings.tunnelingEnabled,
                    useLibass = settings.useLibass,
                    libassRenderType = settings.libassRenderType,
                    libassRenderScale = settings.libassRenderScale,
                ) }
            }
        }
        onNodeWithText("Movie Recommendation Timing").performScrollTo().performClick()
        onAllNodesWithText("IntroDB API").onLast().assertExists()
        onNodeWithText("Final 4 minutes").assertExists()
        val directory = File("build/qa/movie-recommendation-timing").apply { mkdirs() }
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(directory, "selector.png"))
        onNodeWithText("Final 4 minutes").performClick()
        runOnIdle {
            assertEquals(MovieRecommendationTiming.FINAL_FOUR_MINUTES, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
            PlayerSettingsRepository.onProfileChanged()
            assertEquals(MovieRecommendationTiming.FINAL_FOUR_MINUTES, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
        }
        onNodeWithText("Movie Recommendation Timing").performScrollTo().performClick()
        onAllNodesWithText("IntroDB API").onLast().performClick()
        runOnIdle {
            assertEquals(MovieRecommendationTiming.INTRODB, PlayerSettingsRepository.uiState.value.movieRecommendationTiming)
        }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "playback.png"))
    }
}
