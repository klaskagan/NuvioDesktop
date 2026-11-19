package com.nuvio.app.features.player.desktop

import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeekPreviewThumbnailerTest {
    private class FakeSource(
        private val result: (Long) -> Long = { it },
        private val gate: CountDownLatch? = null,
    ) : SeekPreviewFrameSource {
        val positions: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        @Volatile var closed = false
        override fun frame(positionMs: Long, output: File, timeoutMs: Long): Long {
            positions += positionMs
            gate?.await(5, TimeUnit.SECONDS)
            val value = result(positionMs)
            if (value >= 0) output.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), positionMs.toByte()))
            return value
        }
        override fun close() { closed = true }
    }

    private fun thumbnailer(
        sources: MutableList<FakeSource>,
        newSource: () -> FakeSource,
        thumbnails: MutableList<SeekPreviewThumbnail>,
        unavailable: CountDownLatch = CountDownLatch(1),
        idleReleaseMs: Long = 60_000L,
    ) = SeekPreviewThumbnailer(
        openSource = { newSource().also { sources += it } },
        onThumbnail = { thumbnails += it },
        onUnavailable = { unavailable.countDown() },
        warmUpDelayMs = 0L,
        idleReleaseMs = idleReleaseMs,
        prefetchDelayMs = 0L,
        prefetchGapMs = 0L,
    )

    private fun eventually(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(10) }
        throw AssertionError(message)
    }

    @Test
    fun `buckets match the controls page and mobile`() {
        assertEquals(0L, seekPreviewBucket(0, 0))
        // 100 min title -> 5 s buckets (capped), rounded to the nearest bucket.
        assertEquals(1_270_000L, seekPreviewBucket(1_271_000, 6_041_000))
        assertEquals(1_275_000L, seekPreviewBucket(1_272_600, 6_041_000))
        // 20 s clip -> 1 s buckets (floor), clamped to the duration.
        assertEquals(20_000L, seekPreviewBucket(25_000, 20_000))
        assertEquals(3_000L, seekPreviewBucket(2_600, 20_000))
    }

    @Test
    fun `prefetch spreads out from the anchor nearest first`() {
        val targets = seekPreviewPrefetchTargets(anchorMs = 600_000, durationMs = 6_000_000)
        assertEquals(listOf(600_000L, 725_000L, 475_000L, 850_000L), targets.take(4))
        assertEquals(targets.size, targets.toSet().size)
        assertTrue(targets.all { it in 0..6_000_000 })
        assertEquals(30_000L, seekPreviewPrefetchStep(600_000), "short titles keep a 30 s minimum step")
    }

    @Test
    fun `while scrubbing only the latest request is decoded`() {
        val gate = CountDownLatch(1)
        val sources = Collections.synchronizedList(mutableListOf<FakeSource>())
        val thumbnails = Collections.synchronizedList(mutableListOf<SeekPreviewThumbnail>())
        val thumbs = thumbnailer(sources, { FakeSource(gate = gate) }, thumbnails)
        thumbs.request(10_000, 600_000)
        eventually("first decode starts") { sources.singleOrNull()?.positions?.size == 1 }
        listOf(20_000L, 30_000L, 40_000L, 50_000L).forEach { thumbs.request(it, 600_000) }
        gate.countDown()
        eventually("latest decoded") { thumbnails.size == 2 }
        assertEquals(listOf(10_000L, 50_000L), sources.single().positions.toList())
        assertEquals(listOf(10_000L, 50_000L), thumbnails.map { it.bucketMs })
        assertTrue(thumbnails.last().dataUrl().startsWith("data:image/jpeg;base64,"))
        thumbs.dispose()
        eventually("disposed source closed") { sources.single().closed }
    }

    @Test
    fun `warm-up grabs the current frame then prefetches until scrubbing starts`() {
        val sources = Collections.synchronizedList(mutableListOf<FakeSource>())
        val thumbnails = Collections.synchronizedList(mutableListOf<SeekPreviewThumbnail>())
        val thumbs = thumbnailer(sources, { FakeSource() }, thumbnails)
        thumbs.warmUp(positionMs = 600_000, durationMs = 6_000_000)
        eventually("prefetch progressed") { thumbnails.size >= 4 }
        assertEquals(600_000L, thumbnails.first().bucketMs, "current position first")
        thumbs.dispose()
    }

    @Test
    fun `buffering pauses prefetch`() {
        val sources = Collections.synchronizedList(mutableListOf<FakeSource>())
        val thumbnails = Collections.synchronizedList(mutableListOf<SeekPreviewThumbnail>())
        val thumbs = thumbnailer(sources, { FakeSource() }, thumbnails)
        thumbs.setBuffering(true)
        thumbs.warmUp(positionMs = 0, durationMs = 6_000_000)
        eventually("warm-up frame") { thumbnails.size == 1 }
        Thread.sleep(300)
        assertEquals(1, thumbnails.size, "no prefetch while the player buffers")
        thumbs.setBuffering(false)
        eventually("prefetch resumes") { thumbnails.size > 3 }
        thumbs.dispose()
    }

    @Test
    fun `three failures before a first frame mark thumbnails unavailable`() {
        val unavailable = CountDownLatch(1)
        val sources = Collections.synchronizedList(mutableListOf<FakeSource>())
        val thumbnails = Collections.synchronizedList(mutableListOf<SeekPreviewThumbnail>())
        val thumbs = thumbnailer(sources, { FakeSource(result = { SeekPreviewFrameSource.UNAVAILABLE }) }, thumbnails, unavailable)
        listOf(1_000L, 2_000L, 3_000L).forEach { thumbs.request(it, 600_000); Thread.sleep(50) }
        assertTrue(unavailable.await(5, TimeUnit.SECONDS))
        eventually("source closed when unavailable") { sources.all { it.closed } }
        thumbs.request(4_000, 600_000)
        Thread.sleep(100)
        assertEquals(3, sources.sumOf { it.positions.size }, "no decoding after giving up")
        assertTrue(thumbnails.isEmpty())
        thumbs.dispose()
    }

    @Test
    fun `retry-later results never give up`() {
        val unavailable = CountDownLatch(1)
        val sources = Collections.synchronizedList(mutableListOf<FakeSource>())
        val thumbnails = Collections.synchronizedList(mutableListOf<SeekPreviewThumbnail>())
        val thumbs = thumbnailer(sources, { FakeSource(result = { SeekPreviewFrameSource.RETRY_LATER }) }, thumbnails, unavailable)
        (1..5).forEach { thumbs.request(it * 1_000L, 600_000); Thread.sleep(30) }
        eventually("all attempts made") { sources.sumOf { it.positions.size } == 5 }
        assertFalse(unavailable.await(100, TimeUnit.MILLISECONDS))
        thumbs.dispose()
    }

    @Test
    fun `idle connection is released after a scrub and reopened on demand`() {
        val sources = Collections.synchronizedList(mutableListOf<FakeSource>())
        val thumbnails = Collections.synchronizedList(mutableListOf<SeekPreviewThumbnail>())
        val thumbs = thumbnailer(sources, { FakeSource() }, thumbnails, idleReleaseMs = 100L)
        thumbs.request(5_000, 60_000)
        eventually("frame") { thumbnails.size == 1 }
        thumbs.endScrub()
        // A 60 s title prefetches a few targets, then releases after the idle delay.
        eventually("released") { sources.single().closed }
        thumbs.request(42_000, 60_000)
        eventually("reopened") { sources.size == 2 && thumbnails.any { it.bucketMs == 42_000L } }
        thumbs.dispose()
    }
}
