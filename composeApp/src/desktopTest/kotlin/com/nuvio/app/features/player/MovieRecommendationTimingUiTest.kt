package com.nuvio.app.features.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.settings.SettingsPage
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
                LazyColumn {
                    playbackSettingsContent(
                        isTablet = true,
                        page = SettingsPage.PlaybackInterface,
                        onPageChange = {},
                    )
                }
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
