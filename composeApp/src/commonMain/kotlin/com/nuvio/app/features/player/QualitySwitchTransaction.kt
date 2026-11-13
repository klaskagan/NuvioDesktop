package com.nuvio.app.features.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Immutable checkpoint owned by the playback adapter, including source/session and track state. */
internal interface QualitySwitchPlayback<Checkpoint> {
    suspend fun capture(): Checkpoint
    /** Load paused, restore tracks/settings and position, then restore the captured play/pause state. */
    suspend fun loadAndRestore(candidate: PlayerQualityChoice, checkpoint: Checkpoint)
    /** Must only return after successful load, seek and track verification. */
    suspend fun verify(candidate: PlayerQualityChoice, checkpoint: Checkpoint)
    suspend fun rollback(checkpoint: Checkpoint)
    suspend fun commit(candidate: PlayerQualityChoice)
}

internal sealed interface QualitySwitchResult {
    data object Committed : QualitySwitchResult
    data class Failed(val cause: Throwable, val rollbackFailure: Throwable? = null) : QualitySwitchResult
    data class Rejected(val reason: String) : QualitySwitchResult
}

/** Serialize replacement and rollback: a previous cancellation cannot restore over a newer request. */
internal class QualitySwitchTransaction<Checkpoint>(
    private val playback: QualitySwitchPlayback<Checkpoint>,
    private val timeoutMs: Long = 30_000L,
) {
    private val mutex = Mutex()

    suspend fun switch(candidate: PlayerQualityChoice): QualitySwitchResult = mutex.withLock {
        if (candidate.disabledReason != null) {
            return@withLock QualitySwitchResult.Rejected(candidate.disabledReason)
        }
        val checkpoint = try { playback.capture() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { return@withLock QualitySwitchResult.Failed(failure) }
        try {
            withTimeout(timeoutMs) {
                playback.loadAndRestore(candidate, checkpoint)
                playback.verify(candidate, checkpoint)
                playback.commit(candidate)
            }
            QualitySwitchResult.Committed
        } catch (failure: Throwable) {
            val restoreFailure = withContext(NonCancellable) {
                runCatching { withTimeout(timeoutMs) { playback.rollback(checkpoint) } }.exceptionOrNull()
            }
            // Propagate external cancellation after rollback; timeout is a retryable failure.
            if (failure is CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
            QualitySwitchResult.Failed(failure, restoreFailure)
        }
    }
}
