package com.nuvio.app.features.details

import com.nuvio.app.features.trailer.HeroTrailerRendition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

// The existing extractor and player startup allowances, shared across all attempts.
private const val HeroStartupBudgetMillis = 42_000L
private const val RenditionStartupMillis = 6_000L

internal enum class DesktopHeroTrailerEnd {
    Completed,
    Failed,
}

internal class DesktopHeroTrailerAttempt(
    val trailer: MetaTrailer,
    val rendition: HeroTrailerRendition,
) {
    val ready = CompletableDeferred<Boolean>()
    private var terminalReported = false

    fun markReady(): Boolean = ready.complete(true)

    // A terminal event before readiness rejects the rendition so startup can try the next one.
    fun finish(end: DesktopHeroTrailerEnd): DesktopHeroTrailerEnd? {
        if (terminalReported) return null
        terminalReported = true
        return end.takeUnless { ready.complete(false) }
    }
}

internal suspend fun startDesktopHeroTrailer(
    trailers: List<MetaTrailer>,
    resolve: suspend (MetaTrailer) -> List<HeroTrailerRendition>,
    tryRendition: suspend (DesktopHeroTrailerAttempt) -> Boolean,
): Boolean = withTimeoutOrNull(HeroStartupBudgetMillis) {
    for (trailer in trailers) {
        for (rendition in resolve(trailer)) {
            val attempt = DesktopHeroTrailerAttempt(trailer, rendition)
            if (withTimeoutOrNull(RenditionStartupMillis) { tryRendition(attempt) } == true) {
                return@withTimeoutOrNull true
            }
        }
    }
    false
} ?: false

/**
 * The true width / height of a portrait or square trailer rendition, or null for landscape and
 * unknown sizes. The macOS player can't read a pinned HLS stream's size, so it hands back 16:9
 * frames; drawing those at this ratio undoes the squeeze instead of cropping a stretched picture.
 */
internal fun heroTrailerUprightAspect(width: Int, height: Int): Float? =
    if (width > 0 && height > 0 && width.toFloat() / height < 1.2f) width.toFloat() / height else null
