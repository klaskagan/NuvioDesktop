package com.nuvio.app.features.trakt

import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.tracking.TrackingMediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TraktHistoryProviderTest {
    @Test
    fun decodesMovieAndEpisodeWatchesWithLargeIdsAndPreservesRewatches() {
        val response = RawHttpResponse(
            status = 200,
            statusText = "OK",
            url = "https://api.trakt.tv/sync/history",
            headers = mapOf("x-pagination-page-count" to "2"),
            body = """
                [
                  {"id": 4294967296, "watched_at": "2026-11-17T22:00:00.000Z", "type": "movie",
                   "movie": {"title": "Film", "ids": {"imdb": "tt1234567"},
                             "images": {"poster": ["//art.trakt.tv/film.jpg"]}}},
                  {"id": 4294967297, "watched_at": "2026-11-17T20:00:00.000Z", "type": "episode",
                   "show": {"title": "Show", "ids": {"tmdb": 42}},
                   "episode": {"season": 0, "number": 1, "title": "Special", "images": {"screenshot": ["//art.trakt.tv/episode.jpg"]}}},
                  {"id": 4294967298, "watched_at": "2026-11-16T20:00:00.000Z", "type": "movie",
                   "movie": {"title": "Film", "ids": {"imdb": "tt1234567"}}}
                ]
            """.trimIndent(),
        )
        val page = decodeTraktHistoryPage(response, 1)
        assertEquals("2", page.nextCursor)
        assertEquals(listOf("4294967296", "4294967297", "4294967298"), page.events.map { it.id })
        val (movie, episode, rewatch) = page.events
        assertEquals(TrackingMediaKind.MOVIE, movie.media.kind)
        assertEquals("https://art.trakt.tv/film.jpg", movie.media.posterUrl)
        assertEquals(movie.media.ids, rewatch.media.ids)
        assertEquals(TrackingMediaKind.SHOW, episode.media.kind)
        assertEquals(0, episode.media.episode?.season)
        assertEquals("Special", episode.media.episode?.title)
        assertEquals("https://art.trakt.tv/episode.jpg", episode.episodeScreenshot)
        assertEquals(42L, episode.media.ids.tmdb)
        assertNull(decodeTraktHistoryPage(response, 2).nextCursor)
    }
}
