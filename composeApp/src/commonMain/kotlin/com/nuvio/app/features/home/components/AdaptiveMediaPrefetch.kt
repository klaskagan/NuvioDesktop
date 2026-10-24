package com.nuvio.app.features.home.components

import androidx.compose.runtime.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.TimeSource

internal fun mediaPrefetchNowMillis(): Long = MediaPrefetchClock.origin.elapsedNow().inWholeMilliseconds
private object MediaPrefetchClock { val origin = TimeSource.Monotonic.markNow() }

/** Shared with pagination: row counts, never raw grid cell counts. */
@Stable
internal class MediaPrefetchWindow(private val maximumAheadRows: Int = 6) {
    var aheadRows by mutableIntStateOf(2)
        private set
    var direction by mutableIntStateOf(1)
        private set
    private var previousPosition: Double? = null
    private var previousTime = 0L
    private var speed = 0.0

    fun observe(position: Double, nowMillis: Long, preparationMillis: Long, unitsPerRow: Int = 1) {
        val previous = previousPosition
        if (previous != null && nowMillis > previousTime) {
            val delta = position - previous
            val elapsed = nowMillis - previousTime
            if (abs(delta) > .001) {
                val nextDirection = if (delta > 0) 1 else -1
                val measured = (abs(delta) * 1000 / elapsed / unitsPerRow.coerceAtLeast(1)).coerceAtMost(30.0)
                speed = if (direction != nextDirection || elapsed > 750) measured else .65 * speed + .35 * measured
                direction = nextDirection
                previousPosition = position
                previousTime = nowMillis
            } else if (elapsed >= 250) {
                speed = 0.0
                previousTime = nowMillis
            }
        } else if (previous == null) {
            previousPosition = position
            previousTime = nowMillis
        }
        aheadRows = ceil(speed * preparationMillis.coerceIn(100, 12_000) / 1000.0 + 1).toInt().coerceIn(2, maximumAheadRows.coerceIn(2, 6))
    }
}

@Composable
internal fun rememberMediaPrefetchWindow(identity: Any): MediaPrefetchWindow {
    val profile by com.nuvio.app.features.profiles.ProfileRepository.state.collectAsState()
    val settings by com.nuvio.app.features.tmdb.TmdbSettingsRepository.uiState.collectAsState()
    return remember(identity, profile.activeProfile?.id, settings.language, settings.apiKey) { MediaPrefetchWindow() }
}

internal fun shouldPrefetchMediaPage(totalRows: Int, lastVisibleRow: Int, aheadRows: Int): Boolean =
    totalRows > 0 && lastVisibleRow >= 0 && lastVisibleRow + aheadRows.coerceIn(2, 6) + 1 >= totalRows - 1

/** Visible work first, then the direction of travel, then one trailing visual row. */
internal fun adaptivePrefetchRowKeys(
    ordered: List<String>, visible: List<String>, aheadRows: Int, direction: Int, unitsPerRow: Int = 1,
): List<String> {
    if (ordered.isEmpty()) return emptyList()
    val index = ordered.withIndex().associate { it.value to it.index }
    val visibleIndices = visible.mapNotNull(index::get).distinct().sorted().ifEmpty { listOf(0) }
    val first = visibleIndices.first()
    val last = visibleIndices.last()
    val ahead = aheadRows.coerceIn(2, 6) * unitsPerRow.coerceAtLeast(1)
    val behind = unitsPerRow.coerceAtLeast(1)
    val approaching = if (direction >= 0) (last + 1..minOf(last + ahead, ordered.lastIndex)).toList()
        else (first - 1 downTo maxOf(0, first - ahead)).toList()
    val trailing = if (direction >= 0) (first - 1 downTo maxOf(0, first - behind)).toList()
        else (last + 1..minOf(last + behind, ordered.lastIndex)).toList()
    return (visibleIndices + approaching + trailing).map(ordered::get)
}
