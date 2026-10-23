package com.nuvio.app.features.handoff

import kotlin.test.*

class TvDiscoveryTest {
    @Test fun `broader SSDP recognizes Sony without Android DNS advertisement`() {
        val response = "HTTP/1.1 200 OK\r\nST: urn:schemas-sony-com:service:IRCC:1\r\n\r\n"
        assertEquals("sony", TvDiscovery.ssdpTv(response, "192.168.1.4")?.platform)
        assertNull(TvDiscovery.ssdpTv(response, "8.8.8.8"))
    }
    @Test fun `LG SSDP is case insensitive and unrelated devices are ignored`() {
        assertEquals("lg", TvDiscovery.ssdpTv("HTTP/1.1 200 OK\r\nserver: webOS TV\r\n", "192.168.1.127")?.platform)
        assertNull(TvDiscovery.ssdpTv("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n", "192.168.1.1"))
    }
    @Test fun `forgotten pairing can be rediscovered by its reachable historical address`() {
        val lg = DiscoveredTv("LG OLED", "192.168.1.127", "lg")
        val result = TvDiscovery.mergeKnown(emptyList(), listOf(lg, lg)) { true }
        assertEquals(listOf(lg.copy(previouslySeen = true)), result)
        assertTrue(TvDiscovery.mergeKnown(emptyList(), listOf(lg)) { false }.isEmpty())
    }
    @Test fun `advertisements take precedence and public history is not probed`() {
        val lg = DiscoveredTv("LG", "192.168.1.127", "lg")
        var probes = 0
        assertEquals(listOf(lg), TvDiscovery.mergeKnown(listOf(lg, lg), listOf(lg, DiscoveredTv("bad", "8.8.8.8", "lg"))) { probes++; true })
        assertEquals(0, probes)
    }
    @Test fun `Android Legacy and Nuvio use distinct bounded encoded launch routes`() {
        val payload = "{\"token\":\"a+b\"}"
        assertTrue(androidTvHandoffLink("com.nuvio.tv", payload).startsWith("nuvio-handoff://"))
        val debug = androidTvHandoffLink("space.nuvio.nativelegacy.strictdebug", payload)
        assertTrue(debug.startsWith("nuvio-legacy-handoff-debug://"))
        assertEquals(payload, java.net.URLDecoder.decode(debug.substringAfter("launch="), "UTF-8"))
        assertTrue(androidTvHandoffLink("space.nuvio.nativelegacy", payload).startsWith("nuvio-legacy-handoff://"))
    }
}
