package com.nuvio.app.features.handoff

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** The small protobuf subset used by Android TV Remote v2. Lengths are bounded before allocation. */
internal object RemoteWire {
    fun number(field: Int, value: Long): ByteArray = encodeVarint((field * 8).toLong()) + encodeVarint(value)
    fun bytes(field: Int, value: ByteArray): ByteArray =
        encodeVarint((field * 8 + 2).toLong()) + encodeVarint(value.size.toLong()) + value
    fun text(field: Int, value: String): ByteArray = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun encodeVarint(value: Long): ByteArray {
        require(value >= 0)
        val output = ByteArrayOutputStream()
        var remaining = value
        while (remaining >= 128) {
            output.write(((remaining and 127) or 128).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
        return output.toByteArray()
    }

    fun readVarint(input: InputStream): Long {
        var value = 0L
        repeat(9) { index ->
            val next = input.read()
            if (next < 0) throw EOFException()
            value = value or ((next and 127).toLong() shl (index * 7))
            if (next and 128 == 0) return value
        }
        error("Invalid TV protocol integer")
    }

    fun writeFrame(output: OutputStream, message: ByteArray) {
        require(message.size <= 65_536)
        output.write(encodeVarint(message.size.toLong()))
        output.write(message)
        output.flush()
    }

    fun readFrame(input: InputStream): ByteArray {
        val length = readVarint(input)
        require(length in 0..65_536) { "TV message too large" }
        return input.readNBytes(length.toInt()).also { require(it.size == length.toInt()) }
    }

    data class Field(val number: Int, val value: Long? = null, val bytes: ByteArray? = null)
    fun fields(message: ByteArray): List<Field> {
        val input = ByteArrayInputStream(message)
        val result = mutableListOf<Field>()
        while (input.available() > 0) {
            val tag = readVarint(input)
            require(tag ushr 3 in 1..536_870_911)
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> result += Field(field, value = readVarint(input))
                2 -> {
                    val length = readVarint(input)
                    require(length <= input.available())
                    result += Field(field, bytes = input.readNBytes(length.toInt()))
                }
                1 -> require(input.skip(8) == 8L)
                5 -> require(input.skip(4) == 4L)
                else -> error("Unsupported TV message encoding")
            }
        }
        return result
    }
}
