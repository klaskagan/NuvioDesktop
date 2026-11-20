package com.nuvio.app.features.handoff

import java.io.ByteArrayOutputStream
import java.net.*

internal object TvDiscovery {
    /** Advertised services plus explicit previous addresses; never scans a subnet. */
    fun discover(known: List<DiscoveredTv> = emptyList()): List<DiscoveredTv> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val ssdp = pool.submit<List<DiscoveredTv>> { discoverSsdp() }
            val dns = pool.submit<List<DiscoveredTv>> { discoverDns() }
            return mergeKnown(ssdp.get() + dns.get(), known) { tv ->
                runCatching { Socket().use { it.connect(InetSocketAddress(tv.host,
                    if (tv.platform == "sony") 6466 else 3001), 400) }; true }.getOrDefault(false)
            }
        } finally { pool.shutdownNow() }
    }

    internal fun mergeKnown(found: List<DiscoveredTv>, known: List<DiscoveredTv>,
        reachable: (DiscoveredTv) -> Boolean): List<DiscoveredTv> = mergeKnownTvs(found, known, reachable)

    private fun interfaces(): List<NetworkInterface> = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isPointToPoint &&
            it.inetAddresses.toList().any { a -> a is Inet4Address && a.isSiteLocalAddress } }

    private fun discoverSsdp(): List<DiscoveredTv> = runCatching {
        val found = mutableListOf<DiscoveredTv>()
        MulticastSocket().use { socket ->
            socket.soTimeout = 200; socket.timeToLive = 2
            interfaces().forEach { network ->
                socket.networkInterface = network
                listOf("ssdp:all", "urn:lge-com:service:webos-second-screen:1").forEach { service ->
                    val query = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $service\r\n\r\n").toByteArray()
                    socket.send(DatagramPacket(query, query.size, InetAddress.getByName("239.255.255.250"), 1900))
                }
            }
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline) {
                val packet = DatagramPacket(ByteArray(8192), 8192)
                try {
                    socket.receive(packet)
                    ssdpTv(String(packet.data, 0, packet.length), packet.address.hostAddress)?.let(found::add)
                } catch (_: SocketTimeoutException) { }
            }
        }
        found
    }.getOrDefault(emptyList())

    internal fun ssdpTv(response: String, host: String): DiscoveredTv? = com.nuvio.app.features.handoff.ssdpTv(response, host)

    private fun discoverDns(): List<DiscoveredTv> = runCatching {
        val found = mutableListOf<DiscoveredTv>()
        MulticastSocket(null).use { socket ->
            socket.reuseAddress = true; socket.bind(InetSocketAddress(5353)); socket.soTimeout = 200
            val group = InetSocketAddress("224.0.0.251", 5353)
            val output = ByteArrayOutputStream()
            output.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            "_androidtvremote2._tcp.local".split('.').forEach { label -> output.write(label.length); output.write(label.toByteArray()) }
            output.write(byteArrayOf(0, 0, 12, 0x80.toByte(), 1))
            val query = output.toByteArray()
            interfaces().forEach { network ->
                runCatching {
                    socket.joinGroup(group, network); socket.networkInterface = network
                    socket.send(DatagramPacket(query, query.size, group))
                }
            }
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline) {
                val packet = DatagramPacket(ByteArray(16384), 16384)
                try {
                    socket.receive(packet)
                    val name = androidServiceName(packet.data.copyOf(packet.length)) ?: continue
                    if (runCatching { privateTvAddress(packet.address.hostAddress) }.isSuccess)
                        found += DiscoveredTv(name, packet.address.hostAddress, "sony")
                } catch (_: SocketTimeoutException) { }
            }
        }
        found
    }.getOrDefault(emptyList())

    internal fun androidServiceName(packet: ByteArray): String? = androidTvServiceName(packet)
}
