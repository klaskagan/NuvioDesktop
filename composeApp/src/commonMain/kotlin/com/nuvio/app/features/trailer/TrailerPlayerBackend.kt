package com.nuvio.app.features.trailer

/** Applies to explicitly opened trailers; hero and card previews keep their own renderer. */
enum class TrailerPlayerBackend(val storedValue: String) {
    YouTube("youtube"),
    Native("native");

    companion object {
        fun parse(value: String?): TrailerPlayerBackend = entries.firstOrNull { it.storedValue == value } ?: YouTube
    }
}

internal fun effectiveTrailerPlayerBackend(preferred: TrailerPlayerBackend, youtubeSupported: Boolean): TrailerPlayerBackend =
    if (preferred == TrailerPlayerBackend.YouTube && youtubeSupported) TrailerPlayerBackend.YouTube else TrailerPlayerBackend.Native
