package com.nuvio.app.features.player.desktop

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** One decoded seek-bar thumbnail, ready for the controls page. */
internal data class SeekPreviewThumbnail(
    /** The bucket the controls page asked for (its cache key). */
    val bucketMs: Long,
    /** Where the decoded keyframe actually is. */
    val positionMs: Long,
    val jpeg: ByteArray,
) {
    fun dataUrl(): String = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
}

/** A headless decoder for one stream. Blocking; only called from the thumbnailer thread. */
internal interface SeekPreviewFrameSource {
    /** Returns the frame position (ms), [UNAVAILABLE] or [RETRY_LATER]. */
    fun frame(positionMs: Long, output: File, timeoutMs: Long): Long
    fun close()

    companion object {
        const val UNAVAILABLE = -1L
        const val RETRY_LATER = -2L
    }
}

internal class NativeSeekPreviewFrameSource private constructor(private var handle: Long) : SeekPreviewFrameSource {
    override fun frame(positionMs: Long, output: File, timeoutMs: Long): Long {
        val current = handle.takeIf { it != 0L } ?: return SeekPreviewFrameSource.UNAVAILABLE
        return NativePlayerBridge.seekThumbnailerFrame(current, positionMs, output.absolutePath, timeoutMs)
    }

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) NativePlayerBridge.seekThumbnailerDestroy(current)
    }

    companion object {
        fun open(url: String, headerLines: List<String>): SeekPreviewFrameSource? =
            NativePlayerBridge.seekThumbnailerCreate(url, headerLines.toTypedArray())
                .takeIf { it != 0L }
                ?.let(::NativeSeekPreviewFrameSource)
    }
}

/**
 * Seek-bar thumbnail policy, ported from the mobile SeekPreviewController:
 * open the stream shortly after the controls ask for a warm-up, grab the frame
 * at the current position, then prefetch frames spread over the title (paused
 * while the main player buffers). While scrubbing only the latest request is
 * decoded. The decoder is released after [idleReleaseMs] without activity and
 * reopened on demand. All state lives on one daemon thread.
 */
