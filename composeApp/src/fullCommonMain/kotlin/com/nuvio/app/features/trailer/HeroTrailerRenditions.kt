package com.nuvio.app.features.trailer

internal fun rankHeroRenditions(renditions: List<HeroTrailerRendition>): List<HeroTrailerRendition> =
    renditions.distinctBy { it.videoUrl to it.audioUrl }.sortedWith(
        compareByDescending<HeroTrailerRendition> {
            // A scope crop retains the width of its standard 16:9 tier.
            maxOf(it.height, (it.width * 9L / 16L).toInt())
        }.thenByDescending { it.pinnedPlaylist != null }
            .thenByDescending { it.bandwidth },
    )

private val hlsUriAttribute = Regex("""([:,]\s*URI=)"([^"]+)"""")

internal fun parseHeroHlsRenditions(
    baseUrl: String,
    body: String,
    supportsCodecs: (String?) -> Boolean = { true },
): List<HeroTrailerRendition> {
    val lines = body.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    if (lines.firstOrNull() != "#EXTM3U") return emptyList()
    val audio = lines.filter { it.startsWith("#EXT-X-MEDIA:") }
        .filter { parseHlsAttributeList(it)["TYPE"] == "AUDIO" }
        .groupBy { parseHlsAttributeList(it)["GROUP-ID"] }

    return lines.mapIndexedNotNull { index, streamTag ->
        if (!streamTag.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
        val path = lines.getOrNull(index + 1)?.takeUnless { it.startsWith("#") }
            ?: return@mapIndexedNotNull null
        val url = resolveHlsReference(baseUrl, path)
        val attributes = parseHlsAttributeList(streamTag)
        val codecs = attributes["CODECS"]
        if (!supportsCodecs(codecs)) return@mapIndexedNotNull null
        val resolution = attributes["RESOLUTION"].orEmpty()
        val width = resolution.substringBefore('x', "").toIntOrNull() ?: 0
        val height = resolution.substringAfter('x', "").toIntOrNull() ?: 0
        val audioGroup = attributes["AUDIO"]?.let { audio[it] ?: return@mapIndexedNotNull null }.orEmpty()
        // Keep only references that are present in the reduced multivariant playlist.
        val pinnedTag = streamTag.replace(Regex(""",(SUBTITLES|VIDEO|CLOSED-CAPTIONS)="[^"]*""""), "")
        val playlist = buildString {
            appendLine("#EXTM3U")
            lines.filter { it.startsWith("#EXT-X-VERSION:") || it == "#EXT-X-INDEPENDENT-SEGMENTS" }
                .forEach { appendLine(it) }
            for (tag in audioGroup) {
                appendLine(hlsUriAttribute.replace(tag) {
                    it.groupValues[1] + "\"" + resolveHlsReference(baseUrl, it.groupValues[2]) + "\""
                })
            }
            appendLine(pinnedTag)
            appendLine(url)
        }
        HeroTrailerRendition(
            videoUrl = url,
            width = width,
            height = height,
            bandwidth = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0,
            pinnedPlaylist = playlist,
            codecs = codecs,
        )
    }
}

private fun resolveHlsReference(baseUrl: String, reference: String): String {
    if (reference.startsWith("https://") || reference.startsWith("http://")) return reference
    val scheme = baseUrl.substringBefore("://")
    if (reference.startsWith("//")) return "$scheme:$reference"
    val base = baseUrl.substringBefore('#').substringBefore('?')
    val origin = "$scheme://${base.substringAfter("://").substringBefore('/')}"
    if (reference.startsWith('?')) return base + reference
    val path = if (reference.startsWith('/')) reference else base.removePrefix(origin).substringBeforeLast('/', "") + "/" + reference
    // Resolve dot segments before handing the URL to the native media renderer.
    val pathOnly = path.substringBefore('?').substringBefore('#')
    val segments = mutableListOf<String>()
    for (segment in pathOnly.removePrefix("/").split('/')) {
        when (segment) {
            "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }
    return origin + "/" + segments.joinToString("/") + path.removePrefix(pathOnly)
}

internal fun parseHlsAttributeList(line: String): Map<String, String> {
    val index = line.indexOf(':')
    if (index == -1) return emptyMap()

    val raw = line.substring(index + 1)
    val out = LinkedHashMap<String, String>()
    val key = StringBuilder()
    val value = StringBuilder()
    var inKey = true
    var inQuote = false

    for (ch in raw) {
        if (inKey) {
            if (ch == '=') {
                inKey = false
            } else {
                key.append(ch)
            }
            continue
        }

        if (ch == '"') {
            inQuote = !inQuote
            continue
        }

        if (ch == ',' && !inQuote) {
            val parsedKey = key.toString().trim()
            if (parsedKey.isNotEmpty()) {
                out[parsedKey] = value.toString().trim()
            }
            key.clear()
            value.clear()
            inKey = true
            continue
        }

        value.append(ch)
    }

    val lastKey = key.toString().trim()
    if (lastKey.isNotEmpty()) {
        out[lastKey] = value.toString().trim()
    }

    return out
}
