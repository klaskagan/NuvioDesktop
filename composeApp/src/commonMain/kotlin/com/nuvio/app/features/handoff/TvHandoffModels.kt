package com.nuvio.app.features.handoff

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun newTvHandoffId(): String = Uuid.random().toString()

@Serializable
internal data class PairedTv(
    val id: String = newTvHandoffId(),
    val name: String,
    val platform: String,
    val host: String,
    val mac: String = "",
    val appId: String,
    val certificatePin: String,
    val clientKey: String = "",
) {
    override fun toString() = name
}

@Serializable
internal data class DiscoveredTv(
    val name: String,
    val host: String,
    val platform: String,
    val previouslySeen: Boolean = false,
) {
    override fun toString() = "$name ($host)"
}

internal data class WebOsPairing(val clientKey: String, val certificatePin: String)

internal fun tvWakePacket(mac: String): ByteArray {
    require(mac.matches(Regex("([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}"))) {
        "Enter the TV network adapter's MAC address, such as AA:BB:CC:DD:EE:FF."
    }
    val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }.toByteArray()
    require(bytes.any { it != 0.toByte() } && bytes.first().toInt() and 1 == 0)
    return ByteArray(6) { 0xff.toByte() } + ByteArray(96) { bytes[it % 6] }
}

/** Accepts only literal private/link-local IPv4 addresses; never resolves names. */
internal fun requirePrivateTvAddress(host: String): String {
    require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) { "Enter the TV's local IPv4 address, without a port." }
    val octets = host.split('.').map { it.toInt() }
    require(octets.all { it in 0..255 }) { "Enter the TV's local IPv4 address, without a port." }
    val (a, b) = octets
    val private = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
    require(private) { "TV must be on your local network." }
    return octets.joinToString(".")
}

internal fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

/** java.net.URLEncoder-compatible form encoding, so receivers decode identical links on every sender. */
internal fun formUrlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 255
        val char = code.toChar()
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in ".-*_" -> append(char)
            char == ' ' -> append('+')
            else -> append('%').append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

internal fun androidTvHandoffLink(appId: String, launch: String): String {
    val scheme = when (appId) {
        "space.nuvio.nativelegacy.strictdebug" -> "nuvio-legacy-handoff-debug"
        "space.nuvio.nativelegacy" -> "nuvio-legacy-handoff"
        else -> "nuvio-handoff"
    }
    return "$scheme://handoff?launch=" + formUrlEncode(launch)
}

internal fun isNativeLegacyApp(appId: String): Boolean =
    appId == "space.nuvio.native.legacy" || appId.startsWith("space.nuvio.nativelegacy")

/** Advertised services plus explicit previous addresses; never scans a subnet. */
internal fun mergeKnownTvs(
    found: List<DiscoveredTv>,
    known: List<DiscoveredTv>,
    reachable: (DiscoveredTv) -> Boolean,
): List<DiscoveredTv> {
    val advertised = found.distinctBy { it.platform to it.host }
    return (advertised + known.distinctBy { it.platform to it.host }.filter { tv ->
        runCatching { requirePrivateTvAddress(tv.host) }.isSuccess &&
            advertised.none { it.host == tv.host && it.platform == tv.platform } && reachable(tv)
    }.map { it.copy(previouslySeen = true) }).sortedBy { it.name }
}

internal fun ssdpTv(response: String, host: String): DiscoveredTv? = runCatching {
    requirePrivateTvAddress(host)
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

/** Parse DNS labels with bounded pointer traversal, ignoring malformed network responses. */
internal fun androidTvServiceName(packet: ByteArray): String? = runCatching {
    require(packet.size >= 12)
    fun u16(at: Int): Int {
        require(at + 1 < packet.size)
        return ((packet[at].toInt() and 255) shl 8) or (packet[at + 1].toInt() and 255)
    }
    val questions = u16(4)
    val records = u16(6) + u16(8) + u16(10)
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
                labels += packet.decodeToString(position + 1, position + 1 + length)
                position += 1 + length
            }
        }
        error("DNS name cycle")
    }
    var cursor = 12
    repeat(questions) { cursor = nameAt(cursor).second + 4 }
    repeat(records) {
        val name = nameAt(cursor)
        cursor = name.second
        val type = u16(cursor)
        val length = u16(cursor + 8)
        cursor += 10
        require(length <= packet.size - cursor)
        if (type == 12 && name.first.equals("_androidtvremote2._tcp.local", ignoreCase = true)) {
            return nameAt(cursor).first.substringBefore("._androidtvremote2").take(128)
        }
        cursor += length
    }
    null
}.getOrNull()
