package com.nuvio.app

/** Desktop installs this handler; unsupported platforms retain ordinary URL handling. */
object Go3Playback {
    data class Request(val url: String, val programmeTitle: String? = null, val fullscreen: Boolean = false)

    var open: ((Request) -> Boolean)? = null
    fun isWatchUrl(value: String): Boolean = Regex("https://go3\\.lt/(?:lt/)?(watch|live_tv)/[^/?#]+,(live|programme|vod)-[0-9]+(/[^/?#]+,programme-[0-9]+)?").matches(value)
}
