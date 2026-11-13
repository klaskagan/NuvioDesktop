package com.nuvio.app.features.player

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

/** Opt-in live verification: no key or raw provider response is written to test output. */
class PitchApiLiveTest {
    @Test fun novemberReplayHasConfirmedPitchLineups() = runBlocking {
        val key = System.getenv("PITCHAPI_TEST_KEY").orEmpty()
        assumeTrue("Requires an explicitly provided PitchAPI test key", key.isNotBlank())
        PitchApiProvider(key).use { provider ->
            val match = provider.matches("Roma", "2026-11-03").single {
                it.home == "Manchester United" && it.away == "Roma"
            }
            val teams = provider.lineups(match.id)
            assertEquals(2, teams.size)
            assertTrue(teams.all { it.starters.size == 11 })
            assertTrue(teams.all { it.bench.isNotEmpty() && it.formation.isNotBlank() })
            assertTrue(teams.flatMap { it.starters }.all { it.pitchX != null && it.pitchY != null })
        }
    }
}
