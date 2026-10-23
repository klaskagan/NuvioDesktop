package com.nuvio.app.features.handoff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

/** Explicit opt-in hardware checks; never touches a TV during the normal test suite. */
class TvRemoteHardwareTest {
    @Test fun launchesNuvioOnPairedLgUsingTheProductionAdapter() {
        val config = System.getenv("NUVIO_TV_TEST_CONFIG")
        assumeTrue("Set NUVIO_TV_TEST_CONFIG to opt into physical TV checks", config != null)
        val settings = Json.parseToJsonElement(Files.readString(Path.of(config!!))).jsonObject
        val lg = settings.getValue("lg").jsonObject
        fun value(key: String) = lg.getValue(key).jsonPrimitive.content
        WebOsRemote(value("host"), value("certificatePin")).use { remote ->
            remote.register(value("clientKey"))
            remote.launch("space.nuvio.webos", kotlinx.serialization.json.buildJsonObject { })
        }
    }

    @Test fun launchesNuvioOnPairedSonyUsingTheProductionAdapter() {
        val config = System.getenv("NUVIO_TV_TEST_CONFIG")
        assumeTrue("Set NUVIO_TV_TEST_CONFIG to opt into physical TV checks", config != null)
        val settings = Json.parseToJsonElement(Files.readString(Path.of(config!!))).jsonObject
        val sony = settings.getValue("sony").jsonObject
        fun value(key: String) = sony.getValue(key).jsonPrimitive.content
        val identity = TvIdentity(Path.of(settings.getValue("identityDirectory").jsonPrimitive.content))
        AndroidTvRemote(identity).launch(value("host"), value("certificatePin"), "nuvio://meta?type=movie&id=tt0133093").use {
            Thread.sleep(1_000)
        }
    }
}
