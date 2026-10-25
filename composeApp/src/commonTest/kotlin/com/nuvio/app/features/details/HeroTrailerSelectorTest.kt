package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeroTrailerSelectorTest {

    @Test
    fun `prefers older HD to newer SD within the same category`() {
        assertEquals("hd", selectHeroTrailer(listOf(
            trailer("sd", official = true, size = 480, publishedAt = "2026-01-01"),
            trailer("hd", official = true, size = 1080, publishedAt = "2008-01-01"),
        ))?.id)
    }

    @Test
    fun `ranks intermediate and larger resolutions correctly`() {
        val hd = trailer("hd", size = 1080)
        val qhd = trailer("qhd", size = 1440)
        val uhd = trailer("uhd", size = 2160)
        assertEquals("qhd", selectHeroTrailer(listOf(hd, qhd))?.id)
        assertEquals("uhd", selectHeroTrailer(listOf(hd, qhd, uhd))?.id)
        assertEquals("larger", selectHeroTrailer(listOf(hd, trailer("larger", size = 4320)))?.id)
    }

    @Test
    fun `retains official category preference and supports HD season trailers`() {
        assertEquals("official", selectHeroTrailer(listOf(
            trailer("official", official = true, size = 480),
            trailer("unofficial", size = 1080),
        ))?.id)
        assertEquals("hd", selectHeroTrailer(listOf(
            trailer("sd", seasonNumber = 1, size = 480, publishedAt = "2026-01-01"),
            trailer("hd", seasonNumber = 1, size = 1080, publishedAt = "2025-01-01"),
        ))?.id)
    }

    @Test
    fun `selects official non-season YouTube trailer first`() {
        val trailers = listOf(
            trailer(id = "teaser", type = "Teaser", official = true, publishedAt = "2026-02-01"),
            trailer(id = "season", type = "Trailer", official = true, seasonNumber = 2, publishedAt = "2026-03-01"),
            trailer(id = "official", type = "Trailer", official = true, publishedAt = "2026-01-01"),
        )

        assertEquals("official", selectHeroTrailer(trailers)?.id)
    }

    @Test
    fun `falls back to addon-style YouTube trailer when official flag is absent`() {
        val trailers = listOf(
            trailer(id = "clip", type = "Clip", official = false, publishedAt = "2026-02-01"),
            trailer(id = "addon", type = "Trailer", official = false, publishedAt = "2026-01-01"),
        )

        assertEquals("addon", selectHeroTrailer(trailers)?.id)
    }

    @Test
    fun `uses season trailer when only season trailers are available`() {
        val trailers = listOf(
            trailer(id = "season-one", type = "Trailer", official = false, seasonNumber = 1),
            trailer(id = "season-two", type = "Trailer", official = true, seasonNumber = 2),
        )

        assertEquals("season-two", selectHeroTrailer(trailers)?.id)
    }

    @Test
    fun `filters out non YouTube trailers`() {
        val trailers = listOf(
            trailer(id = "vimeo", site = "Vimeo", type = "Trailer", official = true),
            trailer(id = "youtube", site = "YouTube", type = "Teaser", official = false),
        )

        assertEquals("youtube", selectHeroTrailer(trailers)?.id)
    }

    @Test
    fun `returns null for empty or unsupported trailer lists`() {
        assertNull(selectHeroTrailer(emptyList()))
        assertNull(selectHeroTrailer(listOf(trailer(id = "blank", key = ""))))
        assertNull(selectHeroTrailer(listOf(trailer(id = "other", site = "Vimeo"))))
    }

    private fun trailer(
        id: String,
        key: String = id,
        site: String = "YouTube",
        type: String = "Trailer",
        official: Boolean = false,
        publishedAt: String? = null,
        seasonNumber: Int? = null,
        size: Int? = null,
    ): MetaTrailer =
        MetaTrailer(
            id = id,
            key = key,
            name = id,
            site = site,
            type = type,
            official = official,
            publishedAt = publishedAt,
            seasonNumber = seasonNumber,
            size = size,
        )
}
