package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(NuvioTokens.Radius.md),
        color = if (selected) tokens.colors.overlaySelected else Color.Transparent,
        contentColor = when {
            !enabled -> tokens.colors.textDisabled
            selected -> tokens.colors.textPrimary
            else -> tokens.colors.textMuted
        },
        border = BorderStroke(
            width = tokens.borders.thin,
            color = when {
                selected -> tokens.colors.borderSelected
                enabled -> tokens.colors.borderStrong
                else -> tokens.colors.borderDefault
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = NuvioTokens.Space.s12,
                vertical = tokens.components.chipVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailingIcon?.invoke()
        }
    }
}
