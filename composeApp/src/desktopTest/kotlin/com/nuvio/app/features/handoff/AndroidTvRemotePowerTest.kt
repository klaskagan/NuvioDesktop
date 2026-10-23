package com.nuvio.app.features.handoff

import kotlin.test.*

class AndroidTvRemotePowerTest {
    @Test fun `protobuf omitted false means an explicitly reported off TV`() {
        val off = androidTvReportedPower(RemoteWire.fields(RemoteWire.bytes(40, byteArrayOf())))
        assertEquals(false, off)
        assertEquals(26, androidTvWakeKey(off))
    }
    @Test fun `an already awake TV receives an idempotent wake command`() {
        val on = androidTvReportedPower(RemoteWire.fields(RemoteWire.bytes(40, RemoteWire.number(1, 1))))
        assertEquals(true, on)
        assertEquals(224, androidTvWakeKey(on))
    }
    @Test fun `unknown or malformed power is never interpreted as off`() {
        assertNull(androidTvReportedPower(emptyList()))
        assertNull(androidTvReportedPower(RemoteWire.fields(RemoteWire.bytes(40, RemoteWire.number(1, 2)))))
        assertNull(androidTvReportedPower(RemoteWire.fields(RemoteWire.bytes(40, byteArrayOf(8)))))
        assertEquals(224, androidTvWakeKey(null))
    }
    @Test fun `ambiguous duplicate power fields cannot trigger a toggle`() {
        val body = RemoteWire.number(1, 0) + RemoteWire.number(1, 1)
        assertNull(androidTvReportedPower(RemoteWire.fields(RemoteWire.bytes(40, body))))
    }
}
