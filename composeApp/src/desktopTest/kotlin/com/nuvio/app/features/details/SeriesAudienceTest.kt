package com.nuvio.app.features.details

import com.nuvio.app.features.trakt.AudienceEpisodeKey
import com.nuvio.app.features.trakt.TraktAudienceEpisode
import com.nuvio.app.features.trakt.TraktAudienceStats
import com.nuvio.app.features.trakt.audienceRetryDeadline
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesAudienceTest {
    @Test
    fun retentionAxisKeepsWholeLabelsAtShortAndLongSeasonWidths() {
        val short = (1..8).toList()
        assertEquals(short.indices.toList(), retentionAxisTicks(short, List(8) { 20 }, 330, 8).map { it.index })
        for (count in listOf(65, 160)) {
            val episodes = (1..count).toList()
            val widths = episodes.map { "E$it".length * 8 }
            for (width in listOf(300, 680)) {
                val ticks = retentionAxisTicks(episodes, widths, width, 8)
                assertEquals(0, ticks.first().index)
                assertEquals(count - 1, ticks.last().index)
                assertTrue(ticks.size in 3 until count)
                assertTrue(ticks.all { it.left >= 0 && it.left + widths[it.index] <= width })
                assertTrue(ticks.zipWithNext().all { (a, b) -> a.left + widths[a.index] + 8 <= b.left })
            }
        }
    }

    @Test
    fun countsReleasedEpisodesOnceAndReflectsWatchedStateChanges() {
        val first = MetaVideo("first", "First", "2025-01-01", season = 1, episode = 1)
        val replay = first.copy(id = "same-episode")
        val second = first.copy(id = "second", episode = 2)
        val unaired = first.copy(id = "future", released = "2050-04-01", episode = 3)
        val special = first.copy(id = "special", season = 0)
        val meta = MetaDetails("tt0123456", "series", "Fixture", videos = listOf(first, replay, second, unaired, special))
        assertEquals(
            listOf(SeasonWatchedCount(1, 1, 2), SeasonWatchedCount(0, 0, 1)),
            meta.seasonWatchedCounts("2026-01-01") { it.id in setOf(first.id, replay.id, unaired.id) },
        )
        assertEquals(
            listOf(SeasonWatchedCount(1, 0, 2), SeasonWatchedCount(0, 1, 1)),
            meta.seasonWatchedCounts("2026-01-01") { it.id == special.id },
        )
    }

    @Test
    fun parsesRateLimitDelayAndHttpDate() {
        assertEquals(8000L, audienceRetryDeadline("5", 3000))
        assertEquals(784111777000L, audienceRetryDeadline("Sun, 06 Nov 1994 08:49:37 GMT", 0))
        assertNull(audienceRetryDeadline("-1", 3000))
        assertNull(audienceRetryDeadline("invalid", 3000))
    }

    @Test
    fun mapsReorderedEpisodesAndSplitSeasonsUsingTitleAndAirDate() {
        val first = AudienceEpisodeKey(1, 1)
        val second = AudienceEpisodeKey(1, 2)
        val desktop = listOf(
            MetaVideo("second", "Second", "2025-01-02", season = 1, episode = 1),
            MetaVideo("first", "First", "2025-01-01", season = 2, episode = 1),
        )
        val audience = mapOf(
            first to TraktAudienceEpisode(number = 1, title = "First", firstAired = "2025-01-01T12:00:00Z"),
            second to TraktAudienceEpisode(number = 2, title = "Second", firstAired = "2025-01-02T12:00:00Z"),
        )
        val matched = mapAudienceEpisodes(desktop, audience)
        assertEquals("first", matched[first]?.id)
        assertEquals("second", matched[second]?.id)
    }

    /** Trakt's UTC air date is a day after the addon's local one for US evening premieres. */
    @Test
    fun mapsEpisodesWhoseAirDateIsADayApart() {
        val key = AudienceEpisodeKey(36, 1)
        val video = MetaVideo("tt0096697:36:1", "Bart's Birthday", "2024-09-29T00:00:00.000Z", season = 36, episode = 1)
        val audience = mapOf(key to TraktAudienceEpisode(number = 1, title = "Bart's Birthday", firstAired = "2024-09-30T00:00:00.000Z"))
        assertEquals("tt0096697:36:1", mapAudienceEpisodes(listOf(video), audience)[key]?.id)

        // A renumbered episode is still found by its title and the shifted date.
        val moved = video.copy(id = "moved", season = 35, episode = 22)
        assertEquals("moved", mapAudienceEpisodes(listOf(moved), audience)[key]?.id)

        // Two days apart with a different title is not the same episode.
        val other = video.copy(id = "other", title = "Another", released = "2024-10-02")
        assertTrue(mapAudienceEpisodes(listOf(other), audience).isEmpty())
    }

    @Test
    fun leavesAmbiguousEpisodesAndUnknownSpecialsUnmapped() {
        val key = AudienceEpisodeKey(0, 1)
        val episode = TraktAudienceEpisode(number = 1, title = "Special", firstAired = "2025-01-01")
        val unknown = MetaVideo("unknown", "Special", "2025-01-01", episode = 1)
        assertTrue(mapAudienceEpisodes(listOf(unknown), mapOf(key to episode)).isEmpty())

        val first = unknown.copy(id = "first", season = 0)
        val second = first.copy(id = "second")
        assertTrue(mapAudienceEpisodes(listOf(first, second), mapOf(key to episode)).isEmpty())
    }

    @Test
    fun calculatesRetentionWithGapsAndRatiosAboveOneHundred() {
        val keys = (1..5).map { AudienceEpisodeKey(1, it) }
        val stats = mapOf(
            keys[0] to TraktAudienceStats(watchers = 100),
            keys[1] to TraktAudienceStats(watchers = 110),
            keys[3] to TraktAudienceStats(watchers = 70),
            keys[4] to TraktAudienceStats(watchers = 65),
        )
        val ratios = retentionRatios(keys, stats)
        assertEquals(listOf(100.0, 110.0, null, 70.0, 65.0), ratios)
        assertEquals(3 to 5.0, largestAudienceDrop(ratios))
        assertNull(largestAudienceDrop(listOf(100.0, null, 65.0)))
        assertTrue(retentionRatios(keys, stats - keys.first()).all { it == null })
    }

    @Test
    fun scalesOnlyValidValuesAndTreatsFlatRowsAsOneHundredPercent() {
        assertEquals(listOf(100, null, 100), relativeAudienceValues(listOf(3.0, null, 3.0)))
        assertEquals(listOf(0, 50, 100), relativeAudienceValues(listOf(0.0, 2.0, 4.0)))
    }

    @Test
    fun sumsWatchedRuntimeOncePerEpisode() {
        val meta = MetaDetails(
            "runtime", "series", "Runtime",
            videos = listOf(
                MetaVideo("a:1:1", "One", "2024-01-01", season = 1, episode = 1, runtime = 50),
                MetaVideo("b:1:1", "One", "2024-01-01", season = 1, episode = 1, runtime = 50),
                MetaVideo("a:1:2", "Two", "2024-01-08", season = 1, episode = 2, runtime = 50),
                MetaVideo("a:1:3", "Three", "2024-01-15", season = 1, episode = 3),
            ),
        )
        val counts = meta.seasonWatchedCounts("2025-01-01") { it.episode != 2 }
        assertEquals(listOf(SeasonWatchedCount(1, 2, 3, 50)), counts)
        assertEquals("1 h 40", formatWatchedMinutes(100))
        assertEquals("2 h", formatWatchedMinutes(120))
        assertEquals("40 min", formatWatchedMinutes(40))
    }

    @Test
    fun ratingAndFingerprintColoursFollowTheDesignBands() {
        assertEquals(0xffe8b27a.toInt(), audienceRatingColor(6.8).toArgb())
        assertEquals(0xffdfe0a0.toInt(), audienceRatingColor(7.6).toArgb())
        assertEquals(0xffc8e3a0.toInt(), audienceRatingColor(7.9).toArgb())
        assertEquals(0xffa7e3a0.toInt(), audienceRatingColor(8.2).toArgb())
        assertEquals(0xff7be0b0.toInt(), audienceRatingColor(8.9).toArgb())
        assertEquals(0.35f, fingerprintAlpha(0))
        assertEquals(1f, fingerprintAlpha(100))
    }

}
