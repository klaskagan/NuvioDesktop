package com.nuvio.app.features.handoff

import java.net.InetAddress
import kotlin.test.*

class TvWakeTest {
    @Test fun `subnet broadcast precedes the global fallback and destinations are unique`() {
        val subnet = InetAddress.getByName("192.168.1.255")
        val global = InetAddress.getByName("255.255.255.255")
        assertEquals(listOf(subnet, global), tvWakeDestinations(listOf(subnet, subnet, global)))
    }
    @Test fun `IPv6 multicast is not used for IPv4 Wake on LAN`() {
        assertEquals(listOf(InetAddress.getByName("255.255.255.255")),
            tvWakeDestinations(listOf(InetAddress.getByName("ff02::1"))))
    }
    @Test fun `wake targets exactly the selected unicast network adapter`() {
        val bytes = tvWakePacket("02:00:00:00:00:01")
        assertEquals(102, bytes.size)
        assertTrue(bytes.take(6).all { it == 0xff.toByte() })
        val mac = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x01)
        repeat(16) { assertContentEquals(mac, bytes.copyOfRange(6 + it * 6, 12 + it * 6)) }
        assertFailsWith<IllegalArgumentException> { tvWakePacket("FF:FF:FF:FF:FF:FF") }
        assertFailsWith<IllegalArgumentException> { tvWakePacket("00:00:00:00:00:00") }
    }
    @Test fun `wake diagnostics remain bounded and owner private`() {
        val directory = java.nio.file.Files.createTempDirectory("nuvio-wake-log-")
        try {
            repeat(80) { appendTvWakeEvent(directory, "event-$it " + "x".repeat(500)) }
            val path = directory.resolve("wake-events.log")
            val content = java.nio.file.Files.readString(path)
            assertTrue(java.nio.file.Files.size(path) <= 32768)
            assertTrue(content.contains("event-79 "))
            assertFalse(content.contains("event-0 "))
            if (path.fileSystem.supportedFileAttributeViews().contains("posix"))
                assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                    java.nio.file.Files.getPosixFilePermissions(path))
        } finally {
            java.nio.file.Files.deleteIfExists(directory.resolve("wake-events.log"))
            java.nio.file.Files.deleteIfExists(directory)
        }
    }
}
