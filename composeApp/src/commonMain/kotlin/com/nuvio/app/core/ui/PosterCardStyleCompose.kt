package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

internal val LocalPosterCardStyleOverride = androidx.compose.runtime.staticCompositionLocalOf<PosterCardStyleUiState?> { null }

@Composable
internal fun rememberPosterCardStyleUiState(): PosterCardStyleUiState {
    PosterCardStyleRepository.ensureLoaded()
    val uiState by PosterCardStyleRepository.uiState.collectAsState()
    return LocalPosterCardStyleOverride.current ?: uiState
}