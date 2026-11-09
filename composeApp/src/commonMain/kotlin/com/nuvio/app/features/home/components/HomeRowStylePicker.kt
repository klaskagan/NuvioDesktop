package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.supportsHomeRowStyle
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRowStyle
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun homeRowStyleLabel(style: HomeRowStyle): String = stringResource(
    when (style) {
        HomeRowStyle.Automatic -> Res.string.home_row_style_automatic
        HomeRowStyle.NumberedRanking -> Res.string.home_row_style_numbered_ranking
    },
)

@Composable
internal fun HomeRowStylePicker(shelfKey: String, onDismiss: () -> Unit) {
    val state by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    if (!supportsHomeRowStyle(shelfKey)) {
        LaunchedEffect(shelfKey) { onDismiss() }
        return
    }
    val currentStyle = state.rowStyles[shelfKey] ?: HomeRowStyle.Automatic

    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.home_row_style)) {
        val selectedFocus = remember { FocusRequester() }
        HomeRowStyle.entries.forEach { style ->
            val selected = currentStyle == style
            DialogOption(
                text = homeRowStyleLabel(style),
                selected = selected,
                modifier = if (selected) Modifier.focusRequester(selectedFocus) else Modifier,
                onClick = {
                    HomeCatalogSettingsRepository.setRowStyle(shelfKey, style)
                    onDismiss()
                },
            )
        }
        LaunchedEffect(shelfKey) { selectedFocus.requestFocus() }
    }
}
