package com.nuvio.app.features.player

import kotlin.test.*

class FootballLineupDecoderTest {
    @Test fun fixtureDropsResultAndStatus() {
        val matches = FootballLineupDecoder.matches("""{"data":{"date":"2026-11-03","matches":[{"id":"m_fixture","time_utc":"2026-11-03T20:00:00Z","status":"finished","home_team":{"name":"Home"},"away_team":{"name":"Away"},"league":{"name":"Cup"},"score_home":9,"score_away":2}]}}""")
        assertEquals(FootballMatch("m_fixture", "Home", "Away", "Cup", "2026-11-03T20:00:00Z"), matches.single())
        assertFalse(matches.toString().contains("finished"))
        assertFalse(matches.toString().contains("score"))
    }
    @Test fun lineupKeepsOriginalBenchAndDropsEvents() {
        val teams = FootballLineupDecoder.lineups("""{"data":{"home_team":{"name":"Home"},"home":{"confirmed":true,"formation":null,"coach":{"name":"Coach"},"starters":[{"name":"Starter","shirt_number":"1","pitch_x":0.5,"pitch_y":0.1,"rating":9.9,"goals":3}],"subs":[{"name":"Bench","shirt_number":"2","substitution_minute":75}]},"away":{"confirmed":false,"starters":[{"name":"Prediction"}]}}}""")
        assertEquals(1, teams.size)
        assertEquals("Bench", teams.single().bench.single().name)
        assertEquals(0.5f, teams.single().starters.single().pitchX)
        assertEquals(0.1f, teams.single().starters.single().pitchY)
        assertEquals("", teams.single().formation)
        assertFalse(teams.toString().contains("9.9"))
        assertFalse(teams.toString().contains("substitution"))
        assertFalse(teams.toString().contains("Prediction"))
    }
    @Test fun errorsNeverExposeProviderMessages() {
        val error = assertFailsWith<IllegalArgumentException> {
            FootballLineupDecoder.matches("""{"error":{"message":"secret-value Final score"}}""")
        }
        assertFalse(error.message.orEmpty().contains("secret-value"))
    }
    @Test fun invalidCoordinatesUseListFallback() {
        val teams = FootballLineupDecoder.lineups("""{"data":{"home_team":{"name":"Home"},"home":{"confirmed":true,"starters":[{"name":"Keeper","pitch_x":2,"pitch_y":null}]}}}""")
        assertNull(teams.single().starters.single().pitchX)
        assertNull(teams.single().starters.single().pitchY)
    }
}
