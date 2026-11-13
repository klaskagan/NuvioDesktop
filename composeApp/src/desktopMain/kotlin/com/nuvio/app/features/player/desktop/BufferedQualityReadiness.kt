package com.nuvio.app.features.player.desktop

import javax.swing.SwingUtilities
import kotlin.math.abs

internal fun bufferedQualityTarget(positionMs: Long, durationMs: Long, seekable: Boolean): Long = when {
    !seekable -> 0L
    durationMs > 0 -> positionMs.coerceAtMost((durationMs - 1000).coerceAtLeast(0))
    else -> positionMs
}

internal fun bufferedQualityReady(
    positionMs: Long,
    targetMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    frameReady: Boolean,
    seeking: Boolean,
    loading: Boolean,
): Boolean {
    if (!frameReady || seeking || loading || abs(positionMs - targetMs) > 500) return false
    val remaining = (durationMs - targetMs).coerceAtLeast(0)
    val required = if (durationMs > 0) minOf(2_000L, remaining) else 2_000L
    return bufferedPositionMs - targetMs >= (required - 100).coerceAtLeast(0)
}

internal fun <T> qualityOnEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    val result = java.util.concurrent.atomic.AtomicReference<Result<T>>()
    SwingUtilities.invokeAndWait { result.set(runCatching(block)) }
    return result.get().getOrThrow()
}
