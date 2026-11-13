package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QualitySwitchTransactionTest {
    private class Playback : QualitySwitchPlayback<String> {
        val events = mutableListOf<String>()
        var fail = false
        var wait = false
        override suspend fun capture() = "paused:position:tracks:speed:volume".also { events += "capture" }
        override suspend fun loadAndRestore(candidate: PlayerQualityChoice, checkpoint: String) {
            events += "load:$checkpoint"
            if (wait) delay(1000)
            if (fail) error("fixture load failure")
        }
        override suspend fun verify(candidate: PlayerQualityChoice, checkpoint: String) { events += "verify" }
        override suspend fun commit(candidate: PlayerQualityChoice) { events += "commit" }
        override suspend fun rollback(checkpoint: String) { events += "rollback:$checkpoint" }
    }
    private val candidate = PlayerQualityChoice(1, 1080, "fixture", emptyMap())

    @Test fun commitsOnlyAfterVerification() = runBlocking {
        val playback = Playback()
        assertIs<QualitySwitchResult.Committed>(QualitySwitchTransaction(playback).switch(candidate))
        assertEquals(listOf("capture", "load:paused:position:tracks:speed:volume", "verify", "commit"), playback.events)
    }
    @Test fun failureRollsBackAndNeverCommits() = runBlocking {
        val playback = Playback().apply { fail = true }
        assertIs<QualitySwitchResult.Failed>(QualitySwitchTransaction(playback).switch(candidate))
        assertEquals("rollback:paused:position:tracks:speed:volume", playback.events.last())
    }
    @Test fun timeoutRollsBack() = runBlocking {
        val playback = Playback().apply { wait = true }
        assertIs<QualitySwitchResult.Failed>(QualitySwitchTransaction(playback, 20).switch(candidate))
        assertEquals("rollback:paused:position:tracks:speed:volume", playback.events.last())
    }
    @Test fun cancellationRestoresBeforeNextSelection() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val adapter = object : QualitySwitchPlayback<String> {
            override suspend fun capture() = "old"
            override suspend fun loadAndRestore(candidate: PlayerQualityChoice, checkpoint: String) {
                events += candidate.id.toString()
                if (candidate.id == 1) { entered.complete(Unit); delay(10_000) }
            }
            override suspend fun verify(candidate: PlayerQualityChoice, checkpoint: String) {}
            override suspend fun rollback(checkpoint: String) { delay(10); events += "rollback" }
            override suspend fun commit(candidate: PlayerQualityChoice) { events += "commit" }
        }
        val transaction = QualitySwitchTransaction(adapter)
        val first = launch { transaction.switch(candidate) }
        entered.await()
        first.cancelAndJoin()
        transaction.switch(candidate.copy(id = 2))
        assertEquals(listOf("1", "rollback", "2", "commit"), events)
    }
    @Test fun unknownCompatibilityNeverTouchesPlayback() = runBlocking {
        val playback = Playback()
        assertIs<QualitySwitchResult.Rejected>(QualitySwitchTransaction(playback).switch(candidate.copy(disabledReason = "Unknown compatibility")))
        assertEquals(emptyList(), playback.events)
    }
}
