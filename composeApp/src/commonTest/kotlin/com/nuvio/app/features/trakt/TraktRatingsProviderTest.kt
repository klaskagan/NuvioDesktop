package com.nuvio.app.features.trakt

import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TraktRatingsProviderTest {
    private val ids = TrackingExternalIds(imdb = "tt1", tmdb = 42, trakt = 7, tvdb = "99")
    private val movie = TrackingMediaReference(kind = TrackingMediaKind.MOVIE, ids = ids)
    private val show = TrackingMediaReference(kind = TrackingMediaKind.SHOW, ids = ids)

    @Test
    fun `a movie is rated in the movies bucket and removed without a rating`() {
        val item = buildTraktRatingBody(movie, 8)!!.getValue("movies").jsonArray.single().jsonObject
        assertEquals("8", item.getValue("rating").jsonPrimitive.content)
        assertEquals("tt1", item.getValue("ids").jsonObject.getValue("imdb").jsonPrimitive.content)
        assertNull(item.getValue("ids").jsonObject["tvdb"])
        val removal = buildTraktRatingBody(movie, null)!!.getValue("movies").jsonArray.single().jsonObject
        assertNull(removal["rating"])
    }

    @Test
    fun `a show, season and episode nest their rating at the right level`() {
        val whole = buildTraktRatingBody(show, 7)!!.getValue("shows").jsonArray.single().jsonObject
        assertEquals("7", whole.getValue("rating").jsonPrimitive.content)
        assertEquals("99", whole.getValue("ids").jsonObject.getValue("tvdb").jsonPrimitive.content)

        val season = buildTraktRatingBody(show.copy(wholeSeason = 2), 6)!!.getValue("shows").jsonArray.single().jsonObject
        assertNull(season["rating"])
        val seasonEntry = season.getValue("seasons").jsonArray.single().jsonObject
        assertEquals("2", seasonEntry.getValue("number").jsonPrimitive.content)
        assertEquals("6", seasonEntry.getValue("rating").jsonPrimitive.content)

        val episode = buildTraktRatingBody(show.copy(episode = TrackingEpisode(season = 2, number = 3)), 9)!!
            .getValue("shows").jsonArray.single().jsonObject
            .getValue("seasons").jsonArray.single().jsonObject
        assertNull(episode["rating"])
        val episodeEntry = episode.getValue("episodes").jsonArray.single().jsonObject
        assertEquals("3", episodeEntry.getValue("number").jsonPrimitive.content)
        assertEquals("9", episodeEntry.getValue("rating").jsonPrimitive.content)
    }

    @Test
    fun `an item without trakt, imdb or tmdb ids has no body`() {
        assertNull(buildTraktRatingBody(TrackingMediaReference(kind = TrackingMediaKind.SHOW, title = "x"), 5))
    }

    @Test
    fun `keys separate a show from its seasons and episodes`() {
        assertEquals(listOf("movie:trakt:7", "movie:imdb:tt1", "movie:tmdb:42"), traktRatingKeys(movie))
        assertEquals("show:imdb:tt1", traktRatingKeys(show)[1])
        assertEquals("show:imdb:tt1:s2", traktRatingKeys(show.copy(wholeSeason = 2))[1])
        assertEquals("show:imdb:tt1:s2:e3", traktRatingKeys(show.copy(episode = TrackingEpisode(season = 2, number = 3)))[1])
    }

    @Test
    fun `not found items are counted across buckets`() {
        assertEquals(0, traktNotFoundCount("""{"added":{"movies":1},"not_found":{"movies":[],"shows":[]}}"""))
        assertEquals(2, traktNotFoundCount("""{"not_found":{"movies":[{"ids":{}}],"episodes":[{"ids":{}}]}}"""))
        assertEquals(0, traktNotFoundCount("not json"))
    }
}
