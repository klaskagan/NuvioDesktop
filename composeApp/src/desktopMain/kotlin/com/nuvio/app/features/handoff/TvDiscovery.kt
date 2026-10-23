package com.nuvio.app.features.handoff

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Serializable
internal data class DiscoveredTv(val name: String, val host: String, val platform: String,
    val previouslySeen: Boolean = false) {
    override fun toString() = "$name ($host)"
}

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
        reachable: (DiscoveredTv) -> Boolean): List<DiscoveredTv> {
        val advertised = found.distinctBy { it.platform to it.host }
        return (advertised + known.distinctBy { it.platform to it.host }.filter { tv ->
            runCatching { privateTvAddress(tv.host) }.isSuccess &&
                advertised.none { it.host == tv.host && it.platform == tv.platform } && reachable(tv)
        }.map { it.copy(previouslySeen = true) }).sortedBy { it.name }
    }

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

    internal fun ssdpTv(response: String, host: String): DiscoveredTv? = runCatching {
        privateTvAddress(host)
        if (!response.lineSequence().first().startsWith("HTTP/1.1 200", true)) return null
        val headers = response.lineSequence().drop(1).mapNotNull { line ->
            val colon = line.indexOf(':'); if (colon < 0) null else
                line.take(colon).trim().lowercase() to line.substring(colon + 1).trim().lowercase()
        }.toMap()
        val description = listOf("st", "server", "usn").joinToString(" ") { headers[it].orEmpty() }
        when {
            description.contains("webos") || description.contains("lge-com") -> DiscoveredTv("LG TV", host, "lg")
            description.contains("schemas-sony-com") || description.contains("sony") -> DiscoveredTv("Sony BRAVIA", host, "sony")
            else -> null
        }
    }.getOrNull()

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

    /** Parse DNS labels with bounded pointer traversal, ignoring malformed network responses. */
    internal fun androidServiceName(packet: ByteArray): String? = runCatching {
        require(packet.size >= 12)
        val input = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        input.position(4)
        val questions = input.short.toInt() and 65535
        val records = (input.short.toInt() and 65535) + (input.short.toInt() and 65535) + (input.short.toInt() and 65535)
        require(questions <= 32 && records <= 256)
        fun nameAt(start: Int): Pair<String, Int> {
            var position = start
            var end = -1
            val labels = mutableListOf<String>()
            repeat(128) {
                require(position in packet.indices)
                val length = packet[position].toInt() and 255
                if (length == 0) return labels.joinToString(".") to if (end >= 0) end else position + 1
                if (length and 192 == 192) {
                    require(position + 1 < packet.size)
                    if (end < 0) end = position + 2
                    position = ((length and 63) shl 8) or (packet[position + 1].toInt() and 255)
                } else {
                    require(length <= 63 && position + 1 + length <= packet.size)
                    labels += String(packet, position + 1, length, Charsets.UTF_8)
                    position += 1 + length
                }
            }
            error("DNS name cycle")
        }
        repeat(questions) { input.position(nameAt(input.position()).second + 4) }
        repeat(records) {
            val name = nameAt(input.position())
            input.position(name.second)
            val type = input.short.toInt() and 65535
            input.short; input.int
            val length = input.short.toInt() and 65535
            require(length <= input.remaining())
            if (type == 12 && name.first.equals("_androidtvremote2._tcp.local", ignoreCase = true)) {
                return nameAt(input.position()).first.substringBefore("._androidtvremote2").take(128)
            }
            input.position(input.position() + length)
        }
        null
    }.getOrNull()
}
