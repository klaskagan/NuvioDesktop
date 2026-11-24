package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridStreamAudioChannel
import com.nuvio.app.features.debrid.DebridStreamAudioTag
import com.nuvio.app.features.debrid.DebridStreamEncode
import com.nuvio.app.features.debrid.DebridStreamLanguage
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamQuality
import com.nuvio.app.features.debrid.DebridStreamResolution
import com.nuvio.app.features.debrid.DebridStreamVisualTag
import com.nuvio.app.features.player.quickQualityAvailability

/** How soon a source can start, as the sheet's status chip shows it. Ordered best first. */
internal enum class StreamAvailability {
    /** Cached on a debrid service: starts at once. */
    Instant,

    /** A file on the user's own media server. */
    Server,

    /** A direct link from an addon. */
    Direct,

    /** The debrid service is still being asked whether it has the file. */
    Checking,

    /** The debrid service does not have the file yet and must download it before it can play. */
    Download,

    /** A torrent that would have to download first. */
    Torrent,
}

/**
 * One source as the desktop Sources sheet and Quick pick show it: the release decoded into plain
 * labels, with the addon's own text kept as [rawName] for anyone who wants to read it.
 */
internal data class StreamRowInfo(
    val stream: StreamItem,
    val resolution: DebridStreamResolution,
    /** Source, HDR, audio and codec, in that order; only what the release states. */
    val labels: List<String>,
    val availability: StreamAvailability,
    /** The debrid service or media server behind [availability], when there is one. */
    val service: String?,
    val sizeBytes: Long?,
    val seeds: Int?,
    /** Audio languages as upper-case codes ("RU", "EN"), from the addon's flags or its text. */
    val languages: List<String>,
    /** Voice-over and subtitle details from the addon's description: "RU Dub", "MVO", "Subs RU EN". */
    val audioTags: List<String>,
    val rawName: String,
    /** How many identical copies of this source the addon listed; shown as "×N" when above one. */
    val duplicateCount: Int = 1,
    /**
     * What tells this row apart from another of the same release name (tracker, seeds or size), or
     * null when its name is unique.
     */
    val distinguisher: String? = null,
)

