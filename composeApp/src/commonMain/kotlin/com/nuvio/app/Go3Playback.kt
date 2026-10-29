package com.nuvio.app

/** Desktop installs this handler; unsupported platforms retain ordinary URL handling. */
object Go3Playback {
    var fullscreenRequested: Boolean = false
    var open: ((String) -> Boolean)? = null
    fun isWatchUrl(value: String): Boolean = Regex("https://go3\\.lt/(watch|live_tv)/[^/?#]+,(live|programme|vod)-[0-9]+(/[^/?#]+,programme-[0-9]+)?").matches(value)
}
