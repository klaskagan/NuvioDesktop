package com.nuvio.app.features.collection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.home.SeasonalTheme
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SeasonalCollectionBackdropSettings(folders: List<CollectionFolder>) {
    val seasons = folders.filter { SeasonalCollection.theme(it) != null }
    if (seasons.isEmpty()) return
    var selectedId by remember { mutableStateOf(seasons.firstOrNull {
        it.seasonalThemeId == SeasonalTheme.forDate(CurrentDateProvider.todayIsoDate()).id
    }?.id ?: seasons.first().id) }
    val selected = seasons.firstOrNull { it.id == selectedId } ?: seasons.first()
    NuvioSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.seasonal_backgrounds), style = MaterialTheme.typography.titleMedium)
            NuvioDropdownChip(title = stringResource(Res.string.seasonal_background_season), label = selected.title,
                selectedKey = selected.id, options = seasons.map { NuvioDropdownOption(it.id, it.title) },
                onSelected = { selectedId = it.key })
            SeasonalBackdropEditor(selected) { CollectionEditorRepository.updateSeasonalBackdrop(selected.id, it) }
        }
    }
}

@Composable
internal fun SeasonalBackdropEditor(folder: CollectionFolder, onUrlChange: (String) -> Unit) {
    val theme = SeasonalCollection.theme(folder) ?: return
    val original = folder.heroBackdropUrl.isNullOrBlank()
    val bundled = theme.backgroundArtwork
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium)
            .testTag("seasonal_backdrop_preview")) {
            if (original && bundled != null) Image(painterResource(bundled), folder.title,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else NuvioAsyncImage(folder.heroBackdropUrl, folder.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(stringResource(if (original) Res.string.seasonal_original_artwork else Res.string.seasonal_custom_artwork),
            style = MaterialTheme.typography.labelLarge)
        Text(stringResource(Res.string.seasonal_background_help), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        NuvioInputField(value = folder.heroBackdropUrl.orEmpty(), onValueChange = onUrlChange,
            placeholder = stringResource(Res.string.seasonal_backdrop_url))
        TextButton(onClick = { onUrlChange("") }, enabled = !original) {
            Text(stringResource(Res.string.seasonal_restore_artwork))
        }
    }
}
