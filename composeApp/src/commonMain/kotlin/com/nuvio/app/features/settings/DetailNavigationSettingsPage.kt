package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_detail_navigation
import nuvio.composeapp.generated.resources.settings_detail_navigation_auto_hide
import nuvio.composeapp.generated.resources.settings_detail_navigation_auto_hide_description
import nuvio.composeapp.generated.resources.settings_detail_navigation_behavior
import nuvio.composeapp.generated.resources.settings_detail_navigation_description
import nuvio.composeapp.generated.resources.settings_detail_navigation_hide_delay
import nuvio.composeapp.generated.resources.settings_detail_navigation_hide_delay_description
import nuvio.composeapp.generated.resources.settings_detail_navigation_hide_delay_value
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal const val DefaultDetailNavigationHideDelaySeconds = 3
internal const val MinDetailNavigationHideDelaySeconds = 1
internal const val MaxDetailNavigationHideDelaySeconds = 15

internal fun LazyListScope.detailNavigationSettingsContent(isTablet: Boolean) {
    item {
        val navigationEnabled by ThemeSettingsRepository.detailNavigationEnabled.collectAsStateWithLifecycle()
        val autoHide by ThemeSettingsRepository.detailNavigationAutoHide.collectAsStateWithLifecycle()
        val delaySeconds by ThemeSettingsRepository.detailNavigationHideDelaySeconds.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.settings_detail_navigation_behavior),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_detail_navigation),
                    description = stringResource(Res.string.settings_detail_navigation_description),
                    checked = navigationEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ThemeSettingsRepository::setDetailNavigationEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_detail_navigation_auto_hide),
                    description = stringResource(Res.string.settings_detail_navigation_auto_hide_description),
                    checked = autoHide,
                    enabled = navigationEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ThemeSettingsRepository::setDetailNavigationAutoHide,
                )
                SettingsGroupDivider(isTablet = isTablet)
                DetailNavigationHideDelaySlider(
                    isTablet = isTablet,
                    enabled = navigationEnabled && autoHide,
                    delaySeconds = delaySeconds,
                )
            }
        }
    }
}

@Composable
private fun DetailNavigationHideDelaySlider(isTablet: Boolean, enabled: Boolean, delaySeconds: Int) {
    var sliderValue by remember(delaySeconds) { mutableFloatStateOf(delaySeconds.toFloat()) }
    val label = stringResource(Res.string.settings_detail_navigation_hide_delay)
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            ValueBox(text = stringResource(Res.string.settings_detail_navigation_hide_delay_value, sliderValue.roundToInt()))
        }
        Text(
            text = stringResource(Res.string.settings_detail_navigation_hide_delay_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it.roundToInt().toFloat() },
            onValueChangeFinished = { ThemeSettingsRepository.setDetailNavigationHideDelaySeconds(sliderValue.roundToInt()) },
            enabled = enabled,
            valueRange = MinDetailNavigationHideDelaySeconds.toFloat()..MaxDetailNavigationHideDelaySeconds.toFloat(),
            steps = MaxDetailNavigationHideDelaySeconds - MinDetailNavigationHideDelaySeconds - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
