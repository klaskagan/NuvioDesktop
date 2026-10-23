package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerControlTvDevice
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NativePlayerControlsJsonTest {
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
