package com.nuvio.app.features.details

import com.nuvio.app.features.details.components.CastFilter
import com.nuvio.app.features.details.components.castFilterPeople
import com.nuvio.app.features.gallery.GalleryFilter
import com.nuvio.app.features.gallery.GalleryImage
import com.nuvio.app.features.gallery.GalleryKind
import com.nuvio.app.features.gallery.GallerySource
import com.nuvio.app.features.gallery.galleryKinds
import com.nuvio.app.features.gallery.galleryVisible
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailTabContentTest {
    private fun image(url: String, kind: GalleryKind, vote: Double = 0.0, textFree: Boolean = true) =
        GalleryImage(url, url, kind, GallerySource.Tmdb, vote = vote, textFree = textFree)

    private val images = listOf(
        image("poster-en", GalleryKind.Poster, vote = 9.0, textFree = false),
        image("backdrop-low", GalleryKind.Backdrop, vote = 2.0),
        image("backdrop-high", GalleryKind.Backdrop, vote = 8.0),
        image("backdrop-high", GalleryKind.Backdrop, vote = 8.0),
        image("still", GalleryKind.Still),
    )

    @Test fun galleryDropsDuplicatesSortsByVoteAndHidesTextByDefault() {
        assertEquals(listOf("backdrop-high", "backdrop-low", "still"), galleryVisible(images, GalleryFilter()).map { it.url })
        assertEquals(listOf("poster-en"), galleryVisible(images, GalleryFilter(GalleryKind.Poster, textFreeOnly = false)).map { it.url })
        assertEquals(listOf(GalleryKind.Backdrop, GalleryKind.Poster, GalleryKind.Still), galleryKinds(images))
    }

    @Test fun castFilterSplitsCrewFromCast() {
        val people = listOf(MetaPerson("Dan Erickson", "Creator"), MetaPerson("Adam Scott", "Mark S."))
        val crew = setOf("Creator", "Director", "Writer")
        assertEquals(listOf("Dan Erickson"), castFilterPeople(people, CastFilter.Crew, crew).map { it.name })
        assertEquals(listOf("Adam Scott"), castFilterPeople(people, CastFilter.Cast, crew).map { it.name })
        assertEquals(2, castFilterPeople(people, CastFilter.All, crew).size)
    }

    @Test fun galleryTabSitsWhereItsSectionIs() {
        val order = listOf(MetaScreenSectionKey.TRAILERS, MetaScreenSectionKey.GALLERY, MetaScreenSectionKey.MORE_LIKE_THIS)
        assertEquals(
            listOf(DetailBoardTab.Trailers, DetailBoardTab.Gallery, DetailBoardTab.More),
            detailBoardTabs(order, hasContent = { true }, hasStats = false),
        )
    }
}
