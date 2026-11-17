package com.nuvio.app.features.trailer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.CornerTrailerStyle
import com.nuvio.app.features.details.HeroTrailerLayout
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.settings.metaScreenSettingsContent
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TrailerPlayerSettingsUiTest {
    @Test fun actualSettingsChipsPersistBothChoicesAndShowDefaultSelection() = runDesktopComposeUiTest(width = 1000, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.resetToDefaults()
        setContent {
            AppEnvironment {
                val settings by MetaScreenSettingsRepository.uiState.collectAsState()
                LazyColumn { metaScreenSettingsContent(isTablet = true, uiState = settings) }
            }
        }
        onNodeWithText("YouTube").performScrollTo().assertIsSelected()
        onNodeWithText("Native").performClick().assertIsSelected()
        runOnIdle {
            MetaScreenSettingsRepository.onProfileChanged()
            assertEquals(TrailerPlayerBackend.Native, MetaScreenSettingsRepository.uiState.value.trailerPlayerBackend)
        }
        onNodeWithText("Native").assertIsSelected()
        onNodeWithText("YouTube").performClick().assertIsSelected()
        runOnIdle {
            MetaScreenSettingsRepository.onProfileChanged()
            assertEquals(TrailerPlayerBackend.YouTube, MetaScreenSettingsRepository.uiState.value.trailerPlayerBackend)
        }
    }

    @Test fun cornerStylePersistsAcrossLayoutChangesAndProfileReloadUntilReset() = runDesktopComposeUiTest(width = 1000, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.resetToDefaults()
        setContent {
            AppEnvironment {
                val settings by MetaScreenSettingsRepository.uiState.collectAsState()
                LazyColumn { metaScreenSettingsContent(isTablet = true, uiState = settings) }
            }
        }
        onNodeWithText("Backdrop").performScrollTo().assertIsSelected()
        onNodeWithText("Corner trailer style").assertDoesNotExist()
        onNodeWithText("Corner").performClick().assertIsSelected()
        onNodeWithText("TV-style hero").performScrollTo().assertIsSelected()
        onNodeWithText("Framed trailer").performClick().assertIsSelected()
        onNodeWithText("Clean split header").performClick().assertIsSelected()
        onNodeWithText("Backdrop").performScrollTo().performClick().assertIsSelected()
        onNodeWithText("Corner trailer style").assertDoesNotExist()
        runOnIdle {
            MetaScreenSettingsRepository.onProfileChanged()
            assertEquals(HeroTrailerLayout.Backdrop, MetaScreenSettingsRepository.uiState.value.heroTrailerLayout)
            assertEquals(CornerTrailerStyle.CleanSplit, MetaScreenSettingsRepository.uiState.value.cornerTrailerStyle)
        }
        onNodeWithText("Corner").performScrollTo().performClick().assertIsSelected()
        onNodeWithText("Clean split header").performScrollTo().assertIsSelected()
        runOnIdle {
            MetaScreenSettingsRepository.onProfileChanged()
            assertEquals(HeroTrailerLayout.Corner, MetaScreenSettingsRepository.uiState.value.heroTrailerLayout)
            MetaScreenSettingsRepository.resetToDefaults()
            assertEquals(CornerTrailerStyle.TvStyle, MetaScreenSettingsRepository.uiState.value.cornerTrailerStyle)
            assertEquals(HeroTrailerLayout.Backdrop, MetaScreenSettingsRepository.uiState.value.heroTrailerLayout)
        }
        onNodeWithText("Backdrop").performScrollTo().assertIsSelected()
    }

}
