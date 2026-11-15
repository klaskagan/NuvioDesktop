package com.nuvio.app.features.details

import com.nuvio.app.features.trailer.HeroTrailerRendition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

// The existing extractor and player startup allowances, shared across all attempts.
private const val HeroStartupBudgetMillis = 42_000L
private const val RenditionStartupMillis = 6_000L

internal class DesktopHeroTrailerAttempt(
    val trailer: MetaTrailer,
    val rendition: HeroTrailerRendition,
) {
    val ready = CompletableDeferred<Boolean>()
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
