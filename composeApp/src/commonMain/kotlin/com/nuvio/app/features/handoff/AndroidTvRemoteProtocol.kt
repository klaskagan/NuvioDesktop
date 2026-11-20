package com.nuvio.app.features.handoff

/** The small protobuf subset used by Android TV Remote v2. Lengths are bounded before allocation. */
internal object RemoteWire {
    const val MAX_FRAME = 65_536

    fun number(field: Int, value: Long): ByteArray = encodeVarint((field * 8).toLong()) + encodeVarint(value)
    fun bytes(field: Int, value: ByteArray): ByteArray =
        encodeVarint((field * 8 + 2).toLong()) + encodeVarint(value.size.toLong()) + value
    fun text(field: Int, value: String): ByteArray = bytes(field, value.encodeToByteArray())

    fun encodeVarint(value: Long): ByteArray {
        require(value >= 0)
        val output = ArrayList<Byte>(10)
        var remaining = value
        while (remaining >= 128) {
            output += ((remaining and 127) or 128).toByte()
            remaining = remaining ushr 7
        }
        output += remaining.toByte()
        return output.toByteArray()
    }

    /** [next] returns the next unsigned byte, or a negative value at end of stream. */
    inline fun readVarint(next: () -> Int): Long {
        var value = 0L
        for (index in 0 until 9) {
            val byte = next()
            if (byte < 0) throw TvHandoffEndOfStream()
            value = value or ((byte and 127).toLong() shl (index * 7))
            if (byte and 128 == 0) return value
        }
        error("Invalid TV protocol integer")
    }

    fun frame(message: ByteArray): ByteArray {
        require(message.size <= MAX_FRAME)
        return encodeVarint(message.size.toLong()) + message
    }

    fun requireFrameLength(length: Long): Int {
        require(length in 0..MAX_FRAME.toLong()) { "TV message too large" }
        return length.toInt()
    }

    data class Field(val number: Int, val value: Long? = null, val bytes: ByteArray? = null)

    fun fields(message: ByteArray): List<Field> {
        var position = 0
        val next = { if (position < message.size) message[position++].toInt() and 255 else -1 }
        val result = mutableListOf<Field>()
        while (position < message.size) {
            val tag = readVarint(next)
            require(tag ushr 3 in 1..536_870_911)
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> result += Field(field, value = readVarint(next))
                2 -> {
                    val length = readVarint(next)
                    require(length <= message.size - position)
                    result += Field(field, bytes = message.copyOfRange(position, position + length.toInt()))
                    position += length.toInt()
                }
                1 -> { require(message.size - position >= 8); position += 8 }
                5 -> { require(message.size - position >= 4); position += 4 }
                else -> error("Unsupported TV message encoding")
            }
        }
        return result
    }
}

internal class TvHandoffEndOfStream : Exception("TV closed the connection")
internal class TvHandoffTimeout(message: String) : Exception(message)
internal class TvHandoffIdentityFailure(message: String) : Exception(message)

/** Android TV Remote v2 pairing (port 6467) messages and validation, shared by every sender. */
internal object AndroidTvPairing {
    fun message(field: Int, value: ByteArray): ByteArray =
        RemoteWire.number(1, 2) + RemoteWire.number(2, 200) + RemoteWire.bytes(field, value)

    fun requireResponse(message: ByteArray, expected: Int) {
        val fields = RemoteWire.fields(message)
        check(fields.any { it.number == 2 && it.value == 200L } && fields.any { it.number == expected }) {
            "The TV rejected pairing. Start pairing again."
        }
    }

    private val encoding = RemoteWire.number(1, 3) + RemoteWire.number(2, 6)
    fun request(clientName: String) = message(10, RemoteWire.text(1, "atvremote") + RemoteWire.text(2, clientName))
    fun options() = message(20, RemoteWire.bytes(1, encoding) + RemoteWire.number(3, 1))
    fun configuration() = message(30, RemoteWire.bytes(1, encoding) + RemoteWire.number(2, 1))
    fun secret(secret: ByteArray) = message(40, RemoteWire.bytes(1, secret))

    fun normalizedCode(code: String): String = code.trim().also {
        require(it.matches(Regex("[0-9A-Fa-f]{6}"))) { "Enter the six-character code shown on your TV." }
    }

    /** RSA components are unsigned big-endian magnitudes without a leading sign byte. */
    fun secretFor(
        client: RsaPublicKeyParts,
        server: RsaPublicKeyParts,
        code: String,
        sha256: (ByteArray) -> ByteArray,
    ): ByteArray {
        val normalized = normalizedCode(code)
        val secret = sha256(
            client.modulus + client.exponent + server.modulus + server.exponent +
                normalized.drop(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )
        require(secret.first().toInt() and 255 == normalized.take(2).toInt(16)) { "Pairing code did not match this TV." }
        return secret
    }
}

internal class RsaPublicKeyParts(val modulus: ByteArray, val exponent: ByteArray)

/** Android TV Remote v2 control channel (port 6466): replies, power state and app links. */
internal class AndroidTvRemoteSession(private val clientName: String = "nuvio") {
    private var features = 551L
    var reportedPower: Boolean? = null
        private set

    fun resetPower() { reportedPower = null }

    /** Returns replies to send and whether the TV reported its power state (the handshake is complete). */
    fun process(message: ByteArray): Pair<List<ByteArray>, Boolean> {
        val fields = RemoteWire.fields(message)
        if (fields.any { it.number == 40 }) reportedPower = androidTvReportedPower(fields)
        val replies = fields.mapNotNull { field ->
            val body = field.bytes ?: return@mapNotNull null
            when (field.number) {
                1 -> {
                    val supported = RemoteWire.fields(body).firstOrNull { it.number == 1 }?.value ?: 0
                    features = supported and 551L // ping, keys, IME, power, app links
                    check(features and 512L != 0L) { "Sony remote service does not support app links." }
                    RemoteWire.bytes(1, RemoteWire.number(1, features) + RemoteWire.bytes(2,
                        RemoteWire.number(3, 1) + RemoteWire.text(4, "1") +
                            RemoteWire.text(5, clientName) + RemoteWire.text(6, "1.0")))
                }
                2 -> RemoteWire.bytes(2, RemoteWire.number(1, features))
                8 -> RemoteWire.bytes(9, RemoteWire.number(1,
                    RemoteWire.fields(body).firstOrNull { it.number == 1 }?.value ?: 0))
                else -> null
            }
        }
        return replies to fields.any { it.number == 40 }
    }

    fun wakeKey(): ByteArray =
        RemoteWire.bytes(10, RemoteWire.number(1, androidTvWakeKey(reportedPower).toLong()) + RemoteWire.number(2, 3))

    fun appLink(link: String): ByteArray = RemoteWire.bytes(90, RemoteWire.text(1, link))
}

/** POWER is a toggle: use it only after this authenticated handshake reports off. */
internal fun androidTvWakeKey(reportedPower: Boolean?): Int = if (reportedPower == false) 26 else 224

internal fun androidTvReportedPower(fields: List<RemoteWire.Field>): Boolean? = try {
    val body = fields.singleOrNull { it.number == 40 }?.bytes
    if (body == null) null else {
        val values = RemoteWire.fields(body).filter { it.number == 1 }
        val value = when (values.size) { 0 -> 0L; 1 -> values.single().value; else -> null }
        when (value) { 0L -> false; 1L -> true; else -> null }
    }
} catch (_: Exception) { null }
