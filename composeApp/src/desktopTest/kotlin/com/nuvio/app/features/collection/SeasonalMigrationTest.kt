package com.nuvio.app.features.collection

import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.SeasonalTheme
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class SeasonalMigrationTest {
    @Test fun everyStarterCatalogSurvivesTheLossySyncFormat() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val original = SeasonalCollection.defaults()
        val previousKeywords = mapOf("Animal Friends" to "animal", "Festive Stories" to "christmas eve")
        val keywordsByTitle = original.folders.flatMap { it.sources }.associate { it.title to it.seasonalKeywords }
        assertEquals(listOf("animals"), keywordsByTitle["Animal Friends"])
        assertEquals(listOf("christmas"), keywordsByTitle["Festive Stories"])
        val expected = original.copy(folders = original.folders.map { folder ->
            folder.copy(sources = folder.sources.map { source ->
                source.copy(mediaType = if (source.mediaType == "BOTH") "MOVIE" else "TV")
            })
        })
        val raw = json.encodeToJsonElement(Collection.serializer(), expected).jsonObject
        val strippedFolders = raw.getValue("folders").jsonArray.map { element ->
            val folder = element.jsonObject
            val strippedSources = folder.getValue("sources").jsonArray.map { source ->
                val fields = source.jsonObject
                val previous = previousKeywords[fields.getValue("title").jsonPrimitive.content]
                if (previous == null) {
                    JsonObject(fields - setOf("seasonalCatalog", "seasonalKeywords"))
                } else {
                    JsonObject(fields + ("seasonalKeywords" to JsonArray(listOf(JsonPrimitive(previous)))))
                }
            }
            JsonObject(folder + ("sources" to JsonArray(strippedSources)))
        }
        val stripped = JsonArray(listOf(JsonObject(raw + ("folders" to JsonArray(strippedFolders)))))
        val restored = CollectionJsonPreserver.restoreSeasonalSourceMetadata(stripped)
        assertEquals(listOf(expected), json.decodeFromJsonElement<List<Collection>>(restored))
        assertEquals(restored, CollectionJsonPreserver.restoreSeasonalSourceMetadata(restored))
    }

    @Test fun syncedStarterRowsRecoverTheirOwnKeywordWithoutChangingSavedRoute() {
        prepare()
        val json = Json { ignoreUnknownKeys = true }
        val payload = """[
            {"id":"nuvio-seasonal","title":"My Seasons","folders":[
                {"id":"seasonal_halloween","title":"Spooky","sources":[
                    {"title":"Zombies","provider":"tmdb","tmdbSourceType":"DISCOVER","mediaType":"MOVIE",
                     "sortBy":"popularity.desc","filters":{"voteCountGte":100,"voteAverageGte":6.0},"future":"zombies"},
                    {"title":"Witches","provider":"tmdb","tmdbSourceType":"DISCOVER","mediaType":"MOVIE",
                     "sortBy":"popularity.desc","filters":{"voteCountGte":100,"voteAverageGte":6.0},"future":"witches"},
                    {"title":"Witches","provider":"tmdb","tmdbSourceType":"DISCOVER","mediaType":"TV",
                     "sortBy":"popularity.desc","filters":{"voteCountGte":100,"voteAverageGte":6.0,"withKeywords":"42"}}
                ]}
            ]}
        ]"""
        CollectionStorage.savePayload(payload)
        CollectionStorage.saveSeasonalSeedVersion(SeasonalCollection.VERSION)

        fun assertRecovered() {
            val folder = CollectionRepository.getCollection(SeasonalCollection.ID)!!.folders.single()
            val (zombies, witches, custom) = folder.sources
            assertEquals(listOf("zombie"), zombies.seasonalKeywords)
            assertEquals(listOf("witch"), witches.seasonalKeywords)
            assertEquals("MOVIE", witches.mediaType)
            assertTrue(witches.seasonalCatalog)
            assertEquals("42", custom.filters?.withKeywords)
            assertTrue(custom.seasonalKeywords.isEmpty())
            val query = runBlocking {
                TmdbCollectionSourceResolver.discoverQueryForSource(
                    witches, TmdbCollectionSourceType.DISCOVER, TmdbCollectionMediaType.MOVIE, "en-US", 1,
                )
            }
            assertEquals("616", query["with_keywords"])
            val exportedSources = json.parseToJsonElement(CollectionRepository.exportToJson())
                .jsonArray.single().jsonObject.getValue("folders").jsonArray.single()
                .jsonObject.getValue("sources").jsonArray
            assertEquals("zombies", exportedSources[0].jsonObject["future"]?.jsonPrimitive?.content)
            assertEquals("witches", exportedSources[1].jsonObject["future"]?.jsonPrimitive?.content)
        }

        CollectionRepository.initialize()
        assertRecovered()
        CollectionRepository.applyFromRemote(json.parseToJsonElement(payload), ProfileRepository.activeProfileId)
        assertRecovered()
    }

    @Test fun localLoadAndRemotePullRecoverOnlyMissingBuiltInFolderThemes() {
        prepare()
        val json = Json { ignoreUnknownKeys = true }
        val payload = """[
            {"id":"nuvio-seasonal","title":"My Seasons","folders":[
                {"id":"seasonal_christmas","title":"Winter","seasonalThemeId":null},
                {"id":"seasonal_halloween","title":"Spooky","heroBackdropUrl":"https://example.org/art"},
                {"id":"seasonal_love","title":"Explicit","seasonalThemeId":"love"},
                {"id":"seasonal_school","title":"Unrecognized","seasonalThemeId":"custom"},
                {"id":"custom","title":"Spring"}
            ]},
            {"id":"personal","title":"Personal","folders":[{"id":"seasonal_halloween","title":"Personal Halloween"}]}
        ]"""
        val original = json.decodeFromString<List<Collection>>(payload)
        CollectionStorage.savePayload(payload)
        CollectionStorage.saveSeasonalSeedVersion(SeasonalCollection.VERSION)

        fun assertRecovered() {
            val seasonal = CollectionRepository.getCollection(SeasonalCollection.ID)!!
            val folders = original.first().folders.toMutableList()
            folders[0] = folders[0].copy(seasonalThemeId = "christmas")
            folders[1] = folders[1].copy(seasonalThemeId = "halloween")
            assertEquals(original.first().copy(folders = folders), seasonal)
            assertEquals(original.last(), CollectionRepository.getCollection("personal"))
            assertEquals("seasonal_halloween", SeasonalCollection.activeFolder(seasonal, SeasonalTheme.HALLOWEEN)?.id)
            assertNull(SeasonalCollection.activeFolder(seasonal, SeasonalTheme.SUMMER))
        }

        CollectionRepository.initialize()
        assertRecovered()
        CollectionRepository.applyFromRemote(json.parseToJsonElement(payload), ProfileRepository.activeProfileId)
        assertRecovered()
    }

    @Test fun draggingCollectionsUpdatesHomeOrderAndPreservesEnabledFlags() = runBlocking {
        prepare()
        HomeCatalogSettingsRepository.clearLocalState()
        val collections = listOf(Collection("first", "First"), Collection(SeasonalCollection.ID, "Seasonal"), Collection("last", "Last")).map { it.copy(folders = listOf(CollectionFolder("${it.id}-folder", "Folder"))) }
        CollectionRepository.setCollections(collections)
        HomeCatalogSettingsRepository.syncCollections(collections)
        HomeCatalogSettingsRepository.setEnabled(SeasonalCollection.HOME_KEY, false)
        val regularSlots = HomeCatalogSettingsRepository.uiState.value.items.mapIndexedNotNull { index, item ->
            if (!item.isCollection) index to item.key else null
        }
        CollectionRepository.moveByIndex(1, 2)
        val ordered = HomeCatalogSettingsRepository.uiState.value.items.filter { it.isCollection }
        assertEquals(listOf("first", "last", SeasonalCollection.ID), ordered.map { it.collectionId })
        assertFalse(ordered.last().enabled)
        assertEquals(regularSlots, HomeCatalogSettingsRepository.uiState.value.items.mapIndexedNotNull { index, item ->
            if (!item.isCollection) index to item.key else null
        })
        HomeCatalogSettingsRepository.clearLocalState()
        HomeCatalogSettingsRepository.syncCollections(CollectionRepository.collections.value)
        assertEquals(listOf("first", "last", SeasonalCollection.ID), HomeCatalogSettingsRepository.uiState.value.items.filter { it.isCollection }.map { it.collectionId })
    }

    @Test fun upgradeReconcilesOldManagerOrderOnceAndPreservesLaterHomeOrdering() = runBlocking {
        prepare()
        HomeCatalogSettingsRepository.clearLocalState()
        DesktopStorage.store("nuvio_home_catalog_settings").putString(ProfileScopedKey.of("home_catalog_settings"),
            """{"items":[{"key":"collection_nuvio-seasonal","order":0,"enabled":false},{"key":"collection_first","order":1},{"key":"collection_last","order":2}]}""")
        val collections = listOf(Collection("first", "First"), Collection("last", "Last"), Collection(SeasonalCollection.ID, "Seasonal"))
            .map { it.copy(folders = listOf(CollectionFolder("${it.id}-folder", "Folder"))) }
        CollectionRepository.setCollections(collections)
        HomeCatalogSettingsRepository.syncCollections(collections)
        fun ids() = HomeCatalogSettingsRepository.uiState.value.items.filter { it.isCollection }.map { it.collectionId }
        assertEquals(listOf("first", "last", SeasonalCollection.ID), ids())
        assertFalse(HomeCatalogSettingsRepository.uiState.value.items.single { it.key == SeasonalCollection.HOME_KEY }.enabled)
        HomeCatalogSettingsRepository.moveUp(SeasonalCollection.HOME_KEY)
        val homeOrder = ids()
        assertNotEquals(listOf("first", "last", SeasonalCollection.ID), homeOrder)
        HomeCatalogSettingsRepository.clearLocalState()
        HomeCatalogSettingsRepository.syncCollections(collections)
        assertEquals(homeOrder, ids(), "Restart must retain later changes from unified Home ordering")
        HomeCatalogSettingsRepository.syncCollections(collections.map { it.copy(title = it.title + " edited") })
        assertEquals(homeOrder, ids(), "Content edits must not reset Home order")
    }

    private fun prepare() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        CollectionRepository.clearLocalState()
        CollectionStorage.savePayload("[]")
        CollectionStorage.saveSeasonalSeedVersion(0)
    }

    @Test fun seedIsIdempotentAndDeletedFoldersAndCollectionStayDeleted() {
        prepare()
        CollectionRepository.initialize()
        val original = CollectionRepository.getCollection(SeasonalCollection.ID)!!
        CollectionRepository.updateCollection(original.copy(title = "My seasons", folders = original.folders.drop(1)))
        CollectionRepository.clearLocalState()
        CollectionRepository.initialize()
        assertEquals("My seasons", CollectionRepository.getCollection(SeasonalCollection.ID)?.title)
        assertEquals(8, CollectionRepository.getCollection(SeasonalCollection.ID)?.folders?.size)
        CollectionRepository.removeCollection(SeasonalCollection.ID)
        CollectionRepository.clearLocalState()
        CollectionRepository.initialize()
        assertNull(CollectionRepository.getCollection(SeasonalCollection.ID))
    }

    @Test fun oldDisabledFlagMigratesToCollectionPreferenceAndThenNormalToggleWins() = runBlocking {
        prepare()
        HomeCatalogSettingsRepository.clearLocalState()
        DesktopStorage.store("nuvio_home_catalog_settings").putString(ProfileScopedKey.of("home_catalog_settings"),
            """{"seasonalCollectionsEnabled":false}""")
        CollectionRepository.initialize()
        HomeCatalogSettingsRepository.syncCollections(CollectionRepository.collections.value)
        assertFalse(HomeCatalogSettingsRepository.uiState.value.items.single { it.key == SeasonalCollection.HOME_KEY }.enabled)
    }

    @Test fun seedAndDeletionMarkersAreProfileScoped() {
        prepare()
        val profiles = com.nuvio.app.features.profiles.ProfileRepository
        val field = profiles.javaClass.getDeclaredField("activeProfileIndex").apply { isAccessible = true }
        val original = field.getInt(profiles)
        try {
            CollectionRepository.initialize()
            CollectionRepository.removeCollection(SeasonalCollection.ID)
            field.setInt(profiles, 99)
            CollectionRepository.onProfileChanged()
            CollectionStorage.savePayload("[]")
            CollectionStorage.saveSeasonalSeedVersion(0)
            CollectionRepository.initialize()
            assertNotNull(CollectionRepository.getCollection(SeasonalCollection.ID))
            field.setInt(profiles, original)
            CollectionRepository.onProfileChanged()
            CollectionRepository.initialize()
            assertNull(CollectionRepository.getCollection(SeasonalCollection.ID))
        } finally {
            field.setInt(profiles, original)
            CollectionRepository.onProfileChanged()
        }
    }

    @Test fun firstRemotePullPreservesNewDefaultButLaterRemoteDeletionIsRespected() {
        prepare()
        CollectionRepository.initialize()
        CollectionRepository.applyFromRemote(JsonArray(emptyList()), ProfileRepository.activeProfileId)
        assertNotNull(CollectionRepository.getCollection(SeasonalCollection.ID))
        CollectionRepository.acknowledgeSeasonalMigration() // Successful initial server push.
        CollectionRepository.applyFromRemote(JsonArray(emptyList()), ProfileRepository.activeProfileId)
        assertNull(CollectionRepository.getCollection(SeasonalCollection.ID))
        CollectionRepository.clearLocalState()
        CollectionRepository.initialize()
        assertNull(CollectionRepository.getCollection(SeasonalCollection.ID))
    }
}
