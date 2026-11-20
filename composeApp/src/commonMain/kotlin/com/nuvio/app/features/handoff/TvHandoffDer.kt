package com.nuvio.app.features.handoff

/** Minimal DER for RSA keys and a self-signed sender certificate on platforms without an X.509 builder. */
internal object TvHandoffDer {
    private val rsaEncryption = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01)
    private val sha256WithRsa = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b)
    private val commonName = byteArrayOf(0x55, 0x04, 0x03)

    fun element(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + length(content.size) + content
    fun sequence(vararg parts: ByteArray): ByteArray = element(0x30, parts.fold(ByteArray(0)) { acc, part -> acc + part })
    private fun length(size: Int): ByteArray = when {
        size < 128 -> byteArrayOf(size.toByte())
        size < 256 -> byteArrayOf(0x81.toByte(), size.toByte())
        size < 65_536 -> byteArrayOf(0x82.toByte(), (size shr 8).toByte(), size.toByte())
        else -> byteArrayOf(0x83.toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte())
    }

    /** A positive INTEGER from an unsigned big-endian magnitude. */
    fun unsignedInteger(magnitude: ByteArray): ByteArray {
        val trimmed = magnitude.dropWhile { it == 0.toByte() }.toByteArray().let { if (it.isEmpty()) byteArrayOf(0) else it }
        return element(0x02, if (trimmed.first().toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed)
    }

    private fun algorithm(oid: ByteArray) = sequence(element(0x06, oid), byteArrayOf(0x05, 0x00))

    fun subjectPublicKeyInfo(pkcs1: ByteArray): ByteArray =
        sequence(algorithm(rsaEncryption), element(0x03, byteArrayOf(0) + pkcs1))

    /** Reads an RSAPublicKey (PKCS#1) into unsigned modulus/exponent magnitudes. */
    fun rsaPublicKey(pkcs1: ByteArray): RsaPublicKeyParts {
        val reader = Reader(pkcs1)
        val body = Reader(reader.read(0x30))
        fun unsigned(value: ByteArray) =
            if (value.size > 1 && value.first() == 0.toByte()) value.copyOfRange(1, value.size) else value
        return RsaPublicKeyParts(unsigned(body.read(0x02)), unsigned(body.read(0x02)))
    }

    /** RSAPublicKey from a SubjectPublicKeyInfo, as Java's PublicKey.encoded returns it. */
    fun pkcs1FromSubjectPublicKeyInfo(spki: ByteArray): ByteArray {
        val body = Reader(Reader(spki).read(0x30))
        body.read(0x30)
        val bits = body.read(0x03)
        require(bits.isNotEmpty() && bits.first() == 0.toByte())
        return bits.copyOfRange(1, bits.size)
    }

    fun tbsCertificate(
        commonNameValue: String,
        serial: ByteArray,
        notBeforeEpochMs: Long,
        notAfterEpochMs: Long,
        pkcs1PublicKey: ByteArray,
    ): ByteArray {
        val name = sequence(element(0x31, sequence(element(0x06, commonName), element(0x0c, commonNameValue.encodeToByteArray()))))
        return sequence(
            element(0xa0, element(0x02, byteArrayOf(2))),
            unsignedInteger(serial),
            algorithm(sha256WithRsa),
            name,
            sequence(utcTime(notBeforeEpochMs), utcTime(notAfterEpochMs)),
            name,
            subjectPublicKeyInfo(pkcs1PublicKey),
        )
    }

    fun certificate(tbs: ByteArray, signature: ByteArray): ByteArray =
        sequence(tbs, algorithm(sha256WithRsa), element(0x03, byteArrayOf(0) + signature))

    /** UTCTime (YYMMDDHHMMSSZ); valid for 1950–2049. */
    fun utcTime(epochMs: Long): ByteArray {
        val seconds = epochMs.floorDiv(1000L)
        val days = seconds.floorDiv(86_400L)
        val secondOfDay = seconds - days * 86_400L
        // Howard Hinnant's civil-from-days.
        val z = days + 719_468L
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        require(year in 1950..2049)
        fun two(value: Long) = value.toString().padStart(2, '0')
        val text = two(year % 100) + two(month) + two(day) + two(secondOfDay / 3600) +
            two(secondOfDay % 3600 / 60) + two(secondOfDay % 60) + "Z"
        return element(0x17, text.encodeToByteArray())
    }

    private class Reader(private val bytes: ByteArray) {
        private var position = 0
        fun read(tag: Int): ByteArray {
            require(position < bytes.size && bytes[position].toInt() and 255 == tag) { "Unexpected key encoding" }
            position++
            require(position < bytes.size)
            val first = bytes[position++].toInt() and 255
            val size = if (first < 128) first else {
                val count = first and 127
                require(count in 1..3 && position + count <= bytes.size)
                var value = 0
                repeat(count) { value = (value shl 8) or (bytes[position++].toInt() and 255) }
                value
            }
            require(size <= bytes.size - position)
            return bytes.copyOfRange(position, position + size).also { position += size }
        }
    }
}
