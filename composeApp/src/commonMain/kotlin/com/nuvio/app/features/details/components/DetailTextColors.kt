package com.nuvio.app.features.details.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalDetailsArtworkText = staticCompositionLocalOf { false }

@Composable
internal fun detailsSecondaryTextColor(
    fallback: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): Color = if (LocalDetailsArtworkText.current) Color(0xFFC7CDD6) else fallback
