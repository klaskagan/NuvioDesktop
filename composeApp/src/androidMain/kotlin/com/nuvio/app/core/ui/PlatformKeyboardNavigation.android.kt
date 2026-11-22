package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformKeyboardNavigationHost() {
    // No-op on Android — keyboard navigation is handled by the platform.
}

internal actual object DesktopNavigationPreference {
    actual val enabled: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)

    actual fun setEnabled(enabled: Boolean) = Unit
}
