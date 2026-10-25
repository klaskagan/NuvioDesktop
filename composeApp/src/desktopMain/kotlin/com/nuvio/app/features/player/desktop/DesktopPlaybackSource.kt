package com.nuvio.app.features.player.desktop

import okhttp3.HttpUrl.Companion.toHttpUrl

/** One lifetime for the local range adapter and both tracks of a native playback session. */
internal class DesktopPlaybackSource(
    videoUrl: String,
    audioUrl: String?,
    youtubeChunked: Boolean,
    headers: Map<String, String>,
) : AutoCloseable {
    private val proxy = if (youtubeChunked &&
        listOfNotNull(videoUrl, audioUrl).any(DesktopYoutubeStreamProxy::supports)
    ) DesktopYoutubeStreamProxy(headers) else null

    private fun prepare(url: String): String =
        if (proxy != null && DesktopYoutubeStreamProxy.supports(url)) proxy.register(url.toHttpUrl()) else url

    val videoUrl = prepare(videoUrl)
    val audioUrl = audioUrl?.takeIf(String::isNotBlank)?.let(::prepare)

    override fun close() { proxy?.close() }
}

/** mpv's native EDL combines concurrent tracks without changing the JNI ABI on any desktop OS. */
internal fun nativePlaybackUrl(videoUrl: String, audioUrl: String?): String {
    if (audioUrl.isNullOrBlank()) return videoUrl
    fun quote(url: String) = "%${url.toByteArray(Charsets.UTF_8).size}%$url"
    return "edl://!no_clip;${quote(videoUrl)};!new_stream;!no_clip;${quote(audioUrl)}"
}
