package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class CatalogFilterControl(
    val title: String,
    val selectedKey: String,
    val options: List<NuvioDropdownOption>,
    val onSelected: (String) -> Unit,
)

@Composable
internal fun CatalogFilterBar(
    controls: List<CatalogFilterControl>,
    clearLabel: String,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        controls.forEach { control ->
            NuvioDropdownChip(
                modifier = Modifier.align(Alignment.CenterVertically),
                title = control.title,
                label = control.options.firstOrNull { it.key == control.selectedKey }?.label ?: control.title,
                selectedKey = control.selectedKey,
                options = control.options,
                onSelected = { control.onSelected(it.key) },
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterVertically).size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = clearLabel, tint = MaterialTheme.nuvio.colors.textMuted)
            }
        }
    }
}
