package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNightModeTest {
    @Test
    fun detectsOnlyTheLabeledNightModeFilter() {
        assertFalse(desktopNightModeInChain(""))
        assertFalse(desktopNightModeInChain("@nuvio_night_other:lavfi=[alimiter]"))
        assertTrue(desktopNightModeInChain(DESKTOP_NIGHT_MODE_FILTER))
        assertTrue(desktopNightModeInChain("@other:volume=1,$DESKTOP_NIGHT_MODE_FILTER"))
    }

    @Test
    fun publishesConfirmedStateAndFailureTokenToOverlay() {
        val state = PlayerControlsState(nightModeActive = true, nightModeErrorToken = 2)
        val json = Json.parseToJsonElement(state.toControlsJson(false) {}).jsonObject

        assertTrue(json.getValue("nightModeActive").jsonPrimitive.boolean)
        assertEquals(2, json.getValue("nightModeErrorToken").jsonPrimitive.int)
    }
}
