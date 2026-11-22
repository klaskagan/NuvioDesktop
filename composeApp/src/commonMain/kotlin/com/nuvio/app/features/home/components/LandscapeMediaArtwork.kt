package com.nuvio.app.features.home.components

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.CancellationException

/** A source exclusion, not a media exclusion: the same show elsewhere still gets title artwork. */
internal fun preservesOriginalLandscapeArtwork(title: String, target: CatalogTarget): Boolean {
    val catalogId = when (target) {
        is CatalogTarget.Addon -> target.catalogId
        is CatalogTarget.CollectionSource -> target.sourceKey
        else -> ""
    }
    fun String.normalized() = lowercase().filter(Char::isLetterOrDigit)
    return catalogId.normalized().contains("airingnext") || title.normalized().startsWith("airingnext")
}

internal fun isTitleArtworkMedia(type: String): Boolean = type.lowercase() in setOf("movie", "series", "tv", "show")

/** Test fixtures can supply artwork without invoking metadata services or changing account state. */
internal val LocalLandscapeArtworkLoader = staticCompositionLocalOf<suspend (String, String, String) -> TmdbLandscapeArtwork?> {
    { id, type, language -> TmdbMetadataService.fetchLandscapeArtwork(id, type, language) }
}

internal data class LandscapeMediaArtworkState(val artwork: TmdbLandscapeArtwork? = null, val isLoading: Boolean = false, val logoFailed: Boolean = false)

@Composable
internal fun rememberLandscapeMediaArtwork(id: String, type: String, enabled: Boolean): LandscapeMediaArtworkState {
    val settings by remember {
        TmdbSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val loader = LocalLandscapeArtworkLoader.current
    // Reset immediately on identity/language/key changes; a late old lookup must never repaint a new card.
    var artwork by remember(id, type, settings.language, settings.apiKey, enabled, loader) {
        mutableStateOf(LandscapeMediaArtworkState(isLoading = enabled))
    }
    LaunchedEffect(id, type, settings.language, settings.apiKey, enabled, loader) {
        if (enabled) {
            val result = try { loader(id, type, settings.language) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            artwork = LandscapeMediaArtworkState(artwork = result)
        }
    }
    return artwork
}
