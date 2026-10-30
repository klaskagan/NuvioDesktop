package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.*
import kotlin.math.abs
import kotlinx.serialization.json.*

internal fun readQualityTracks(get: (String) -> String): List<PlayerQualityTrack> {
    val snapshot = runCatching { Json.parseToJsonElement(get("track-list")).jsonArray }.getOrNull()
    val fieldGet: (String) -> String = if (snapshot == null) get else { key ->
        if (key == "track-list/count") snapshot.size.toString() else {
            val parts = key.split('/', limit = 3)
            val value = snapshot.getOrNull(parts.getOrNull(1)?.toIntOrNull() ?: -1)
                ?.jsonObject?.get(parts.getOrNull(2).orEmpty()) as? JsonPrimitive
            when (value?.contentOrNull) { "true" -> "yes"; "false" -> "no"; else -> value?.contentOrNull.orEmpty() }
        }
    }
    return readQualityTrackFields(fieldGet)
}

private fun readQualityTrackFields(get: (String) -> String): List<PlayerQualityTrack> =
    (0 until (get("track-list/count").toIntOrNull() ?: 0).coerceIn(0, 1024)).mapNotNull { index ->
        val prefix = "track-list/$index/"
        fun field(name: String) = get(prefix + name)
        val type = field("type")
        if (type != "audio" && type != "sub") return@mapNotNull null
        val title = field("title")
        val commentary = field("commentary")
        val visual = field("visual-impaired")
        val hearing = field("hearing-impaired")
        val forced = field("forced")
        val role = when {
            commentary == "yes" || Regex("commentary", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "commentary:$title"
            visual == "yes" || Regex("audio description|descriptive", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "description"
            type == "sub" && forced == "yes" && hearing == "yes" -> "forced+hearing-impaired"
            type == "sub" && forced == "yes" -> "forced"
            hearing == "yes" || Regex("\\bSDH\\b", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "hearing-impaired"
            commentary == "no" && visual == "no" && hearing == "no" && (type == "audio" || forced == "no") -> "main"
            Regex("\\b(main|original|full)\\b", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "main"
            else -> null
        }
        PlayerQualityTrack(
            field("id").toIntOrNull() ?: return@mapNotNull null, type,
            field("lang").takeIf { it.isNotBlank() && it != "und" }, title, role,
            field("codec").takeIf(String::isNotBlank), field("selected") == "yes",
            field("external-filename").takeIf { field("external") == "yes" && it.isNotBlank() },
        )
    }

private fun PlayerQualityTrack.identity() = QualityTrackIdentity(
    if (type == "audio") QualityTrackKind.Audio else QualityTrackKind.Subtitle,
    normalizeLanguageCode(language), role, codec, externalUrl,
)

internal fun qualityTrackMatch(selected: PlayerQualityTrack, candidates: List<PlayerQualityTrack>): PlayerQualityTrack? {
    val exact = candidates.firstOrNull { it.type == selected.type && it.title.isNotBlank() && it.title == selected.title &&
        it.language == selected.language && it.role != null && it.role == selected.role &&
        (it.type != "sub" || it.codec == selected.codec) && it.externalUrl == selected.externalUrl }
    return exact ?: matchingQualityTrack(selected.identity(), candidates.map { it.identity() })?.let(candidates::get)
}

internal fun qualityCompatibility(checkpoint: PlayerQualityCheckpoint, tracks: List<PlayerQualityTrack>, durationMs: Long): String? {
    if (!checkpoint.seekable || checkpoint.durationMs <= 0 || durationMs <= 0) return "Live timeline preservation is not verified"
    // Large duration differences are evidence of a different edition/timeline.
    if (abs(checkpoint.durationMs - durationMs) > 5_000) return "Release duration differs; playback position cannot be preserved safely"
    checkpoint.tracks.filter { it.selected }.forEach { selected ->
        if (selected.externalUrl == null && qualityTrackMatch(selected, tracks) == null)
            return if (selected.type == "audio") "Selected audio language and role are unavailable or unknown" else "Selected subtitle language and type are unavailable or unknown"
    }
    return null
}


internal enum class QualityTrackKind { Audio, Subtitle }
internal data class QualityTrackIdentity(
    val kind: QualityTrackKind,
    val language: String?,
    /** Null means unknown, not 'main'. */
    val role: String?,
    val codec: String?,
    val externalUrl: String? = null,
)

/** Track IDs/indexes are local to one media item and must never be compared across sources. */
internal fun matchingQualityTrack(selected: QualityTrackIdentity, candidates: List<QualityTrackIdentity>): Int? {
    if (selected.externalUrl != null) return candidates.indexOfFirst { it == selected }.takeIf { it >= 0 }
    val language = selected.language?.lowercase()?.takeUnless { it.isBlank() || it == "und" } ?: return null
    val role = selected.role ?: return null
    return candidates.indexOfFirst {
        it.kind == selected.kind && it.language?.lowercase() == language && it.role == role &&
            (selected.kind != QualityTrackKind.Subtitle || selected.codec != null && it.codec == selected.codec)
    }.takeIf { it >= 0 }
}
