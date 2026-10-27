package com.nuvio.app.features.collection

import com.nuvio.app.features.home.SeasonalTheme
import com.nuvio.app.features.home.SeasonalSessionOrder
import com.nuvio.app.features.home.MetaPreview
import kotlinx.serialization.json.*
import kotlin.test.*

class SeasonalCollectionTest {
    @Test fun defaultsHaveNineStableSeasonFoldersAndEditableCatalogCounts() {
        val collection = SeasonalCollection.defaults()
        assertEquals(FolderViewMode.ROWS, collection.folderViewMode)
        assertTrue(collection.showAllTab)
        assertEquals(9, collection.folders.size)
        SeasonalTheme.entries.forEach { theme ->
            val folder = SeasonalCollection.activeFolder(collection, theme)!!
            assertEquals(when (theme) { SeasonalTheme.HALLOWEEN -> 13; SeasonalTheme.CHRISTMAS -> 12; else -> 10 }, folder.sources.size)
            assertEquals(theme, SeasonalCollection.theme(folder.copy(title = "Renamed")))
            assertEquals(folder.id, SeasonalCollection.activeFolder(collection.copy(folders = collection.folders.reversed()), theme)?.id)
        }
    }

    @Test fun metadataAndUnknownFieldsSurviveSerializationAndEditorModelCopies() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val collection = SeasonalCollection.defaults()
        val raw = json.encodeToJsonElement(Collection.serializer(), collection).jsonObject
        val decorated = JsonObject(raw + ("futureField" to JsonPrimitive("preserve")))
        val merged = CollectionJsonPreserver.merge(json, JsonArray(listOf(decorated)), listOf(collection.copy(title = "My seasons")))
        assertEquals("preserve", merged[0].jsonObject["futureField"]?.jsonPrimitive?.content)
        val restored = json.decodeFromJsonElement(Collection.serializer(), merged[0])
        assertEquals(collection.folders, restored.folders)
        assertEquals("My seasons", restored.title)
    }

    @Test fun unknownSourceMetadataDoesNotLeakBetweenDiscoveryCatalogs() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val sources = listOf(CollectionSource(provider = "tmdb", tmdbSourceType = "DISCOVER", mediaType = "movie", filters = TmdbCollectionFilters(withGenres = "27")),
            CollectionSource(provider = "tmdb", tmdbSourceType = "DISCOVER", mediaType = "movie", filters = TmdbCollectionFilters(withGenres = "35")))
        val collection = Collection("test", "Test", folders = listOf(CollectionFolder("folder", "Folder", sources = sources)))
        val raw = json.encodeToJsonElement(Collection.serializer(), collection).jsonObject
        val folder = raw["folders"]!!.jsonArray[0].jsonObject
        val decoratedSources = JsonArray(folder["sources"]!!.jsonArray.mapIndexed { index, element ->
            JsonObject(element.jsonObject + ("unknown" to JsonPrimitive("source-$index"))) })
        val decoratedFolder = JsonObject(folder + ("sources" to decoratedSources))
        val decorated = JsonObject(raw + ("folders" to JsonArray(listOf(decoratedFolder))))
        val merged = CollectionJsonPreserver.merge(json, JsonArray(listOf(decorated)), listOf(collection))
        val restored = merged[0].jsonObject["folders"]!!.jsonArray[0].jsonObject["sources"]!!.jsonArray
        assertEquals(listOf("source-0", "source-1"), restored.map { it.jsonObject["unknown"]!!.jsonPrimitive.content })
    }

    @Test fun homeShuffleUsesLaunchSeedAndKeepsExistingOrderAcrossRefreshAndLateSources() {
        val movies = (1..40).map { MetaPreview("$it", "movie", "Movie $it") }
        val firstLaunch = SeasonalSessionOrder(1)
        val initial = firstLaunch.append(movies)
        assertEquals(initial, firstLaunch.append(movies.reversed()))
        assertEquals(initial, firstLaunch.append(movies + MetaPreview("late", "series", "Late show")).take(initial.size))
        assertNotEquals(initial, SeasonalSessionOrder(2).append(movies))
        assertEquals(initial, SeasonalSessionOrder(1).append(movies))
    }

    @Test fun calendarScheduleIsUnchangedAndMissingFolderDoesNotSelectAnotherSeason() {
        assertEquals(SeasonalTheme.HALLOWEEN, SeasonalTheme.forMonth(10))
        assertEquals(SeasonalTheme.HALLOWEEN, SeasonalTheme.forMonth(11))
        assertEquals(SeasonalTheme.CHRISTMAS, SeasonalTheme.forMonth(12))
        val collection = SeasonalCollection.defaults()
        assertNull(SeasonalCollection.activeFolder(collection.copy(folders = collection.folders.filter { it.seasonalThemeId != "halloween" }), SeasonalTheme.HALLOWEEN))
    }
}
