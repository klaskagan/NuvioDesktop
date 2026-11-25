package com.nuvio.app.features.trakt

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Trakt refused review posts with 400 invalid_request_body when the item was only
 * {"ids":{"imdb":…}}; the body now carries the item as Trakt's summary returns it.
 */
class TraktCommentBodyTest {
    @Test fun theItemKeepsTitleYearAndIdsFromTheSummary() {
        val summary = """{"title":"Severance","year":2022,"ids":{"trakt":154997,"slug":"severance","imdb":"tt11280740","tmdb":95396},"overview":"…","network":"Apple TV"}"""
        val item = traktCommentItem(summary)
        assertEquals("""{"title":"Severance","year":2022,"ids":{"trakt":154997,"slug":"severance","imdb":"tt11280740","tmdb":95396}}""", item)
        assertEquals(
            """{"show":$item,"comment":"Five \"quoted\" words here, really.","spoiler":false}""",
            traktCommentBody("show", item!!, "Five \"quoted\" words here, really.", spoiler = false),
        )
    }
}
