package com.nuvio.app.features.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingCollectionTest {
    private fun folder(id: String, title: String) = CollectionFolder(
        id = id, title = title, coverImageUrl = "https://art.example/$id-cover.jpg", tileShape = "LANDSCAPE",
        hideTitle = true, heroBackdropUrl = "https://art.example/$id-hero.jpg", titleLogoUrl = "https://art.example/$id-logo.png",
        sources = listOf(CollectionSource(addonId = "aio-metadata", type = "movie", catalogId = "streaming.$id")),
    )

    private val community = Collection(
        id = "ed90c6e7-community", title = "ㅤ", viewMode = "FOLLOW_LAYOUT",
        folders = listOf(
            folder("trakt", "Trakt"), folder("netflix", "Netflix"), folder("disney", "Disney+"),
            folder("prime", "Prime Video"), folder("apple", "Apple TV"), folder("hbo", "HBO Max"),
            folder("imdb", "IMDB Top 250"), folder("crunchyroll", "Crunchyroll"), folder("discovery", "Discovery+"),
        ),
    )
    private val others = listOf(Collection("collection-x-community", "By Decade", folders = listOf(folder("2020", "2020s Movies"))))

    @Test
    fun seedCopiesEachServiceFoldersArtworkAndLeavesTheSourceAlone() {
        val collections = others + community
        val before = collections.toList()
        val seeded = StreamingCollection.seedFrom(collections)!!
        assertEquals(before, collections)

        assertEquals(StreamingCollection.ID, seeded.id)
        assertEquals(community.title, seeded.title)
        assertEquals(community.viewMode, seeded.viewMode)
        // Services only, in the source's order; Trakt and IMDB Top 250 aren't streaming services.
        assertEquals(
            listOf("Netflix", "Disney+", "Prime Video", "Apple TV", "HBO Max", "Crunchyroll", "Discovery+"),
            seeded.folders.map { it.title },
        )
        assertEquals(
            listOf("streaming_netflix", "streaming_disney", "streaming_amazon-prime", "streaming_apple-tv", "streaming_hbo-max",
                "streaming_crunchyroll", "streaming_discovery-plus"),
            seeded.folders.map { it.id },
        )
        val netflix = seeded.folders.first()
        val original = community.folders[1]
        assertEquals(original.copy(id = netflix.id, sources = emptyList()), netflix)
        assertTrue(seeded.usesConfiguredArtwork())
        assertEquals(netflix, seeded.artworkFolder(netflix.id))
        // Seeding again from a list that already has the copy still reads the community collection.
        assertEquals(seeded, StreamingCollection.seedFrom(collections + seeded))
    }

    @Test
    fun serviceFoldersMapToSearchProviderTagsByStableId() {
        val seeded = StreamingCollection.seedFrom(listOf(community))!!
        assertEquals("Apple TV", StreamingCollection.provider(seeded.id, "streaming_apple-tv")?.name)
        assertEquals("9|119", StreamingCollection.provider(seeded.id, "streaming_amazon-prime")?.value)
        // Only this collection's own service folders: not the community copy, not a folder the user added.
        assertNull(StreamingCollection.provider(community.id, "streaming_netflix"))
        assertNull(StreamingCollection.provider(seeded.id, "my-folder"))
        assertEquals("streaming_hbo-max", StreamingCollection.folderFor(listOf(community, seeded), "HBO Max")?.id)
        assertNull(StreamingCollection.folderFor(listOf(community), "HBO Max"))
    }

    @Test
    fun brandRowsAreRegionlessStudioQueriesThatTakeThePagesRegion() {
        val seeded = StreamingCollection.seedFrom(listOf(community))!!
        val disney = seeded.folders.single { it.title == "Disney+" }
        assertEquals(listOf("Marvel", "Pixar", "Lucasfilm"), disney.sources.map { it.title })
        val marvel = disney.sources.first()
        assertEquals("tmdb", marvel.provider)
        assertEquals("DISCOVER", marvel.tmdbSourceType)
        assertEquals(TmdbCollectionFilters(withCompanies = "420", withWatchProviders = "337"), marvel.filters)
        assertEquals(listOf("DC"), seeded.folders.single { it.title == "HBO Max" }.sources.map { it.title })

        val regional = marvel.withStreamingRegion("GB")
        assertEquals("GB", regional.filters?.watchRegion)
        // The saved source (and so its route key) is unchanged; an explicit region is respected.
        assertNull(marvel.filters?.watchRegion)
        val pinned = marvel.copy(filters = marvel.filters?.copy(watchRegion = "US"))
        assertEquals(pinned, pinned.withStreamingRegion("GB"))
        val addon = CollectionSource(addonId = "a", type = "movie", catalogId = "c")
        assertEquals(addon, addon.withStreamingRegion("GB"))
    }

    @Test
    fun nothingIsSeededWithoutTheCommunityCollection() {
        assertNull(StreamingCollection.seedFrom(others))
        assertNull(StreamingCollection.seedFrom(emptyList()))
        // A user collection that merely has a Netflix folder is not the source.
        assertNull(StreamingCollection.seedFrom(listOf(Collection("mine", "ㅤ", folders = community.folders))))
    }
}
