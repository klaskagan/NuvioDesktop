package com.nuvio.app.features.home.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun artworkFixedGridColumns(widthPx: Int, padding: Dp, cellWidth: Dp): Int =
    with(LocalDensity.current) {
        val available = (widthPx - 2 * padding.roundToPx()).coerceAtLeast(0)
        val spacing = 12.dp.roundToPx()
        // GridCells.FixedSize rounds each dimension before integer division.
        ((available + spacing) / (cellWidth.roundToPx() + spacing).coerceAtLeast(1)).coerceAtLeast(1)
    }

/** Requests the next page once the last visible item is within [threshold] items of the end. */
@Composable
internal fun LoadMoreOnScrollEffect(
    state: LazyGridState,
    canLoadMore: Boolean,
    isLoading: Boolean,
    enabled: Boolean = true,
    threshold: Int = 12,
    onLoadMore: () -> Unit,
) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, canLoadMore, isLoading, enabled) {
        if (!enabled || !canLoadMore || isLoading) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 1 - threshold
        }.distinctUntilChanged().filter { it }.collect { currentLoadMore() }
    }
}

@Composable
internal fun LoadMoreOnScrollEffect(
    state: LazyListState,
    canLoadMore: Boolean,
    isLoading: Boolean,
    enabled: Boolean = true,
    threshold: Int = 4,
    onLoadMore: () -> Unit,
) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, canLoadMore, isLoading, enabled) {
        if (!enabled || !canLoadMore || isLoading) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 1 - threshold
        }.distinctUntilChanged().filter { it }.collect { currentLoadMore() }
    }
}
