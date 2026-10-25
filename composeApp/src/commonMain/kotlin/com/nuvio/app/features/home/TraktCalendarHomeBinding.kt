package com.nuvio.app.features.home

import androidx.compose.runtime.*
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.trakt.*
import kotlinx.coroutines.*

@Composable
internal fun TraktCalendarHomeBinding() {
    val auth by TraktAuthRepository.uiState.collectAsState()
    val settings by HomeCatalogSettingsRepository.uiState.collectAsState()
    val calendar by TraktCalendarRepository.uiState.collectAsState()
    LaunchedEffect(auth.calendarAccountKey) {
        HomeCatalogSettingsRepository.syncCatalogs(AddonRepository.uiState.value.addons)
        HomeRepository.applyCurrentSettings()
    }
    LaunchedEffect(calendar) { HomeRepository.applyCurrentSettings() }
    val enabled = settings.items.firstOrNull { it.key == TRAKT_CALENDAR_KEY }?.enabled == true
    com.nuvio.app.core.ui.ScreenActivityEffect(auth.calendarAccountKey, enabled) { active ->
        if (!active || auth.calendarAccountKey == null || !enabled) return@ScreenActivityEffect
        while (isActive) {
            TraktCalendarRepository.load()
            delay(30_000)
        }
    }
}
