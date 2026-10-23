package com.nuvio.app.features.handoff

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.HttpsURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalHandoffServerTest {
    private fun request(): TvHandoffRequest {
        val now = System.currentTimeMillis()
        return TvHandoffRequest(requestId = "test-request", senderDeviceId = "desktop", targetDeviceId = "tv",
            accountId = "account", profileId = 1, contentType = "movie", contentId = "tt0133093",
            videoId = "tt0133093", title = "The Matrix", positionMs = 60_000, durationMs = 6_000_000,
            createdAtEpochMs = now, expiresAtEpochMs = now + TV_HANDOFF_TTL_MS)
    }

    @Test fun pinnedTransportRequiresTokenAndCancellationRejectsFurtherRequests() {
        val directory = Files.createTempDirectory("nuvio-handoff-test")
        try {
            val identity = TvIdentity(directory)
            val updates = CopyOnWriteArrayList<TvHandoffUpdate>()
            LocalHandoffServer(identity, InetAddress.getLoopbackAddress(), request(), updates::add).use { server ->
                val params = server.launchParameters
                fun exchange(body: String? = null, authorized: Boolean = true): Pair<Int, String> {
                    val endpoint = params.getValue("endpoint").jsonPrimitive.content
                    val connection = URL(endpoint + if (body == null) "" else "/status").openConnection() as HttpsURLConnection
                    try {
                        connection.sslSocketFactory = identity.context(identity.certificate.fingerprint()).socketFactory
                        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                        connection.connectTimeout = 5_000; connection.readTimeout = 5_000
                        if (authorized) connection.setRequestProperty("Authorization", "Bearer ${params.getValue("token").jsonPrimitive.content}")
                        if (body != null) {
                            connection.requestMethod = "POST"; connection.doOutput = true
                            connection.outputStream.use { it.write(body.toByteArray()) }
                        }
                        val code = connection.responseCode
                        return code to if (code == 200) connection.inputStream.bufferedReader().use { it.readText() } else ""
                    } finally { connection.disconnect() }
                }
                assertEquals(403, exchange(authorized = false).first)
                val received = exchange()
                assertEquals(200, received.first)
                assertEquals(server.request, Json.decodeFromString<TvHandoffRequest>(received.second))
                val update = TvHandoffUpdate("test-request", "tv", TvHandoffPhase.RECEIVED)
                assertEquals(200, exchange(Json.encodeToString(update)).first)
                assertEquals(listOf(update), updates.toList())
                assertEquals(409, exchange(Json.encodeToString(update.copy(targetDeviceId = "other"))).first)
                assertEquals(400, exchange("x".repeat(16_385)).first)
                server.cancel()
                assertEquals(410, exchange().first)
                assertEquals(410, exchange(Json.encodeToString(update.copy(phase = TvHandoffPhase.PLAYING, positionMs = 60_000))).first)
            }
            val reloaded = TvIdentity(directory)
            assertEquals(identity.certificate.fingerprint(), reloaded.certificate.fingerprint())
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun cancellationRacingPlaybackHasExactlyOneWinner() {
        val directory = Files.createTempDirectory("nuvio-handoff-race")
        try {
            val identity = TvIdentity(directory)
            repeat(4) {
                LocalHandoffServer(identity, InetAddress.getLoopbackAddress(), request()) {}.use { server ->
                    fun post(phase: TvHandoffPhase): Int {
                        val params = server.launchParameters
                        val connection = URL(params.getValue("endpoint").jsonPrimitive.content + "/status")
                            .openConnection() as HttpsURLConnection
                        try {
                            connection.sslSocketFactory = identity.context(identity.certificate.fingerprint()).socketFactory
                            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
                            connection.setRequestProperty("Authorization", "Bearer ${params.getValue("token").jsonPrimitive.content}")
                            connection.requestMethod = "POST"; connection.doOutput = true
                            connection.outputStream.use {
                                it.write(Json.encodeToString(TvHandoffUpdate("test-request", "tv", phase, 60_000)).toByteArray())
                            }
                            return connection.responseCode
                        } finally { connection.disconnect() }
                    }
                    listOf(TvHandoffPhase.RECEIVED, TvHandoffPhase.RESOLVING, TvHandoffPhase.LOADING)
                        .forEach { assertEquals(200, post(it)) }
                    if (it == 0) {
                        assertEquals(200, post(TvHandoffPhase.PLAYING))
                        assertTrue(server.cancelAndCheckPlayback(), "confirmed TV playback must win over a later Resume here")
                        return@use
                    }
                    val gate = java.util.concurrent.CountDownLatch(1)
                    val playing = java.util.concurrent.CompletableFuture.supplyAsync {
                        gate.await(); post(TvHandoffPhase.PLAYING)
                    }
                    gate.countDown()
                    val committed = server.cancelAndCheckPlayback()
                    assertEquals(if (committed) 200 else 410, playing.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(committed, server.cancelAndCheckPlayback())
                }
            }
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun networkWakeNeverUsesPowerToggleAndRejectsInvalidAddresses() {
        val packet = tvWakePacket("02:00:00:00:00:01")
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xff.toByte() })
        assertEquals(listOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x01), packet.drop(6).take(6).map { it.toInt() and 255 })
        assertFailsWith<IllegalArgumentException> { tvWakePacket("FF:FF:FF:FF:FF:FF") }
        assertFailsWith<IllegalArgumentException> { tvWakePacket("not a mac") }
        assertEquals("192.168.1.127", privateTvAddress("192.168.1.127").hostAddress)
        listOf("8.8.8.8", "127.0.0.1", "localhost", "192.168.1.4:36481", "https://192.168.1.4").forEach {
            assertFailsWith<IllegalArgumentException> { privateTvAddress(it) }
        }
    }

    @Test fun remoteWireBoundsFramesAndEncodesTheStandardWakeCommand() {
        val message = RemoteWire.bytes(10, RemoteWire.number(1, 224) + RemoteWire.number(2, 3))
        assertEquals("520508e0011003", message.joinToString("") { "%02x".format(it) })
        val fields = RemoteWire.fields(message)
        assertEquals(10, fields.single().number)
        assertEquals(224L, RemoteWire.fields(fields.single().bytes!!).first().value)
        val frame = java.io.ByteArrayOutputStream()
        RemoteWire.writeFrame(frame, message)
        assertTrue(RemoteWire.readFrame(frame.toByteArray().inputStream()).contentEquals(message))
        assertFailsWith<IllegalArgumentException> {
            RemoteWire.readFrame((RemoteWire.encodeVarint(65_537) + ByteArray(0)).inputStream())
        }
        assertFalse(RemoteWire.fields(RemoteWire.bytes(1, byteArrayOf())).isEmpty())
    }

    @Test fun malformedDiscoveryResponsesAreIgnored() {
        assertEquals(null, TvDiscovery.androidServiceName(byteArrayOf()))
        assertEquals(null, TvDiscovery.androidServiceName(ByteArray(12)))
        val cycle = byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0xc0.toByte(), 12, 0, 12, 0, 1)
        assertEquals(null, TvDiscovery.androidServiceName(cycle))
    }
}
