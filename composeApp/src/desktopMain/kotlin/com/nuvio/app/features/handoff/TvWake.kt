package com.nuvio.app.features.handoff

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

internal fun tvWakeDestinations(directed: List<InetAddress>): List<InetAddress> =
    (directed.filterIsInstance<Inet4Address>() + InetAddress.getByName("255.255.255.255")).distinct()

/** Use the TV's LAN route, including its subnet broadcast on multi-interface Macs. */
internal fun sendTvWakePackets(target: InetAddress, mac: String) {
    val source = DatagramSocket().use { socket -> socket.connect(target, 9); socket.localAddress }
    val directed = NetworkInterface.getByInetAddress(source)?.interfaceAddresses.orEmpty().mapNotNull { it.broadcast }
    val packet = tvWakePacket(mac)
    DatagramSocket(InetSocketAddress(source, 0)).use { socket ->
        socket.broadcast = true
        var sent = 0
        try {
            repeat(3) {
                tvWakeDestinations(directed).forEach { destination ->
                    socket.send(DatagramPacket(packet, packet.size, destination, 9))
                    sent++
                }
            }
        } finally {
            if (sent > 0) runCatching {
                appendTvWakeEvent(java.nio.file.Path.of(System.getProperty("user.home"), ".nuvio", "tv-handoff"),
                    "${java.time.Instant.now()} Wake-on-LAN target=${target.hostAddress} mac=$mac packets=$sent")
            }
        }
    }
}

private val wakeEventLock = Any()

/** Bounded owner-private diagnostics; no tokens, media URLs or account identity. */
internal fun appendTvWakeEvent(directory: java.nio.file.Path, event: String) = synchronized(wakeEventLock) {
    val path = directory.resolve("wake-events.log")
    val previous = if (java.nio.file.Files.exists(path) && java.nio.file.Files.size(path) <= 32768)
        java.nio.file.Files.readString(path) else ""
    privateWrite(path, (previous + event.take(512) + "\n").takeLast(32768).toByteArray(Charsets.UTF_8))
}
