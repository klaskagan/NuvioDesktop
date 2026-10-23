package com.nuvio.app.features.handoff

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvHandoffProtocolTest {
    private val now = 1_700_000_000_000L
    private fun request() = TvHandoffRequest(
        requestId = "request-1", senderDeviceId = "desktop", targetDeviceId = "tv",
        accountId = "account", profileId = 1, contentType = "movie", contentId = "tt0133093",
        videoId = "tt0133093", title = "The Matrix", positionMs = 2_100_000, durationMs = 8_100_000,
        createdAtEpochMs = now, expiresAtEpochMs = now + TV_HANDOFF_TTL_MS,
    )

    @Test fun contentContractRoundTripsWithoutStreamUrls() {
        val json = Json.encodeToString(request())
        assertEquals(request(), Json.decodeFromString<TvHandoffRequest>(json))
        assertFalse(json.contains("sourceUrl"))
        assertNull(request().validate(now))
    }

    @Test fun rejectsInvalidOrExpiredRequests() {
        assertEquals(TvHandoffRejection.UNSUPPORTED_VERSION, request().copy(version = 2).validate(now))
        assertEquals(TvHandoffRejection.EXPIRED, request().validate(now + TV_HANDOFF_TTL_MS))
        listOf(
            request().copy(positionMs = -1), request().copy(positionMs = Long.MAX_VALUE),
            request().copy(durationMs = 0), request().copy(profileId = 0),
            request().copy(contentType = "series"), request().copy(episode = 2),
            request().copy(expiresAtEpochMs = now + TV_HANDOFF_TTL_MS + 1),
            request().copy(createdAtEpochMs = now + 60_000),
        ).forEach { assertEquals(TvHandoffRejection.INVALID_REQUEST, it.validate(now)) }
    }

    @Test fun validatesAccountDeviceAndProfileSeparately() {
        assertNull(request().receiverRejection(now, "tv", "account", 1))
        assertEquals(TvHandoffRejection.WRONG_DEVICE, request().receiverRejection(now, "other", "account", 1))
        assertEquals(TvHandoffRejection.WRONG_ACCOUNT, request().receiverRejection(now, "tv", "other", 1))
        assertEquals(TvHandoffRejection.PROFILE_REQUIRED, request().receiverRejection(now, "tv", "account", 2))
    }

    @Test fun onlyConfirmedPlaybackSuppressesDesktopProgress() {
        val session = TvHandoffSession(request())
        fun advance(phase: TvHandoffPhase, position: Long? = null) = session.accept(
            TvHandoffUpdate("request-1", "tv", phase, position), now,
        )
        assertFalse(advance(TvHandoffPhase.PLAYING, request().positionMs))
        assertTrue(advance(TvHandoffPhase.RECEIVED))
        assertTrue(advance(TvHandoffPhase.RESOLVING))
        assertTrue(advance(TvHandoffPhase.AWAITING_USER))
        assertTrue(advance(TvHandoffPhase.LOADING))
        assertFalse(session.suppressLocalProgress)
        assertFalse(advance(TvHandoffPhase.PLAYING))
        assertFalse(advance(TvHandoffPhase.PLAYING, request().positionMs - 10_000))
        assertTrue(advance(TvHandoffPhase.PLAYING, request().positionMs + 500))
        assertTrue(session.suppressLocalProgress)
        assertFalse(advance(TvHandoffPhase.FAILED))
    }

    @Test fun lateOrWrongDeviceUpdatesCannotReviveCancelledSessions() {
        val session = TvHandoffSession(request())
        assertFalse(session.accept(TvHandoffUpdate("other", "tv", TvHandoffPhase.RECEIVED), now))
        assertFalse(session.accept(TvHandoffUpdate("request-1", "other", TvHandoffPhase.RECEIVED), now))
        assertTrue(session.cancel())
        assertFalse(session.accept(TvHandoffUpdate("request-1", "tv", TvHandoffPhase.RECEIVED), now))
        assertFalse(session.suppressLocalProgress)
    }

    @Test fun expiresPendingSessionButNotSuccessfulPlayback() {
        val session = TvHandoffSession(request())
        assertTrue(session.expire(now + TV_HANDOFF_TTL_MS))
        assertEquals(TvHandoffPhase.EXPIRED, session.phase)
        assertFalse(session.cancel())
    }

    @Test fun editionHintsDoNotConstrainQuality() {
        assertEquals("extended", tvHandoffEditionHint("Movie.EXTENDED.1080p.WEB-DL.H264"))
        assertEquals("extended", tvHandoffEditionHint("Movie.Extended.2160p.DV.HDR.HEVC"))
        assertEquals("director's cut", tvHandoffEditionHint("Movie.Directors.Cut.1080p"))
        assertNull(tvHandoffEditionHint("Movie.2160p.DV.HDR.HEVC"))
    }
}
