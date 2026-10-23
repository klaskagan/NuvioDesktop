package com.nuvio.app.features.handoff

import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.*

class TvReceiverReadinessTest {
    @Test fun `launch acknowledgement is retried until the app receives the request`() {
        var clock = 0L; var launches = 0; var reached = false
        awaitTvReceiver({ true }, { reached }, { launches++; if (launches == 3) reached = true },
            { true }, { clock }, { clock += it * 1_000_000 })
        assertEquals(3, launches)
    }
    @Test fun `cancelling during wake stops retries`() {
        var active = true; var launches = 0
        awaitTvReceiver({ active }, { false }, { launches++ }, { true }, { 0L }, { active = false })
        assertEquals(1, launches)
    }
    @Test fun `identity failures never retry`() {
        var launches = 0
        assertFailsWith<SecurityException> {
            awaitTvReceiver({ true }, { false }, { launches++; throw SecurityException() }, { false })
        }
        assertEquals(1, launches)
    }
    @Test fun `network wake retries expire within a bounded time`() {
        var clock = 0L; var launches = 0
        assertFailsWith<SocketTimeoutException> {
            awaitTvReceiver({ true }, { false }, { launches++; if (launches == 1) throw ConnectException() },
                { true }, { clock }, { clock += it * 1_000_000 })
        }
        assertTrue(launches in 2..16)
        assertEquals(60_000_000_000L, clock)
    }
    @Test fun `wrapped websocket TLS failures never retry`() {
        var launches = 0
        val failure = IllegalStateException("Connection failed", javax.net.ssl.SSLHandshakeException("Changed certificate"))
        assertFailsWith<IllegalStateException> {
            awaitTvReceiver({ true }, { false }, { launches++; throw failure }, { !it.hasTvIdentityFailure() })
        }
        assertEquals(1, launches)
    }
    @Test fun `ordinary connection failures remain retryable`() {
        assertFalse(IllegalStateException("Connection failed", ConnectException()).hasTvIdentityFailure())
    }

    @Test fun `an unreachable sleeping TV can finish waking before the next attempt`() {
        var clock = 0L; var launches = 0; var reached = false
        awaitTvReceiver({ true }, { reached }, {
            launches++
            if (launches == 1) throw java.net.NoRouteToHostException() else reached = true
        }, { !it.hasLocalNetworkDenial() }, { clock }, { clock += it * 1_000_000 })
        assertEquals(2, launches)
    }
    @Test fun `explicit operating system network permission errors are distinct from sleeping TVs`() {
        assertTrue(IllegalStateException("Connection", java.net.SocketException("Operation not permitted")).hasLocalNetworkDenial())
        assertFalse(java.net.NoRouteToHostException().hasLocalNetworkDenial())
    }

}
