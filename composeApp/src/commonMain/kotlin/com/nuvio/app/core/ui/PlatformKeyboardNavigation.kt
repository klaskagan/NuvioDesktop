package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

/**
 * Installs desktop-only keyboard affordances: arrow-key focus movement, the Escape
 * back shortcut registration point, and initial focus so the first arrow press has
 * somewhere to land.
 */
@Composable
expect fun PlatformKeyboardNavigationHost()

internal expect object DesktopNavigationPreference {
    val enabled: kotlinx.coroutines.flow.StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
}
