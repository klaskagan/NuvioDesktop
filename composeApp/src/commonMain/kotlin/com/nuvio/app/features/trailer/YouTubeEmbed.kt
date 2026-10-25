package com.nuvio.app.features.trailer

import androidx.compose.runtime.Composable

/** Experimental macOS backend. Other platforms keep their existing player. */
internal expect object YouTubeEmbed {
    val supported: Boolean
    @Composable fun Popup(
        videoUrl: String?, title: String, type: String, contentTitle: String,
        loading: Boolean, error: String?, onDismiss: () -> Unit, onRetry: (() -> Unit)?,
    )
}

internal fun youtubeEmbedVideoId(url: String): String? {
    val candidate = when {
        url.matches(Regex("[A-Za-z0-9_-]{11}")) -> url
        url.startsWith("https://www.youtube.com/watch?") || url.startsWith("https://youtube.com/watch?") ->
            url.substringAfter('?').split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=')
        url.startsWith("https://youtu.be/") -> url.substringAfter("https://youtu.be/").substringBefore('?').substringBefore('/')
        else -> null
    }
    return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}
