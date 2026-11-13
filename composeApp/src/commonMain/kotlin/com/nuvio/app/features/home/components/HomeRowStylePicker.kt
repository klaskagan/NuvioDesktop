package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
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
internal fun homeRowStyleLabel(style: HomeRowStyle): String =
    if (style.isLandscapeSize) {
        stringResource(
            Res.string.home_row_style_landscape_size,
            stringResource(Res.string.home_row_style_landscape),
            landscapeSizeLabel(style),
        )
    } else {
        stringResource(
            when (style) {
                HomeRowStyle.Automatic -> Res.string.home_row_style_automatic
                HomeRowStyle.Posters -> Res.string.home_row_style_posters
                HomeRowStyle.BannerWithTitle -> Res.string.home_row_style_banner_with_title
                HomeRowStyle.NumberedRanking -> Res.string.home_row_style_numbered_ranking
                HomeRowStyle.Round -> Res.string.home_row_style_round
                else -> error("Landscape sizes have their own labels")
            },
        )
    }

@Composable
private fun landscapeSizeLabel(style: HomeRowStyle): String = stringResource(
    when (style) {
        HomeRowStyle.LandscapeSmall -> Res.string.home_row_style_landscape_small
        HomeRowStyle.LandscapeMedium -> Res.string.home_row_style_landscape_medium
        HomeRowStyle.LandscapeLarge -> Res.string.home_row_style_landscape_large
        else -> error("Expected a Landscape size")
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

    var pickingSize by remember(shelfKey) { mutableStateOf(false) }
    val selectedStyle = when {
        pickingSize && currentStyle.isLandscapeSize -> currentStyle
        pickingSize || currentStyle.isLandscapeSize -> HomeRowStyle.LandscapeMedium
        else -> currentStyle
    }
    val options = HomeRowStyle.entries.filter {
        if (pickingSize) it.isLandscapeSize else !it.isLandscapeSize || it == HomeRowStyle.LandscapeMedium
    }
    val landscapeLabel = stringResource(Res.string.home_row_style_landscape)
    val title = if (pickingSize) landscapeLabel else stringResource(Res.string.home_row_style)
    DialogSurface(onDismissRequest = onDismiss, title = title) {
        val selectedFocus = remember { FocusRequester() }
        Column(
            modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { style ->
                val selected = selectedStyle == style
                DialogOption(
                    text = when {
                        pickingSize -> landscapeSizeLabel(style)
                        style.isLandscapeSize -> landscapeLabel
                        else -> homeRowStyleLabel(style)
                    },
                    selected = selected,
                    modifier = if (selected) Modifier.focusRequester(selectedFocus) else Modifier,
                    onClick = {
                        if (!pickingSize && style.isLandscapeSize) {
                            pickingSize = true
                        } else {
                            HomeCatalogSettingsRepository.setRowStyle(shelfKey, style)
                            onDismiss()
                        }
                    },
                )
            }
        }
        if (pickingSize) {
            DialogButton(
                text = stringResource(Res.string.action_back),
                onClick = { pickingSize = false },
            )
        }
        LaunchedEffect(shelfKey, pickingSize) { selectedFocus.requestFocus() }
    }
}
