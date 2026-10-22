package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.ui.NuvioTheme
import java.io.File
import javax.imageio.ImageIO
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DetailNavigationSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previousSettings: JsonObject

    @Before fun prepareSettings() {
        ThemeSettingsRepository.ensureLoaded()
        previousSettings = ThemeSettingsStorage.exportToSyncPayload()
        ThemeSettingsRepository.setDetailNavigationEnabled(true)
        ThemeSettingsRepository.setDetailNavigationAutoHide(true)
        ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(3)
    }

    @After fun restoreSettings() {
        ThemeSettingsStorage.replaceFromSyncPayload(previousSettings)
        ThemeSettingsRepository.onProfileChanged()
    }

    @Test fun pageGroupsControlsAndShowsThreeSecondsByDefault() {
        showPage()
        assertEquals(SettingsPage.Appearance, SettingsPage.DetailNavigation.parentPage)
        compose.onNodeWithText("Auto-hide detail top bar").assertExists()
        compose.onNodeWithText("3 s").assertExists()
        compose.onNodeWithContentDescription("Hide Delay").assertIsEnabled()
        val directory = File("build/reports/detail-navigation-settings").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(directory, "page.png"))
    }

    @Test fun changingDelayPersistsAndIsIncludedInProfileSync() {
        showPage()
        compose.onNodeWithContentDescription("Hide Delay").performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithText("8 s").assertExists()
        compose.runOnIdle {
            assertEquals(8, ThemeSettingsRepository.detailNavigationHideDelaySeconds.value)
            assertEquals(8, ThemeSettingsStorage.loadDetailNavigationHideDelaySeconds())
            assertEquals(8, ThemeSettingsStorage.exportToSyncPayload().decodeSyncInt("detail_navigation_hide_delay_seconds"))
            ThemeSettingsRepository.onProfileChanged()
            assertEquals(8, ThemeSettingsRepository.detailNavigationHideDelaySeconds.value)
        }
    }

    @Test fun alwaysVisibleModeDisablesDelayAndNavigationOffDisablesBothControls() {
        showPage()
        compose.onNodeWithText("Auto-hide detail top bar").performClick()
        compose.onNodeWithContentDescription("Hide Delay").assertIsNotEnabled()
        compose.onNodeWithText("Auto-hide detail top bar").performClick()
        compose.onNodeWithContentDescription("Hide Delay").assertIsEnabled()
        compose.onNodeWithText("Navigation on detail screens", substring = false).performClick()
        compose.onNodeWithContentDescription("Hide Delay").assertIsNotEnabled()
        compose.onNodeWithText("Auto-hide detail top bar").assertIsNotEnabled()
    }

    @Test fun missingSavedDelayDefaultsToThreeAndValuesStayInRange() {
        val withoutDelay = JsonObject(ThemeSettingsStorage.exportToSyncPayload().filterKeys {
            it != "detail_navigation_hide_delay_seconds"
        })
        ThemeSettingsStorage.replaceFromSyncPayload(withoutDelay)
        ThemeSettingsRepository.onProfileChanged()
        assertEquals(3, ThemeSettingsRepository.detailNavigationHideDelaySeconds.value)
        ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(100)
        assertEquals(15, ThemeSettingsRepository.detailNavigationHideDelaySeconds.value)
        ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(-1)
        assertEquals(1, ThemeSettingsRepository.detailNavigationHideDelaySeconds.value)
    }

    private fun showPage() {
        compose.setContent {
            NuvioTheme {
                LazyColumn(
                    modifier = Modifier.size(900.dp, 650.dp).background(Color.Black),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item {
                        Text(
                            "Detail navigation",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    detailNavigationSettingsContent(isTablet = true)
                }
            }
        }
    }
}
