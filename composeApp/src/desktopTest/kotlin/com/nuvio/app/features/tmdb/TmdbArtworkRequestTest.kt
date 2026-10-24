package com.nuvio.app.features.tmdb

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class TmdbArtworkRequestTest {
    @Test fun appendedArtworkExplicitlyRequestsNeutralImagesAlongsideLocalizedDetails() {
        val query = tmdbArtworkDetailsQuery("pt-BR")
        val url = buildTmdbUrl("movie/10601", "fixture", query)
        assertTrue(url.contains("include_image_language=pt,pt-BR,en,null"))
        assertEquals("pt-BR", query["language"])
        assertEquals("images", query["append_to_response"])
        assertTrue(tmdbArtworkDetailsQuery("en-US").getValue("include_image_language").split(',').contains("null"))
    }

    @Test fun availableLogoDoesNotTriggerAnotherApiRequest() = runBlocking {
        var calls = 0
        val images = TmdbImagesResponse(logos = listOf(TmdbImage("/peter-pan.png", "en")),
            backdrops = listOf(TmdbImage("/textless.jpg")))
        val result = images.withFallbackLogos("en") { calls++; error("Unnecessary request") }
        assertEquals(0, calls)
        assertEquals(images, result)
    }

    @Test fun absentPreferredLogoFetchesNativeLogoOnceAndKeepsCleanArtwork() = runBlocking {
        var calls = 0
        val images = TmdbImagesResponse(backdrops = listOf(TmdbImage("/clean.jpg")))
        val result = images.withFallbackLogos("en") {
            calls++
            TmdbImagesResponse(logos = listOf(TmdbImage("/portuguese.png", "pt"), TmdbImage("/turkish.png", "tr")),
                backdrops = listOf(TmdbImage("/clean.jpg"), TmdbImage("/other.jpg")))
        }
        assertEquals(1, calls)
        assertEquals("/turkish.png", result.selectLandscapeImagePaths("en", "tr").logo)
        assertEquals("/clean.jpg", result.selectCleanLandscapeBackdropPath())
        assertEquals(2, result.backdrops.size)
    }

    @Test fun failedNativeLookupKeepsTextFallbackAndOriginalCleanBackdrop() = runBlocking {
        val images = TmdbImagesResponse(backdrops = listOf(TmdbImage("/clean.jpg")))
        assertEquals(images, images.withFallbackLogos("en") { null })
    }
}
