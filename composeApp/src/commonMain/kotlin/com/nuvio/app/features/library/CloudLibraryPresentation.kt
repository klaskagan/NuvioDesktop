package com.nuvio.app.features.library

import androidx.compose.runtime.Composable
import com.nuvio.app.features.cloud.CloudLibraryFile
import com.nuvio.app.features.cloud.CloudLibraryItem
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What a cloud item holds, read from its names (providers only report transport: torrent, usenet…). */
internal enum class CloudMediaKind { Movie, Show, Other }

private val EpisodeToken = Regex("""(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})\b""")
private val SeasonToken = Regex("""(?i)\b(S\d{1,2}|Season[ ._-]?\d{1,2}|Complete[ ._-]Series)\b""")
private val YearToken = Regex("""\b(19[2-9]\d|20\d\d)\b""")
private val VideoExtension = Regex("""(?i)\.(mkv|mp4|m4v|avi|mov|wmv|ts|m2ts|webm)$""")
private val ReleaseNoise = Regex(
    """(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr10?\+?|hdr|dv|dolby[ ._-]?vision|web[ ._-]?dl|webrip|bluray|blu[ ._-]?ray|brrip|bdrip|remux|hdtv|x26[45]|h[ ._]?26[45]|hevc|avc|aac|ac3|ddp?\d[ ._]?\d|dts(-hd)?|atmos|truehd|10bit|proper|repack|extended|dsnp|amzn|nf|hmax|atvp)\b.*$""",
)

internal fun CloudLibraryItem.mediaKind(): CloudMediaKind {
    val names = listOf(name) + playableFiles.map { it.name }
    if (names.any { EpisodeToken.containsMatchIn(it) } || SeasonToken.containsMatchIn(name)) return CloudMediaKind.Show
    val hasVideo = playableFiles.any { VideoExtension.containsMatchIn(it.name) } || VideoExtension.containsMatchIn(name)
    if (hasVideo && YearToken.containsMatchIn(name)) return CloudMediaKind.Movie
    if (hasVideo && playableFiles.size == 1) return CloudMediaKind.Movie
    return CloudMediaKind.Other
}

/** A release name without extension, dots, quality tags or year; may be empty. */
private fun cleanRelease(raw: String): String {
    var text = raw.replace(VideoExtension, "").replace('.', ' ').replace('_', ' ').trim()
    text = text.replace(ReleaseNoise, "").trim()
    // Cut at the year, keeping a season token before it.
    YearToken.find(text)?.let { year -> if (year.range.first > 0) text = text.substring(0, year.range.first) }
    return text.replace(Regex("""[\s\-\[(]+$"""), "").replace(Regex("""^[\s\-·]+"""), "").replace(Regex("""\s{2,}"""), " ").trim()
}

/** A readable title from a release name: "Severance S02 (2025) 1080p" → "Severance S02". */
internal fun cloudDisplayTitle(raw: String): String = cleanRelease(raw).ifBlank { raw }

private fun pad(value: Int, width: Int) = value.toString().padStart(width, '0')

/** "S02E01 · Hello Ms Cobel" when the file name carries an episode, else the cleaned name. */
internal fun cloudFileLabel(file: CloudLibraryFile): String {
    val match = EpisodeToken.find(file.name) ?: return cloudDisplayTitle(file.name)
    val code = "S${pad(match.groupValues[1].toInt(), 2)}E${pad(match.groupValues[2].toInt(), 2)}"
    val title = cleanRelease(file.name.substring(match.range.last + 1))
    return if (title.isBlank()) code else "$code · $title"
}

/** Episode order for a season pack; files without an episode keep their name order after them. */
internal fun cloudFileSortKey(file: CloudLibraryFile): String =
    EpisodeToken.find(file.name)?.let { "0-${pad(it.groupValues[1].toInt(), 3)}-${pad(it.groupValues[2].toInt(), 4)}" }
        ?: "1-${file.name.lowercase()}"

@Composable
internal fun cloudMediaKindLabel(kind: CloudMediaKind?): String = stringResource(
    when (kind) {
        null -> Res.string.cloud_library_type_all
        CloudMediaKind.Movie -> Res.string.library_cloud_kind_movies
        CloudMediaKind.Show -> Res.string.library_cloud_kind_shows
        CloudMediaKind.Other -> Res.string.library_cloud_kind_other
    },
)

@Composable
internal fun cloudMediaKindSingular(kind: CloudMediaKind): String = stringResource(
    when (kind) {
        CloudMediaKind.Movie -> Res.string.library_cloud_kind_movie
        CloudMediaKind.Show -> Res.string.library_cloud_kind_show
        CloudMediaKind.Other -> Res.string.library_cloud_kind_item
    },
)

/** "today", "yesterday", "5 days ago", "3 weeks ago", "4 months ago". */
@Composable
internal fun relativeAgo(epochMs: Long, nowMs: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()): String {
    val days = ((nowMs - epochMs).coerceAtLeast(0) / 86_400_000L).toInt()
    return when {
        days == 0 -> stringResource(Res.string.library_ago_today)
        days == 1 -> stringResource(Res.string.library_ago_yesterday)
        days < 7 -> pluralStringResource(Res.plurals.library_ago_days, days, days)
        days < 60 -> (days / 7).let { pluralStringResource(Res.plurals.library_ago_weeks, it, it) }
        else -> (days / 30).let { pluralStringResource(Res.plurals.library_ago_months, it, it) }
    }
}

/** "just now", "4 min ago", "2 h ago", or the day form for older refreshes. */
@Composable
internal fun refreshedAgo(epochMs: Long, nowMs: Long = kotlin.time.Clock.System.now().toEpochMilliseconds()): String {
    val minutes = ((nowMs - epochMs).coerceAtLeast(0) / 60_000L).toInt()
    return when {
        minutes < 1 -> stringResource(Res.string.library_refreshed_just_now)
        minutes < 60 -> stringResource(Res.string.library_refreshed_minutes, minutes)
        minutes < 24 * 60 -> stringResource(Res.string.library_refreshed_hours, minutes / 60)
        else -> stringResource(Res.string.library_refreshed_ago, relativeAgo(epochMs, nowMs))
    }
}

/** The row's meta line: "8 files · 21.6 GB · Real-Debrid · 2 days ago", plus a non-ready status. */
@Composable
internal fun cloudRowMeta(item: CloudLibraryItem): String {
    val files = item.playableFiles.size
    val ready = item.status == null || item.status.equals("ready", true) || item.status.equals("completed", true) ||
        item.status.equals("downloaded", true) || item.status.equals("cached", true) || item.status.equals("uploading", true)
    return listOfNotNull(
        if (files == 0) stringResource(Res.string.cloud_library_no_playable_files) else pluralStringResource(Res.plurals.library_file_count, files, files),
        item.sizeBytes?.let(::formatCloudBytes),
        item.providerName,
        item.addedAtEpochMs?.let { relativeAgo(it) },
        item.status?.takeIf { !ready }?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
            ?.let { status -> item.progressFraction?.let { "$status ${(it * 100).toInt()}%" } ?: status },
    ).joinToString(" · ")
}
