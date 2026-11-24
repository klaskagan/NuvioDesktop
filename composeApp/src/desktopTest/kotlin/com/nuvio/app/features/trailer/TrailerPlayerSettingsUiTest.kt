package com.nuvio.app.features.trailer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
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
}
