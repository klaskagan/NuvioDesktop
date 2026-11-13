package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem

/** Native IDs are only meaningful within one loaded source. */
data class PlayerQualityTrack(
    val id: Int,
    val type: String,
    val language: String?,
    val title: String,
    val role: String?,
    val codec: String?,
    val selected: Boolean,
    val externalUrl: String? = null,
)

data class PlayerQualityCheckpoint(
    val positionMs: Long,
    val paused: Boolean,
    val speed: Float,
    val volume: Float,
    val muted: Boolean,
    val subtitleDelayMs: Int,
    val tracks: List<PlayerQualityTrack>,
    val durationMs: Long,
    val seekable: Boolean = true,
)

data class PlayerQualityChoice(
    val id: Int,
    val height: Int,
    val url: String,
    val headers: Map<String, String>,
    val stream: StreamItem? = null,
    val disabledReason: String? = null,
)

data class PlayerControlQualityItem(val height: Int, val enabled: Boolean, val reason: String = "")

/** A decoded, buffered candidate which still has no visible/native playback ownership. */
interface BufferedPlayerQuality {
    /** Synchronous ownership transfer; returns the old player's current state at the cut. */
    fun promote(): PlayerQualityCheckpoint
    suspend fun cancel()
}
