package com.nuvio.app.core.logging

import co.touchlab.kermit.Logger

/**
 * Logging entry point for the Swift player ported from NuvioMobile-Enhanced. Enhanced keeps an
 * in-app log viewer behind this object; here it forwards to Kermit (Xcode/device console).
 */
object InAppLogBridge {
    fun debug(tag: String, message: String) = Logger.withTag(tag).d { message }
    fun info(tag: String, message: String) = Logger.withTag(tag).i { message }
    fun warn(tag: String, message: String) = Logger.withTag(tag).w { message }
    fun error(tag: String, message: String) = Logger.withTag(tag).e { message }

    fun mpv(platform: String, prefix: String, level: String, message: String) {
        val logger = Logger.withTag("MPV/$platform")
        val text = "[$prefix] ${message.trimEnd()}"
        when (level) {
            "fatal", "error" -> logger.e { text }
            "warn" -> logger.w { text }
            "info" -> logger.i { text }
            else -> logger.d { text }
        }
    }
}
