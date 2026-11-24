package com.nuvio.app.features.handoff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DetailsTvHandoffRequestTest {
    private val now = 1_700_000_000_000L

    private fun request(
        isSeries: Boolean = false,
        position: Long? = null,
        duration: Long? = null,
        runtime: Int? = null,
    ) = detailsTvHandoffRequest(
        senderDeviceId = "desktop", targetDeviceId = "tv", accountId = "account", profileId = 1,
        isSeries = isSeries, contentId = "tt0133093", videoId = if (isSeries) "tt0133093:2:3" else "tt0133093",
        title = "Fixture", season = 2.takeIf { isSeries }, episode = 3.takeIf { isSeries },
        savedPositionMs = position, savedDurationMs = duration, runtimeMinutes = runtime, nowEpochMs = now,
    )

    @Test fun resumesFromTheSavedPosition() {
        val sent = assertNotNull(request(position = 1_100_000, duration = 7_200_000))
        assertEquals(1_100_000, sent.positionMs)
        assertEquals(7_200_000, sent.durationMs)
        assertNull(sent.validate(now))
    }

    @Test fun startsFromTheBeginningWithTheRuntimeWhenNothingIsSaved() {
        val sent = assertNotNull(request(runtime = 50))
        assertEquals(0, sent.positionMs)
        assertEquals(3_000_000, sent.durationMs)
    }

    @Test fun seriesCarryTheirEpisode() {
        val sent = assertNotNull(request(isSeries = true, runtime = 45))
        assertEquals("series", sent.contentType)
        assertEquals(2, sent.season)
        assertEquals(3, sent.episode)
    }

    @Test fun noDurationMeansNoRequest() {
        assertNull(request())
        assertNull(request(position = 10, duration = 0, runtime = 0))
    }
}
