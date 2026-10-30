package com.nuvio.app.features.player.desktop

import kotlin.test.*

class BufferedQualityReadinessTest {
    private fun ready(position: Long = 4000, target: Long = 4000, buffered: Long = 6500,
        duration: Long = 20_000, frame: Boolean = true, seeking: Boolean = false, loading: Boolean = false) =
        bufferedQualityReady(position, target, buffered, duration, frame, seeking, loading)

    @Test fun requiresDecodedFrameAndBufferedVideo() {
        assertTrue(ready())
        assertFalse(ready(frame = false))
        assertFalse(ready(buffered = 4500))
        assertFalse(ready(seeking = true))
        assertFalse(ready(loading = true))
    }
    @Test fun oldPlayerAdvancingRequiresAlignmentBeforeCut() {
        assertFalse(ready(target = 6000, buffered = 9000))
        assertTrue(ready(position = 5900, target = 6000, buffered = 9000))
    }
    @Test fun differingTimelinesDoNotBlockPreparation() {
        assertEquals(19_000L, bufferedQualityTarget(40_000, 20_000, true))
        assertEquals(0L, bufferedQualityTarget(40_000, 0, false))
        assertEquals(4_000L, bufferedQualityTarget(4_000, 20_000, true))
    }
    @Test fun nearEndOnlyRequiresRemainingDuration() {
        assertTrue(ready(position = 19_000, target = 19_000, buffered = 20_000))
    }
}
