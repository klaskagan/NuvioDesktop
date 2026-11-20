package com.nuvio.app.features.handoff

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Blocking stream framing for the shared Android TV Remote v2 wire format. */
internal fun RemoteWire.writeFrame(output: OutputStream, message: ByteArray) {
    output.write(frame(message))
    output.flush()
}

internal fun RemoteWire.readFrame(input: InputStream): ByteArray {
    val length = try { requireFrameLength(readVarint { input.read() }) } catch (_: TvHandoffEndOfStream) { throw EOFException() }
    return input.readNBytes(length).also { require(it.size == length) }
}