private val sizeRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(TB|GB|MB)\b""", RegexOption.IGNORE_CASE)
private val seedsRegex = Regex("""(?:👤|👥|seed(?:er)?s?:?)\s*(\d+)""", RegexOption.IGNORE_CASE)
private val neutralPreferences = DebridStreamPreferences()

// Release names often glue these to a neighbour ("DDP5.1", "H.265"), which token matching misses.
private val channelsRegex = Regex("""(?<![\d.])([257])\.([01])(?![\d])(?!\s*(?:TB|GB|MB))""", RegexOption.IGNORE_CASE)
private val hevcRegex = Regex("""(?<![a-z0-9])(?:h[. ]?265|x265|hevc)(?![a-z0-9])""", RegexOption.IGNORE_CASE)
private val avcRegex = Regex("""(?<![a-z0-9])(?:h[. ]?264|x264|avc)(?![a-z0-9])""", RegexOption.IGNORE_CASE)

// Addons mark a debrid source "[TB+]" (cached) or "[TB download]" (not cached yet).
private val serviceMarkerRegex = Regex("""\[([A-Za-z0-9]{2,5})(?:\+|\s+download)]""", RegexOption.IGNORE_CASE)

// A release that states its resolution outright ("UHD.BDRip.1080p") is that resolution, whatever
// a "UHD" or "4K" tag beside it suggests.
private val explicitResolutionRegex = Regex("""(?<![\p{L}\p{N}])(2160|1440|1080|720|576|480|360)[pi](?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val bdRipRegex = Regex("""(?<![\p{L}\p{N}])(?:BD|BR)-?Rip(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val remuxRegex = Regex("""(?<![\p{L}\p{N}])Remux(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

/** The source label, read from the release itself where the parser's quality is too broad. */
internal fun streamSourceLabel(text: String, quality: DebridStreamQuality): String? = when {
    remuxRegex.containsMatchIn(text) -> "Remux"
    bdRipRegex.containsMatchIn(text) -> "BDRip"
    quality != DebridStreamQuality.UNKNOWN -> quality.label
    else -> null
}

internal fun streamResolution(text: String, parsed: DebridStreamResolution): DebridStreamResolution {
    val stated = explicitResolutionRegex.findAll(text).map { it.groupValues[1].toInt() }.toSet().singleOrNull()
        ?: return parsed
    return DebridStreamResolution.entries.firstOrNull { it.value == stated } ?: parsed
}

private val flagLanguages = mapOf(
    "GB" to "EN", "US" to "EN", "AU" to "EN", "CA" to "EN", "RU" to "RU", "UA" to "UK", "PL" to "PL", "DE" to "DE",
    "AT" to "DE", "FR" to "FR", "ES" to "ES", "MX" to "ES", "IT" to "IT", "PT" to "PT", "BR" to "PT", "JP" to "JA",
    "KR" to "KO", "CN" to "ZH", "IN" to "HI", "LT" to "LT", "LV" to "LV", "EE" to "ET", "CZ" to "CS", "TR" to "TR",
    "NL" to "NL", "SE" to "SV", "NO" to "NO", "DK" to "DA", "FI" to "FI", "GR" to "EL", "HU" to "HU", "RO" to "RO",
)

/** Audio languages an addon shows as a line of flags ("🇬🇧 / 🇷🇺 / 🇺🇦"), as upper-case codes. */
internal fun streamLanguagesFromFlags(text: String): List<String> {
    // A flag is two regional-indicator letters, each the surrogate pair D83C + DDE6..DDFF. Walked by
    // hand: a regex over lone surrogates does not match them inside a valid pair on every platform.
    fun letterAt(index: Int): Char? {
        if (index + 1 >= text.length || text[index].code != 0xD83C) return null
        val low = text[index + 1].code
        return if (low in 0xDDE6..0xDDFF) 'A' + (low - 0xDDE6) else null
    }
    val found = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val first = letterAt(index)
        val second = if (first != null) letterAt(index + 2) else null
        if (first != null && second != null) {
            flagLanguages["$first$second"]?.let { found += it }
            index += 4
        } else {
            index++
        }
    }
    return found.distinct()
}

// The words trackers use for a language inside a description ("Dub (Rus)", "+ Ukr +").
private val languageWords = mapOf(
    "rus" to "RU", "russian" to "RU", "ru" to "RU", "ukr" to "UK", "ukrainian" to "UK", "ua" to "UK",
    "eng" to "EN", "english" to "EN", "en" to "EN", "pol" to "PL", "polish" to "PL", "pl" to "PL",
    "ger" to "DE", "deu" to "DE", "german" to "DE", "fre" to "FR", "fra" to "FR", "french" to "FR",
    "spa" to "ES", "esp" to "ES", "spanish" to "ES", "ita" to "IT", "italian" to "IT", "lit" to "LT",
    "jpn" to "JA", "japanese" to "JA", "kor" to "KO", "korean" to "KO",
)
private val wordRegex = Regex("""[\p{L}]+""")
private fun languageCodesIn(text: String?): List<String> =
    wordRegex.findAll(text.orEmpty()).mapNotNull { languageWords[it.value.lowercase()] }.distinct().toList()

private val subtitlesRegex = Regex("""(?<![\p{L}\p{N}])subs?(?![\p{L}\p{N}])\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
private val voiceRegex = Regex(
    """(?<![\p{L}\p{N}])(Dubbed|Dub|MVO|DVO|AVO|VO|Lektor|Original)(?![\p{L}\p{N}])(?:\s*\(([^)]*)\))?""",
    RegexOption.IGNORE_CASE,
)
private val videoFileRegex = Regex("""\.(mkv|mp4|avi|m4v|ts|mov|wmv)\s*$""", RegexOption.IGNORE_CASE)

/**
 * Voice-over and subtitle details from an addon's description, as short tags. A kind with its
 * language in brackets keeps it ("Dub (Rus)" → "RU Dub"); a bare kind stays bare, because guessing
 * its language would mislabel a source. A language standing alone between plus signs ("+ Ukr +")
 * is a tag of its own. Anything else in the description is left out.
 */
internal fun streamAudioTags(description: String): List<String> {
    val lines = description.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !videoFileRegex.containsMatchIn(it) && "👤" !in it && "💾" !in it }
        .toList()
    val tags = mutableListOf<String>()
    var subtitles: String? = null
    lines.forEach { line ->
        subtitlesRegex.find(line)?.let { match ->
            val codes = languageCodesIn(match.groupValues[1])
            if (codes.isNotEmpty() && subtitles == null) subtitles = "Subs " + codes.joinToString(" ")
        }
        val spoken = subtitlesRegex.replace(line, " ")
        voiceRegex.findAll(spoken).forEach { match ->
            val kind = when (val word = match.groupValues[1].lowercase()) {
                "dubbed", "dub" -> "Dub"
                "lektor" -> "Lektor"
                "original" -> "Original"
                else -> word.uppercase()
            }
            val codes = languageCodesIn(match.groupValues[2])
            if (codes.isEmpty()) tags += kind else codes.forEach { tags += "$it $kind" }
        }
        if ('+' in spoken) {
            spoken.split('+').forEach { part ->
                val word = part.trim().substringBefore('(').trim()
                languageWords[word.lowercase()]?.takeIf { word.length >= 3 }?.let { tags += it }
            }
        }
    }
    return (tags.distinct() + listOfNotNull(subtitles)).take(7)
}

/** The language's name for a filter pill ("RU" → "Russian"); the code itself when unknown. */
internal fun streamLanguageName(code: String): String =
    DebridStreamLanguage.entries.firstOrNull { it.code.equals(code, ignoreCase = true) }?.label ?: code

/** A size the addon only wrote into its text ("💾 6.59 GB"), in bytes. */
internal fun streamSizeFromText(text: String): Long? {
    val match = sizeRegex.find(text) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = when (match.groupValues[2].uppercase()) {
        "TB" -> 1024.0 * 1024 * 1024 * 1024
        "GB" -> 1024.0 * 1024 * 1024
        else -> 1024.0 * 1024
    }
    return (value * unit).toLong().takeIf { it > 0 }
}

internal fun streamSeedsFromText(text: String): Int? =
    seedsRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()

internal fun streamRowInfo(stream: StreamItem): StreamRowInfo {
    val facts = DebridStreamMetadata.facts(stream, neutralPreferences)
    val text = listOfNotNull(stream.name, stream.title, stream.description).joinToString("\n")
    // The parser lists a combined tag beside its parts (HDR+DV, then DV); one label says it.
    val visual = facts.visualTags.firstOrNull { it != DebridStreamVisualTag.SDR && it != DebridStreamVisualTag.UNKNOWN }?.let { tag ->
        when (tag) {
            DebridStreamVisualTag.HDR_DV -> "DV · HDR"
            DebridStreamVisualTag.DV_ONLY, DebridStreamVisualTag.DV -> "DV"
            DebridStreamVisualTag.HDR_ONLY, DebridStreamVisualTag.HDR -> "HDR"
            else -> tag.label
        }
    }.orEmpty()
    val audio = listOfNotNull(
        facts.audioTags.firstOrNull { it != DebridStreamAudioTag.UNKNOWN }?.label,
        facts.audioChannels.firstOrNull { it != DebridStreamAudioChannel.UNKNOWN }?.label
            ?: channelsRegex.find(text)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" },
    ).joinToString(" ")
    // One name per codec, whichever way the release spells it.
    val codec = facts.encode.takeIf { it != DebridStreamEncode.UNKNOWN }?.label?.let { if (it.equals("AVC", ignoreCase = true)) "H.264" else it } ?: when {
        hevcRegex.containsMatchIn(text) -> "HEVC"
        avcRegex.containsMatchIn(text) -> "H.264"
        else -> null
    }
    val cache = stream.debridCacheStatus
    // The addon's own marker counts as much as the app's cache check: "[TB+]" is cached on the
    // service, "[TB download]" has to be downloaded there first, even though both carry a link.
    val marker = quickQualityAvailability(stream)
    val availability = when {
        stream.serverTarget != null -> StreamAvailability.Server
        marker == 0 || stream.debridResolved -> StreamAvailability.Instant
        cache?.state == StreamDebridCacheState.CHECKING -> StreamAvailability.Checking
        marker == 2 -> StreamAvailability.Download
        stream.isTorrentStream -> StreamAvailability.Torrent
        else -> StreamAvailability.Direct
    }
    val service = when (availability) {
        StreamAvailability.Instant, StreamAvailability.Checking, StreamAvailability.Download ->
            cache?.let { status -> DebridProviders.shortName(status.providerId).ifBlank { status.providerName.trim() } }
                ?.takeIf { it.isNotBlank() }
                ?: serviceMarkerRegex.find(stream.name.orEmpty())?.groupValues?.get(1)?.uppercase()
        else -> null
    }
    val described = listOfNotNull(stream.title, stream.description).joinToString("\n")
    // A flag line states the audio languages outright; without one, fall back to what the text mentions.
    val languages = streamLanguagesFromFlags(text).ifEmpty { facts.languages.map { it.code.uppercase() }.distinct() }
    return StreamRowInfo(
        stream = stream,
        resolution = streamResolution(text, facts.resolution),
        labels = listOfNotNull(
            streamSourceLabel(text, facts.quality),
            visual.takeIf { it.isNotBlank() },
            audio.takeIf { it.isNotBlank() },
            codec,
        ),
        availability = availability,
        service = service,
        sizeBytes = facts.size ?: streamSizeFromText(text),
        seeds = streamSeedsFromText(text).takeIf {
            availability == StreamAvailability.Torrent || availability == StreamAvailability.Checking || availability == StreamAvailability.Download
        },
        languages = languages,
        // A bare language tag ("UK") repeats the row's language chip, so it is left out.
        audioTags = streamAudioTags(described).filterNot { tag -> languages.any { it.equals(tag, ignoreCase = true) } },
        rawName = stream.behaviorHints.filename?.takeIf { it.isNotBlank() }
            ?: (stream.description ?: stream.title ?: stream.name).orEmpty().lineSequence()
                .map { it.trim() }.filter { it.isNotEmpty() }.maxByOrNull { it.length }.orEmpty(),
    )
}

/**
 * Merges exact copies an addon lists more than once (same torrent file, or else the same link) into
 * the best-seeded one with a [StreamRowInfo.duplicateCount]. Rows that remain with the same release
 * name get a [StreamRowInfo.distinguisher]. Different addons' copies stay separate.
 */
internal fun mergeDuplicateStreams(rows: List<StreamRowInfo>): List<StreamRowInfo> {
    fun key(row: StreamRowInfo): String? {
        val stream = row.stream
        val source = stream.infoHash?.takeIf { it.isNotBlank() }?.let { "${it.lowercase()}:${stream.fileIdx ?: -1}" }
            ?: stream.url?.takeIf { it.isNotBlank() }
            ?: stream.externalUrl?.takeIf { it.isNotBlank() }
            ?: return null
        return "${stream.addonId}|$source"
    }
    val merged = LinkedHashMap<Any, StreamRowInfo>()
    rows.forEachIndexed { index, row ->
        val k: Any = key(row) ?: index
        val kept = merged[k]
        merged[k] = when {
            kept == null -> row
            (row.seeds ?: -1) > (kept.seeds ?: -1) -> row.copy(duplicateCount = kept.duplicateCount + 1)
            else -> kept.copy(duplicateCount = kept.duplicateCount + 1)
        }
    }
    val result = merged.values.toList()
    val sameName = result.groupBy { it.stream.addonId to it.rawName }.filter { it.key.second.isNotBlank() && it.value.size > 1 }
    if (sameName.isEmpty()) return result
    return result.map { row ->
        val siblings = sameName[row.stream.addonId to row.rawName] ?: return@map row
        val tracker = row.stream.sourceName?.trim()?.takeIf { it.isNotBlank() }
        row.copy(
            distinguisher = when {
                tracker != null && siblings.count { it.stream.sourceName?.trim() == tracker } == 1 -> tracker
                row.seeds != null && siblings.count { it.seeds == row.seeds } == 1 -> "${row.seeds} seeds"
                row.sizeBytes != null -> formatStreamSize(row.sizeBytes)
                else -> tracker
            },
        )
    }
}

internal enum class StreamSheetSort { Quality, Size, Seeds }

/** What the sheet's filter row narrows the list to. Null means no restriction. */
internal data class StreamSheetFilter(
    val resolution: DebridStreamResolution? = null,
    /** An upper-case language code; only sources with that audio language are listed. */
    val language: String? = null,
    val instantOnly: Boolean = false,
    val sort: StreamSheetSort = StreamSheetSort.Quality,
)

/** Starts now, without waiting for a download or a cache check. */
internal val StreamRowInfo.startsNow: Boolean
    get() = availability == StreamAvailability.Instant || availability == StreamAvailability.Server ||
        availability == StreamAvailability.Direct

/** Where the row's best language sits in [preferred] (lower is better); last when it has none of them. */
private fun StreamRowInfo.languageRank(preferred: List<String>): Int =
    preferred.indexOfFirst { wanted -> languages.any { it.equals(wanted, ignoreCase = true) } }.let { if (it < 0) Int.MAX_VALUE else it }

/**
 * Sources that start now come first, then the user's languages (so a 1080p in a preferred language
 * beats a 4K without it), then resolution.
 */
private fun bestFirst(preferred: List<String>): Comparator<StreamRowInfo> =
    compareBy<StreamRowInfo> { !it.startsNow }
        .thenBy { it.languageRank(preferred) }
        .thenByDescending { it.resolution.value }
        .thenBy { it.availability.ordinal }
        .thenByDescending { it.labels.size }
        .thenByDescending { it.seeds ?: 0 }
        .thenByDescending { it.sizeBytes ?: 0L }

/** The audio languages present, the user's [preferred] ones first, then the most common. */
internal fun streamLanguageChoices(rows: List<StreamRowInfo>, preferred: List<String> = emptyList()): List<String> {
    val counts = rows.flatMap { it.languages }.groupingBy { it }.eachCount()
    val wanted = preferred.map { it.uppercase() }
    return counts.keys.sortedWith(
        compareBy<String> { code -> wanted.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it } }.thenByDescending { counts[it] ?: 0 },
    )
}

/** The resolutions present, best first, for the filter's quality choices. */
internal fun streamResolutionChoices(rows: List<StreamRowInfo>): List<DebridStreamResolution> =
    rows.map { it.resolution }.distinct().sortedByDescending { it.value }

/**
 * The list as the sheet shows it: filtered, grouped by resolution (best first, unknown last), and
 * sorted inside each group. Quality order keeps sources that start now ahead of the rest.
 */
internal fun groupStreamRows(
    rows: List<StreamRowInfo>,
    filter: StreamSheetFilter,
    preferredLanguages: List<String> = emptyList(),
): List<Pair<DebridStreamResolution, List<StreamRowInfo>>> {
    val order: Comparator<StreamRowInfo> = when (filter.sort) {
        StreamSheetSort.Quality -> bestFirst(preferredLanguages)
        StreamSheetSort.Size -> compareByDescending { it.sizeBytes ?: 0L }
        StreamSheetSort.Seeds -> compareByDescending { it.seeds ?: 0 }
    }
    return rows
        .filter { filter.resolution == null || it.resolution == filter.resolution }
        .filter { filter.language == null || it.languages.any { code -> code.equals(filter.language, ignoreCase = true) } }
        .filter { !filter.instantOnly || it.startsNow }
        .groupBy { it.resolution }
        .entries
        .sortedByDescending { it.key.value }
        .map { it.key to it.value.sortedWith(order) }
}

/** The three choices Quick pick offers. Any of them can be missing; they never repeat a source. */
internal data class StreamQuickPicks(
    val best: StreamRowInfo?,
    val faster: StreamRowInfo?,
    val server: StreamRowInfo?,
)

/**
 * Best match is the top source by [bestFirst] among those [selectable], which puts sources that
 * start now and the user's [preferredLanguages] ahead of resolution. Faster start is the
 * smallest source that starts now at the next resolution down (1080p at most, so a 4K best match
 * offers 1080p and not 480p); failing that, a same-resolution source under half the size. On your
 * server is the first source from a media server.
 */
internal fun streamQuickPicks(
    rows: List<StreamRowInfo>,
    preferredLanguages: List<String> = emptyList(),
    selectable: (StreamItem) -> Boolean = { true },
): StreamQuickPicks {
    val usable = rows.filter { selectable(it.stream) }
    val server = usable.firstOrNull { it.availability == StreamAvailability.Server }
    val best = usable.filter { it !== server }.sortedWith(bestFirst(preferredLanguages)).firstOrNull() ?: server
    // A lighter alternative keeps the best match's language when it is one the user asked for.
    val bestRank = best?.languageRank(preferredLanguages) ?: Int.MAX_VALUE
    val lighter = usable.filter { it !== best && it !== server && it.startsNow && it.sizeBytes != null }
        .let { all -> all.filter { it.languageRank(preferredLanguages) <= bestRank }.ifEmpty { all }.takeIf { bestRank != Int.MAX_VALUE } ?: all }
    val nextDown = lighter
        .filter { best != null && it.resolution.value in 1 until best.resolution.value && it.resolution.value <= DebridStreamResolution.P1080.value }
        .maxOfOrNull { it.resolution.value }
    val faster = lighter.filter { it.resolution.value == nextDown }.minByOrNull { it.sizeBytes!! }
        ?: lighter.filter { best?.sizeBytes != null && it.resolution == best.resolution && it.sizeBytes!! < best.sizeBytes / 2 }
            .minByOrNull { it.sizeBytes!! }
    return StreamQuickPicks(best = best, faster = faster, server = server.takeIf { it !== best })
}

/** "7.8 GB" / "940 MB", for the sheet's size column. */
internal fun formatStreamSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    if (gb >= 1.0) {
        val tenths = kotlin.math.round(gb * 10).toLong()
        return if (tenths % 10 == 0L || gb >= 100) "${tenths / 10} GB" else "${tenths / 10}.${tenths % 10} GB"
    }
    return "${(bytes / (1024.0 * 1024)).toLong().coerceAtLeast(1)} MB"
}

/** Hands a source picked on the Details page to the stream route that will open it. */
internal object StreamPreselection {
    private var pending: Pair<String, StreamItem>? = null

    fun set(videoId: String, stream: StreamItem) {
        pending = videoId to stream
    }

    /** The source picked for [videoId], once. A pick for another title is dropped. */
    fun take(videoId: String): StreamItem? {
        val (pickedFor, stream) = pending ?: return null
        pending = null
        return stream.takeIf { pickedFor == videoId }
    }
}
