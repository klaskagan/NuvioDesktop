package com.nuvio.app.features.handoff

import kotlinx.serialization.Serializable
import kotlin.math.abs

const val TV_HANDOFF_PROTOCOL_VERSION = 1
const val TV_HANDOFF_TTL_MS = 120_000L

/** Content identity only: the receiver resolves sources using its own quality preferences. */
@Serializable
data class TvHandoffRequest(
    val version: Int = TV_HANDOFF_PROTOCOL_VERSION,
    val requestId: String,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val accountId: String,
    val profileId: Int,
    val contentType: String,
    val contentId: String,
    val videoId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val season: Int? = null,
    val episode: Int? = null,
    val editionHint: String? = null,
) {
    fun validate(nowEpochMs: Long): TvHandoffRejection? = when {
        version != TV_HANDOFF_PROTOCOL_VERSION -> TvHandoffRejection.UNSUPPORTED_VERSION
        listOf(requestId, senderDeviceId, targetDeviceId, accountId, contentId, videoId).any {
            it.isBlank() || it.length > 512 || it.any(Char::isISOControl)
        } -> TvHandoffRejection.INVALID_REQUEST
        title.isBlank() || title.length > 512 || editionHint.orEmpty().length > 128 -> TvHandoffRejection.INVALID_REQUEST
        profileId !in 1..6 || contentType !in setOf("movie", "series") -> TvHandoffRejection.INVALID_REQUEST
        contentType == "series" && (season == null || episode == null || season < 0 || episode < 1) ->
            TvHandoffRejection.INVALID_REQUEST
        contentType == "movie" && (season != null || episode != null) -> TvHandoffRejection.INVALID_REQUEST
        positionMs < 0 || durationMs <= 0 || positionMs >= durationMs -> TvHandoffRejection.INVALID_REQUEST
        createdAtEpochMs <= 0 || createdAtEpochMs > nowEpochMs + 5_000L -> TvHandoffRejection.INVALID_REQUEST
        expiresAtEpochMs <= createdAtEpochMs || expiresAtEpochMs - createdAtEpochMs > TV_HANDOFF_TTL_MS ->
            TvHandoffRejection.INVALID_REQUEST
        nowEpochMs >= expiresAtEpochMs -> TvHandoffRejection.EXPIRED
        else -> null
    }

    fun receiverRejection(
        nowEpochMs: Long,
        receiverDeviceId: String,
        receiverAccountId: String,
        receiverProfileId: Int,
    ): TvHandoffRejection? = validate(nowEpochMs) ?: when {
        targetDeviceId != receiverDeviceId -> TvHandoffRejection.WRONG_DEVICE
        accountId != receiverAccountId -> TvHandoffRejection.WRONG_ACCOUNT
        profileId != receiverProfileId -> TvHandoffRejection.PROFILE_REQUIRED
        else -> null
    }
}

@Serializable
enum class TvHandoffRejection {
    UNSUPPORTED_VERSION, INVALID_REQUEST, EXPIRED, WRONG_DEVICE, WRONG_ACCOUNT, PROFILE_REQUIRED,
}

@Serializable
enum class TvHandoffPhase {
    WAKING, LAUNCHING, RECEIVED, AWAITING_USER, RESOLVING, LOADING, PLAYING, FAILED, CANCELLED, EXPIRED;

    val terminal: Boolean get() = this in setOf(PLAYING, FAILED, CANCELLED, EXPIRED)
}

@Serializable
data class TvHandoffUpdate(
    val requestId: String,
    val targetDeviceId: String,
    val phase: TvHandoffPhase,
    val positionMs: Long? = null,
    val failureCode: String? = null,
)

/** All callers serialize access. Terminal sessions cannot be revived by delayed network messages. */
class TvHandoffSession(val request: TvHandoffRequest) {
    var phase: TvHandoffPhase = TvHandoffPhase.WAKING
        private set

    val suppressLocalProgress: Boolean get() = phase == TvHandoffPhase.PLAYING

    fun expire(nowEpochMs: Long): Boolean {
        if (phase.terminal || nowEpochMs < request.expiresAtEpochMs) return false
        phase = TvHandoffPhase.EXPIRED
        return true
    }

    fun cancel(): Boolean {
        if (phase.terminal) return false
        phase = TvHandoffPhase.CANCELLED
        return true
    }

    fun accept(update: TvHandoffUpdate, nowEpochMs: Long): Boolean {
        if (expire(nowEpochMs) || phase.terminal) return false
        if (update.requestId != request.requestId || update.targetDeviceId != request.targetDeviceId) return false
        if (update.phase == TvHandoffPhase.PLAYING && (
                update.positionMs == null || update.positionMs < 0 ||
                    abs(update.positionMs - request.positionMs) > 2_000L
                )
        ) return false
        val allowed = when (phase) {
            TvHandoffPhase.WAKING -> setOf(TvHandoffPhase.LAUNCHING, TvHandoffPhase.RECEIVED)
            TvHandoffPhase.LAUNCHING -> setOf(TvHandoffPhase.RECEIVED)
            TvHandoffPhase.RECEIVED -> setOf(TvHandoffPhase.AWAITING_USER, TvHandoffPhase.RESOLVING)
            TvHandoffPhase.AWAITING_USER -> setOf(TvHandoffPhase.RESOLVING, TvHandoffPhase.LOADING)
            TvHandoffPhase.RESOLVING -> setOf(TvHandoffPhase.AWAITING_USER, TvHandoffPhase.LOADING)
            TvHandoffPhase.LOADING -> setOf(TvHandoffPhase.AWAITING_USER, TvHandoffPhase.PLAYING)
            else -> emptySet()
        }
        if (update.phase != phase && update.phase !in allowed && update.phase != TvHandoffPhase.FAILED) return false
        phase = update.phase
        return true
    }
}

/** Edition is independent of resolution, HDR, audio codec and release group. */
fun tvHandoffEditionHint(releaseName: String): String? {
    val normalized = releaseName.lowercase().replace(Regex("[._-]+"), " ")
    return listOf(
        "director's cut" to Regex("\\bdirector(?:s|'s)?\\s+cut\\b"),
        "extended" to Regex("\\bextended\\b"),
        "theatrical" to Regex("\\btheatrical\\b"),
        "unrated" to Regex("\\bunrated\\b"),
        "uncut" to Regex("\\buncut\\b"),
        "final cut" to Regex("\\bfinal\\s+cut\\b"),
    ).firstOrNull { (_, pattern) -> pattern.containsMatchIn(normalized) }?.first
}
