package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.nuvio.app.core.ui.LocalScreenActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private data class MediaPageInputs(
    val active: Boolean,
    val canLoadMore: Boolean,
    val isLoading: Boolean,
    val totalRows: Int,
    val lastVisibleRow: Int,
    val aheadRows: Int,
    val direction: Int,
) {
    val shouldRequest: Boolean get() = active && canLoadMore && !isLoading && direction > 0 &&
        // Filtering can remove a whole provider page while its cursor still has more results.
        (totalRows == 0 || shouldPrefetchMediaPage(totalRows, lastVisibleRow, aheadRows))
}

/** Page retrieval follows the same adaptive visual-row window as image preparation. */
@Composable
internal fun MediaPagePrefetchEffect(
    rows: List<ArtworkPrefetchRow>,
    window: MediaPrefetchWindow,
    viewport: Any,
    identity: Any,
    canLoadMore: Boolean,
    isLoading: Boolean,
    enabled: Boolean = true,
    onLoadMore: () -> Unit,
    visibleKeys: () -> List<Any>,
) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentVisibleKeys by rememberUpdatedState(visibleKeys)
    val rowForKey = remember(rows) { rows.flatMapIndexed { index, row -> row.itemKeys.map { it to index } }.toMap() }
    val lastVisibleRow by remember(viewport, rowForKey) {
        derivedStateOf { currentVisibleKeys().mapNotNull(rowForKey::get).maxOrNull() ?: -1 }
    }
    // Publish guards, viewport and adaptive changes together after composition. Observing the window
    // directly from an old effect can race a simultaneous loading/inactive update before recomposition.
    val inputs by rememberUpdatedState(MediaPageInputs(
        active = LocalScreenActive.current && enabled,
        canLoadMore = canLoadMore,
        isLoading = isLoading,
        totalRows = rows.size,
        lastVisibleRow = lastVisibleRow,
        aheadRows = window.aheadRows,
        direction = window.direction,
    ))
    LaunchedEffect(viewport, identity) {
        snapshotFlow { inputs.shouldRequest }
            .distinctUntilChanged().filter { it }.collect { currentLoadMore() }
    }
}
