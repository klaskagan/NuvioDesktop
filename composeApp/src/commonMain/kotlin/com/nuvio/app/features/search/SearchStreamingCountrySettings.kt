package com.nuvio.app.features.search

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.settings.SettingsNavigationRow
import kotlinx.coroutines.CancellationException
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.search_country_automatic
import nuvio.composeapp.generated.resources.search_discovery_retry
import nuvio.composeapp.generated.resources.search_streaming_country
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchStreamingCountrySettings(isTablet: Boolean) {
    val selected by SearchStreamingCountry.override.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var regions by remember { mutableStateOf<Map<String, String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val title = stringResource(Res.string.search_streaming_country)
    val automatic = stringResource(Res.string.search_country_automatic)
    LaunchedEffect(open, selected, attempt) {
        if ((!open && selected == null) || regions != null) return@LaunchedEffect
        failed = false
        try {
            regions = SearchStreamingCountry.regions()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }
    SettingsNavigationRow(
        title = title,
        description = selected?.let { regions?.get(it) ?: it } ?: automatic,
        isTablet = isTablet,
        onClick = { open = true },
    )
    if (open) {
        DialogSurface(title = title, onDismissRequest = { open = false }) {
            DialogOption(text = automatic, selected = selected == null, onClick = {
                SearchStreamingCountry.set(null)
                open = false
            })
            val countries = regions
            when {
                countries != null -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(countries.entries.sortedBy { it.value }, key = { it.key }) { (code, name) ->
                        DialogOption(text = name, selected = selected == code, onClick = {
                            SearchStreamingCountry.set(code)
                            open = false
                        })
                    }
                }
                failed -> TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.search_discovery_retry)) }
                else -> NuvioLoadingIndicator()
            }
        }
    }
}
