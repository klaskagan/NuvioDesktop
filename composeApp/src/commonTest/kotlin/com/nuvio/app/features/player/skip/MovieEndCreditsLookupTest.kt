package com.nuvio.app.features.player.skip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MovieEndCreditsLookupTest {
    @Test fun primaryHasPriorityAndCachesPerReleaseDuration() = runTest {
        var primaryCalls = 0
        val lookup = MovieEndCreditsLookup(
            primary = { primaryCalls++; IntroDbSegmentsResponse(outro = IntroDbSegment(startSec = 500.0, endSec = 590.0)) },
            backup = { error("Backup must not run when primary has valid credits") },
        )
        assertEquals(500_000L, lookup.startMs("tt123", 600_000))
        assertEquals(500_000L, lookup.startMs("tt123", 600_000))
        assertEquals(1, primaryCalls)
        assertEquals(500_000L, lookup.startMs("tt123", 700_000))
        assertEquals(2, primaryCalls)
    }

    @Test fun missingPrimaryUsesBackupMillisecondsIncludingOpenEndedCredits() = runTest {
        val lookup = MovieEndCreditsLookup(primary = { null }, backup = { url ->
            assertEquals("https://api.theintrodb.org/v4/media?imdb_id=tt123&duration_ms=600000", url)
            """{"type":"movie","credits":[{"start_ms":570000,"end_ms":null},{"start_ms":500000,"end_ms":550000}]}"""
        })
        assertEquals(500_000L, lookup.startMs("tt123", 600_000))
    }

    @Test fun invalidPrimaryAndMalformedOrNonMovieBackupReturnNoTiming() = runTest {
        for (body in listOf("not-json", "{}", """{"type":"tv","credits":[{"start_ms":500000}]}""",
            """{"type":"movie","credits":[{"start_ms":-1},{"start_ms":700000},{"start_ms":500000,"end_ms":450000}]}""")) {
            val lookup = MovieEndCreditsLookup(
                primary = { IntroDbSegmentsResponse(outro = IntroDbSegment(startSec = 700.0, endSec = 800.0)) },
                backup = { body },
            )
            assertNull(lookup.startMs("tt123", 600_000))
        }
    }

    @Test fun networkFailureAndTimeoutUseBackup() = runTest {
        for (timeout in listOf(false, true)) {
            var backupCalled = false
            val lookup = MovieEndCreditsLookup(primary = {
                if (timeout) delay(6_000) else error("HTTP 503")
                null
            }, backup = {
                backupCalled = true
                """{"type":"movie","credits":[{"start_ms":500000,"end_ms":null}]}"""
            })
            assertEquals(500_000L, lookup.startMs("tt123", 600_000))
            assertTrue(backupCalled)
        }
    }

    @Test fun cancellationDoesNotStartBackupOrCacheFailure() = runTest {
        var cancel = true
        val lookup = MovieEndCreditsLookup(primary = {
            if (cancel) throw CancellationException("movie changed")
            IntroDbSegmentsResponse(outro = IntroDbSegment(startMs = 500_000, endMs = 590_000))
        }, backup = { error("Cancelled playback must not query backup") })
        assertFailsWith<CancellationException> { lookup.startMs("tt123", 600_000) }
        cancel = false
        assertEquals(500_000L, lookup.startMs("tt123", 600_000))
    }
}
