package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.downloads.DownloadsFolder
import com.nuvio.app.features.downloads.rememberDownloadsFolderControls
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.isIos
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.playbackSettingsContent(
    isTablet: Boolean,
    page: SettingsPage,
    onPageChange: (SettingsPage) -> Unit,
) {
    item {
        val settings by remember {
            PlayerSettingsRepository.ensureLoaded()
            PlayerSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        when (page) {
            SettingsPage.Playback -> {
                val sections = visiblePlaybackSections(isIos, settings.androidPlaybackEngine, P2pSettingsRepository.isVisible)
                SettingsGroup(isTablet = isTablet) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(section.page.titleRes),
                            description = stringResource(section.descriptionRes),
                            isTablet = isTablet,
                            onClick = { onPageChange(section.page) },
                        )
                    }
                }
                if (DownloadsFolder.isSupported && AppFeaturePolicy.downloadsEnabled) {
                    Spacer(Modifier.height(18.dp))
                    PlaybackDownloadFolderSettings(isTablet)
                }
            }
            SettingsPage.PlaybackPlayer -> PlaybackPlayerSettings(isTablet, settings)
            SettingsPage.PlaybackStreamSelection -> PlaybackStreamSettings(isTablet, settings)
            SettingsPage.PlaybackUpNext -> PlaybackUpNextSettings(isTablet, settings)
            SettingsPage.PlaybackSkipSegments -> PlaybackSkipSettings(isTablet, settings)
            SettingsPage.PlaybackInterface -> PlaybackInterfaceSettings(isTablet, settings)
            SettingsPage.PlaybackAudio -> PlaybackAudioSettings(isTablet, settings)
            SettingsPage.PlaybackSubtitles -> PlaybackSubtitleSettings(isTablet, settings)
            SettingsPage.PlaybackVideo -> PlaybackVideoSettings(isTablet, settings)
            SettingsPage.PlaybackBuffer -> if (!isIos) PlaybackBufferSettings(isTablet, settings)
            SettingsPage.PlaybackP2p -> if (P2pSettingsRepository.isVisible) PlaybackP2pSettings(isTablet)
            else -> Unit
        }
    }
}

/** Desktop: where new downloads are saved (the Downloads page's folder menu sets the same value). */
@Composable
private fun PlaybackDownloadFolderSettings(isTablet: Boolean) {
    val folder = rememberDownloadsFolderControls()
    SettingsSection(title = stringResource(Res.string.downloads_folder_settings_section), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.downloads_folder_title),
                description = folder.displayPath,
                icon = Icons.Outlined.Folder,
                isTablet = isTablet,
                trailingContent = if (folder.isCustom) {
                    {
                        TextButton(onClick = folder.reset, modifier = Modifier.testTag("settings-download-folder-reset")) {
                            Text(stringResource(Res.string.downloads_folder_reset))
                        }
                    }
                } else {
                    null
                },
                onClick = folder.change,
            )
        }
    }
}
