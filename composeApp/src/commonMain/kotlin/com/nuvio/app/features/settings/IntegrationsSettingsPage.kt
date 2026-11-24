package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.settings_integrations_mdblist_description
import nuvio.composeapp.generated.resources.settings_integrations_debrid_description
import nuvio.composeapp.generated.resources.settings_integrations_section_title
import nuvio.composeapp.generated.resources.settings_integrations_tmdb_description
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.isDesktop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import nuvio.composeapp.generated.resources.settings_kinopoisk_title
import nuvio.composeapp.generated.resources.settings_kinopoisk_description
import nuvio.composeapp.generated.resources.action_save

internal fun LazyListScope.integrationsContent(
    isTablet: Boolean,
    onTmdbClick: () -> Unit,
    onMdbListClick: () -> Unit,
    onDebridClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_tmdb_enrichment),
                    description = stringResource(Res.string.settings_integrations_tmdb_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Tmdb),
                    isTablet = isTablet,
                    onClick = onTmdbClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_mdblist_ratings),
                    description = stringResource(Res.string.settings_integrations_mdblist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.MdbList),
                    isTablet = isTablet,
                    onClick = onMdbListClick,
                )
                if (isDesktop) {
                    SettingsGroupDivider(isTablet = isTablet)
                    KinopoiskKeyRow(isTablet = isTablet)
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_debrid),
                    description = stringResource(Res.string.settings_integrations_debrid_description),
                    isTablet = isTablet,
                    onClick = onDebridClick,
                )
            }
        }
    }
}

/** Settings → Integrations → Kinopoisk API key, beside MDBList: the Details gallery's film stills. */
@androidx.compose.runtime.Composable
private fun KinopoiskKeyRow(isTablet: Boolean) {
    val saved by androidx.compose.runtime.remember {
        com.nuvio.app.features.gallery.KinopoiskSettingsRepository.ensureLoaded()
        com.nuvio.app.features.gallery.KinopoiskSettingsRepository.apiKey
    }.collectAsState()
    var draft by androidx.compose.runtime.remember(saved) { androidx.compose.runtime.mutableStateOf(saved.orEmpty()) }
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text(stringResource(Res.string.settings_kinopoisk_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Text(
            stringResource(Res.string.settings_kinopoisk_description),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSecretTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(Res.string.settings_kinopoisk_title),
            modifier = androidx.compose.ui.Modifier.fillMaxWidth().testTag("settings_kinopoisk_key"),
        )
        androidx.compose.material3.Button(
            onClick = { com.nuvio.app.features.gallery.KinopoiskSettingsRepository.setApiKey(draft) },
            enabled = draft.trim() != saved.orEmpty(),
        ) { androidx.compose.material3.Text(stringResource(Res.string.action_save)) }
    }
}
