package com.nuvio.app.features.trailer

import java.net.URI
import java.net.URLDecoder

internal sealed interface YouTubeLinkDestination {
    data class Embedded(val videoId: String) : YouTubeLinkDestination
    data class Browser(val url: String) : YouTubeLinkDestination
    data object Ignore : YouTubeLinkDestination
}

/** Only user-activated http(s) links reach this policy; never execute page URL schemes. */
internal fun routeYouTubeLink(url: String, currentId: String, recommendation: Boolean = false, external: Boolean = false): YouTubeLinkDestination {
    val uri = runCatching { URI(url) }.getOrNull() ?: return YouTubeLinkDestination.Ignore
    if (uri.scheme?.lowercase() !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return YouTubeLinkDestination.Ignore
    val host = uri.host.lowercase()
    val id = when {
        host == "youtu.be" -> uri.path.trim('/').substringBefore('/')
        host == "youtube.com" || host.endsWith(".youtube.com") -> when {
            uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "v" }
                ?.substringAfter('=')?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            uri.path.startsWith("/shorts/") || uri.path.startsWith("/embed/") -> uri.path.split('/').getOrNull(2)
            else -> null
        }
        else -> null
    }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    return if (!external && id != null && (recommendation || id != currentId)) YouTubeLinkDestination.Embedded(id)
    else YouTubeLinkDestination.Browser(uri.toASCIIString())
}
