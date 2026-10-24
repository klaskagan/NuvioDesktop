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

/** Stable provider; completing one asset invalidates its card, rather than every shelf. */
@Stable
internal class PreparedArtworkStore(private val parent: PreparedArtworkStore? = null) {
    private val slots = mutableStateMapOf<String, MutableState<LandscapeMediaArtworkState>>()
    // Membership is supplied by the parent before composing children. Result slots remain observable.
    private var managedKeys: Set<String> = emptySet()
    private val pending = LandscapeMediaArtworkState(isLoading = true)
    private val readyOrder = ArrayDeque<String>()
    private val active = mutableSetOf<String>()
    operator fun get(key: String): LandscapeMediaArtworkState? = slots[key]?.value ?: parent?.get(key) ?: pending.takeIf { key in managedKeys }
    // Known offscreen items wait for the viewport scheduler rather than racing it with a card lookup.
    fun manage(keys: Set<String>) { managedKeys = keys }
    fun manages(key: String): Boolean = key in managedKeys || parent?.manages(key) == true
    fun plan(keys: List<String>) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            val keep = keys.toSet() + active + readyOrder.toSet()
            slots.keys.toList().filterNot(keep::contains).forEach(slots::remove)
            keys.forEach { key -> slots.getOrPut(key) { mutableStateOf(LandscapeMediaArtworkState(isLoading = true)) } }
        }
    }
    fun begin(key: String) { active.add(key) }
    fun complete(key: String, result: LandscapeMediaArtworkState) {
        active.remove(key)
        slots.getOrPut(key) { mutableStateOf(result) }.value = result
        readyOrder.remove(key); readyOrder.addLast(key)
        while (readyOrder.size > 128) slots.remove(readyOrder.removeFirst())
    }
}
internal val LocalPreparedLandscapeArtwork = staticCompositionLocalOf { PreparedArtworkStore() }
internal data class LandscapeMediaArtworkState(val artwork: TmdbLandscapeArtwork? = null, val isLoading: Boolean = false, val logoFailed: Boolean = false)

internal class LandscapeRowRevealState(private val expectedKeys: Set<String>? = null) {
    val ready = mutableStateMapOf<String, Boolean>()
    var revealed by mutableStateOf(false)
    fun report(key: String, value: Boolean) {
        if (ready[key] != value) ready[key] = value
        if (!revealed && expectedKeys != null && expectedKeys.all { ready[it] == true }) revealed = true
    }
}
internal val LocalLandscapeRowReveal = staticCompositionLocalOf<LandscapeRowRevealState?> { null }

@Composable
internal fun rememberLandscapeMediaArtwork(id: String, type: String, enabled: Boolean): LandscapeMediaArtworkState {
    val settings by remember {
        TmdbSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val loader = LocalLandscapeArtworkLoader.current
    val preparedStore = LocalPreparedLandscapeArtwork.current
    val prepared = preparedStore["$type:$id"].takeIf { enabled }
    // Reset immediately on identity/language/key changes; a late old lookup must never repaint a new card.
    var artwork by remember(id, type, settings.language, settings.apiKey, enabled, loader) {
        mutableStateOf(LandscapeMediaArtworkState(isLoading = enabled))
    }
    LaunchedEffect(id, type, settings.language, settings.apiKey, enabled, loader, prepared, preparedStore) {
        if (enabled && preparedStore["$type:$id"] == null) {
            val result = try { loader(id, type, settings.language) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            artwork = LandscapeMediaArtworkState(artwork = result)
        }
    }
    return prepared ?: artwork
}
