package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerControlTvDevice
import com.nuvio.app.features.player.supportsFootballLineups
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NativePlayerControlsJsonTest {
    @Test
    fun `movie recommendation payload escapes title and session identities`() {
        val item = com.nuvio.app.features.player.PlayerControlMovieRecommendation("session:movie:tt1", "A \"movie\"\nTitle", "https://example.com/a.jpg")
        val controls = PlayerControlsState(movieRecommendationItems = listOf(item), movieRecommendationSessionKey = "session", movieRecommendationStage = 2, movieRecommendationDismissed = true)
        val payload = Json.parseToJsonElement(controls.toControlsJson(false) {}).jsonObject
        assertEquals("2", payload.getValue("movieRecommendationStage").jsonPrimitive.content)
        assertEquals(true, payload.getValue("movieRecommendationDismissed").jsonPrimitive.boolean)
        val actual = payload.getValue("movieRecommendationItems").jsonArray.single().jsonObject
        assertEquals(item.title, actual.getValue("title").jsonPrimitive.content)
        assertEquals(item.actionKey, actual.getValue("actionKey").jsonPrimitive.content)
    }

    @Test
    fun `lineups availability reaches native controls only for explicit sports types`() {
        for (type in listOf(null, "", "movie", "series", "trailer", "sport", "Sports")) {
            val state = PlayerControlsState(lineupsAvailable = supportsFootballLineups(type))
            for (fullscreen in listOf(false, true)) {
                val payload = Json.parseToJsonElement(state.toControlsJson(fullscreen) {}).jsonObject
                assertEquals(fullscreen, payload.getValue("isFullscreen").jsonPrimitive.boolean)
                assertEquals(type == "sport" || type == "Sports", payload.getValue("lineupsAvailable").jsonPrimitive.boolean)
            }
        }
    }

    @Test
    fun `controls payload parses with handoff enabled and disabled`() {
        for (enabled in listOf(false, true)) {
            val controls = PlayerControlsState(
                showTvHandoff = enabled,
                tvHandoffDevices = listOf(PlayerControlTvDevice("Sony \"Living room\"", "sony")),
                tvHandoffStatus = "Allow Nuvio → Local Network", tvHandoffReady = enabled,
                tvHandoffLabel = "Continue on \"TV\"\nNow",
            )
            val payload = Json.parseToJsonElement(controls.toControlsJson(false) {}).jsonObject
            assertEquals(enabled, payload.getValue("showTvHandoff").jsonPrimitive.boolean)
            assertEquals(controls.tvHandoffLabel, payload.getValue("tvHandoffLabel").jsonPrimitive.content)
            assertEquals(controls.tvHandoffDevices.first().name,
                payload.getValue("tvHandoffDevices").jsonArray.first().jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals(controls.tvHandoffStatus, payload.getValue("tvHandoffStatus").jsonPrimitive.content)
            assertEquals(enabled, payload.getValue("tvHandoffReady").jsonPrimitive.boolean)
            assertEquals(controls.externalPlayerLabel, payload.getValue("externalPlayerLabel").jsonPrimitive.content)
        }
    }
}
