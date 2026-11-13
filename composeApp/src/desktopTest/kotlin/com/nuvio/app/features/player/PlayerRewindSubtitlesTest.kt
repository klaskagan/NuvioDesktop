package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerRewindSubtitlesTest {
    @Test
    fun queuedPositionUpdatesDoNotEndReplayBeforeSeekLands() {
        val replay = PlayerRewindSubtitles()
        replay.rewind(fromMs = 30_000, targetMs = 20_000)

        assertFalse(replay.observe(29_950))
        assertFalse(replay.observe(30_050))
        assertTrue(replay.isActive)
        assertFalse(replay.observe(20_000))
        assertFalse(replay.observe(29_999))
        assertTrue(replay.observe(30_000))
        assertFalse(replay.isActive)
    }

    @Test
    fun consecutiveRewindsKeepTheOriginalEndpoint() {
        val replay = PlayerRewindSubtitles()
        replay.rewind(fromMs = 40_000, targetMs = 30_000)
        replay.rewind(fromMs = 30_000, targetMs = 20_000)

        assertFalse(replay.observe(30_000))
        assertFalse(replay.observe(20_000))
        assertFalse(replay.observe(39_999))
        assertTrue(replay.observe(40_000))
    }

    @Test
    fun manualOverrideCancelsAutomaticTurnOff() {
        val replay = PlayerRewindSubtitles()
        replay.rewind(fromMs = 30_000, targetMs = 20_000)
        assertFalse(replay.observe(20_000))

        assertTrue(replay.clear())
        assertFalse(replay.observe(30_000))
        assertFalse(replay.clear())
    }

    @Test
    fun explicitForwardSeekPastEndpointEndsReplayEvenBeforeConfirmation() {
        val replay = PlayerRewindSubtitles()
        replay.rewind(fromMs = 30_000, targetMs = 20_000)

        assertFalse(replay.clearAtForwardTarget(29_000))
        assertTrue(replay.clearAtForwardTarget(35_000))
        assertFalse(replay.observe(35_000))
    }
}
