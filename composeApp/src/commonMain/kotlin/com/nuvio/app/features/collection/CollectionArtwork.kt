package com.nuvio.app.features.collection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.features.catalog.CatalogHeroBackdrop

/** Only the requested community collections opt into the artwork presentation. */
internal fun Collection.usesConfiguredArtwork(): Boolean {
    if (!id.endsWith("-community")) return false
    return title.equals("By Decade", true) || title.equals("Studios", true) ||
        (title.replace("ㅤ", "").isBlank() && folders.map { it.title }.containsAll(listOf("Trakt", "Netflix", "Prime Video")))
}

internal fun Collection.artworkFolder(folderId: String): CollectionFolder? =
    folders.firstOrNull { it.id == folderId && !it.heroBackdropUrl.isNullOrBlank() }?.takeIf { usesConfiguredArtwork() }

@Composable
internal fun CollectionBackdrop(folder: CollectionFolder, minHeight: Dp, measuredHeightPx: Int, scrollTranslation: (Float) -> Float) {
    CatalogHeroBackdrop(minHeight, measuredHeightPx, scrollTranslation, "collection_background_${folder.id}") {
        NuvioAsyncImage(folder.heroBackdropUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun CollectionIntroduction(folder: CollectionFolder, minHeight: Dp, modifier: Modifier = Modifier, subtitle: String = "") {
    Column(modifier.fillMaxWidth().heightIn(min = minHeight).testTag("collection_intro_${folder.id}")
        .windowInsetsPadding(WindowInsets.statusBars).padding(top = 100.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.Bottom) {
        var logoFailed by remember(folder.id, folder.titleLogoUrl) { mutableStateOf(false) }
        if (!folder.titleLogoUrl.isNullOrBlank() && !logoFailed) {
            NuvioAsyncImage(folder.titleLogoUrl, folder.title,
                Modifier.widthIn(max = 300.dp).fillMaxWidth().height(88.dp).padding(bottom = 12.dp),
                contentScale = ContentScale.Fit, alignment = androidx.compose.ui.Alignment.CenterStart,
                onError = { logoFailed = true })
        } else {
            Text(folder.title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