internal class SeekPreviewThumbnailer(
    private val openSource: () -> SeekPreviewFrameSource?,
    private val onThumbnail: (SeekPreviewThumbnail) -> Unit,
    private val onUnavailable: () -> Unit,
    private val warmUpDelayMs: Long = 1_200L,
    private val idleReleaseMs: Long = 30_000L,
    private val prefetchDelayMs: Long = 3_000L,
    private val prefetchGapMs: Long = 250L,
    private val bufferingPollMs: Long = 500L,
    private val frameTimeoutMs: Long = 15_000L,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "nuvio-seek-thumbnails").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    },
) {
    private val log = Logger.withTag("SeekPreviewThumbs")
    private val workDirectory: File by lazy {
        Files.createTempDirectory("nuvio-seek-thumbs").toFile().apply { deleteOnExit() }
    }

    // Thumbnailer thread only.
    private var source: SeekPreviewFrameSource? = null
    private val decoded = LinkedHashMap<Long, Long>() // bucket -> keyframe position
    private var durationMs = 0L
    private var anchorMs = 0L
    private var scrubbing = false
    private var wanted: Long? = null
    private var decodeScheduled = false
    private var buffering = false
    private var hasSucceeded = false
    private var failuresBeforeSuccess = 0
    private var unavailable = false
    private var disposed = false
    private var warmTask: ScheduledFuture<*>? = null
    private var prefetchTask: ScheduledFuture<*>? = null
    private var prefetchTargets: ArrayDeque<Long> = ArrayDeque()
    private var releaseTask: ScheduledFuture<*>? = null

    fun warmUp(positionMs: Long, durationMs: Long) = post {
        if (!setDuration(durationMs) || scrubbing) return@post
        anchorMs = positionMs.coerceIn(0L, this.durationMs)
        if (warmTask?.isDone == false || prefetchTask?.isDone == false) return@post
        cancelRelease()
        warmTask = executor.schedule({
            guard {
                if (scrubbing) return@guard
                decode(bucketFor(anchorMs))
                schedulePrefetch(0L)
            }
        }, if (hasSucceeded) 0L else warmUpDelayMs, TimeUnit.MILLISECONDS)
    }

    /** [bucketMs] is the controls page's cache key; it must come from [bucketFor]. */
    fun request(bucketMs: Long, durationMs: Long) = post {
        if (!setDuration(durationMs)) return@post
        scrubbing = true
        warmTask?.cancel(false)
        prefetchTask?.cancel(false)
        cancelRelease()
        // Keep the page's key as-is: the frame is delivered under it.
        wanted = bucketMs.coerceAtLeast(0L)
        if (!decodeScheduled) {
            decodeScheduled = true
            executor.execute { guard { decodeWanted() } }
        }
    }

    fun endScrub() = post {
        if (!scrubbing) return@post
        scrubbing = false
        wanted?.let { anchorMs = it }
        wanted = null
        if (hasSucceeded) schedulePrefetch(prefetchDelayMs) else scheduleRelease()
    }

    fun setBuffering(isBuffering: Boolean) = post { buffering = isBuffering }

    fun dispose() {
        post {
            disposed = true
            warmTask?.cancel(false)
            prefetchTask?.cancel(false)
            cancelRelease()
            closeSource()
            workDirectory.deleteRecursively()
        }
        executor.shutdown()
    }

    fun bucketFor(positionMs: Long): Long = seekPreviewBucket(positionMs, durationMs)

    private fun setDuration(value: Long): Boolean {
        if (disposed || unavailable || value <= 0L) return false
        durationMs = value
        return true
    }

    private fun decodeWanted() {
        decodeScheduled = false
        val bucket = wanted ?: return
        decode(bucket)
        // A newer request may have arrived while decoding.
        if (wanted != null && wanted != bucket && !decodeScheduled) {
            decodeScheduled = true
            executor.execute { guard { decodeWanted() } }
        }
    }

    private fun schedulePrefetch(delayMs: Long) {
        if (disposed || unavailable || durationMs <= 0L) return
        prefetchTask?.cancel(false)
        prefetchTargets = ArrayDeque(seekPreviewPrefetchTargets(anchorMs, durationMs))
        prefetchTask = executor.schedule({ guard { prefetchStep() } }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun prefetchStep() {
        if (scrubbing || disposed || unavailable) return
        if (buffering) {
            prefetchTask = executor.schedule({ guard { prefetchStep() } }, bufferingPollMs, TimeUnit.MILLISECONDS)
            return
        }
        val stepMs = seekPreviewPrefetchStep(durationMs)
        while (prefetchTargets.isNotEmpty()) {
            val target = prefetchTargets.removeFirst()
            val bucket = bucketFor(target)
            if (decoded.values.any { abs(it - target) < stepMs / 2 }) continue
            decode(bucket)
            prefetchTask = executor.schedule({ guard { prefetchStep() } }, prefetchGapMs, TimeUnit.MILLISECONDS)
            return
        }
        scheduleRelease()
    }

    private fun decode(bucket: Long) {
        if (disposed || unavailable) return
        if (decoded.containsKey(bucket)) return
        val active = source ?: openSource().also { opened ->
            if (opened == null) {
                markUnavailable("could not open the stream")
                return
            }
            source = opened
            log.d { "thumbnail source opened" }
        } ?: return
        val output = File(workDirectory, "frame-$bucket.jpg")
        val started = System.nanoTime()
        val result = runCatching { active.frame(bucket, output, frameTimeoutMs) }
            .getOrElse { error ->
                log.w(error) { "thumbnail decode failed" }
                SeekPreviewFrameSource.UNAVAILABLE
            }
        if (disposed) return
        if (result < 0L || !output.isFile) {
            output.delete()
            if (result == SeekPreviewFrameSource.RETRY_LATER) return
            failuresBeforeSuccess += 1
            if (!hasSucceeded && failuresBeforeSuccess >= MaxInitialFailures) markUnavailable("no frame after $failuresBeforeSuccess attempts")
            return
        }
        val jpeg = output.readBytes()
        output.delete()
        hasSucceeded = true
        decoded[bucket] = result
        while (decoded.size > MaxDecodedFrames) decoded.remove(decoded.keys.first())
        log.d { "thumbnail bucket=$bucket keyframe=$result in ${(System.nanoTime() - started) / 1_000_000}ms" }
        onThumbnail(SeekPreviewThumbnail(bucketMs = bucket, positionMs = result, jpeg = jpeg))
    }

    private fun markUnavailable(reason: String) {
        if (unavailable) return
        unavailable = true
        log.i { "seek thumbnails unavailable: $reason" }
        prefetchTask?.cancel(false)
        closeSource()
        onUnavailable()
    }

    private fun scheduleRelease() {
        cancelRelease()
        if (source == null) return
        releaseTask = executor.schedule({
            guard { if (!scrubbing) closeSource() }
        }, idleReleaseMs, TimeUnit.MILLISECONDS)
    }

    private fun cancelRelease() {
        releaseTask?.cancel(false)
        releaseTask = null
    }

    private fun closeSource() {
        val active = source ?: return
        source = null
        runCatching { active.close() }
        log.d { "thumbnail source released" }
    }

    private inline fun guard(block: () -> Unit) {
        if (disposed) return
        runCatching(block).onFailure { error -> log.w(error) { "thumbnail task failed" } }
    }

    private fun post(block: () -> Unit) {
        if (executor.isShutdown) return
        runCatching { executor.execute { guard(block) } }
    }

    private companion object {
        const val MaxInitialFailures = 3
        const val MaxDecodedFrames = 400
    }
}

private const val MinBucketMs = 1_000L
private const val MaxBucketMs = 5_000L
private const val BucketsPerTitle = 1_000L
private const val PrefetchCount = 48L
private const val PrefetchMinStepMs = 30_000L

/** Must match `seekPreviewBucket` in player-ui/controls.js. */
internal fun seekPreviewBucket(positionMs: Long, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    val bucketMs = (durationMs / BucketsPerTitle).coerceIn(MinBucketMs, MaxBucketMs)
    return ((positionMs.coerceIn(0L, durationMs) + bucketMs / 2) / bucketMs * bucketMs).coerceAtMost(durationMs)
}

internal fun seekPreviewPrefetchStep(durationMs: Long): Long =
    (durationMs / PrefetchCount).coerceAtLeast(PrefetchMinStepMs)

/** Targets spreading out from [anchorMs], nearest first (mobile prefetch order). */
internal fun seekPreviewPrefetchTargets(anchorMs: Long, durationMs: Long): List<Long> {
    if (durationMs <= 0L) return emptyList()
    val anchor = anchorMs.coerceIn(0L, durationMs)
    val step = seekPreviewPrefetchStep(durationMs)
    return buildList {
        add(anchor)
        var offset = step
        while (anchor + offset <= durationMs || anchor - offset >= 0L) {
            if (anchor + offset <= durationMs) add(anchor + offset)
            if (anchor - offset >= 0L) add(anchor - offset)
            offset += step
        }
    }
}
