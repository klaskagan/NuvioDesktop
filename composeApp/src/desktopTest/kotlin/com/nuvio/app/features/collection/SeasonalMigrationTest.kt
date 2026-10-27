package com.nuvio.app.features.collection

import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class SeasonalMigrationTest {
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
        CollectionRepository.applyFromRemote(emptyList(), JsonArray(emptyList()))
        assertNotNull(CollectionRepository.getCollection(SeasonalCollection.ID))
        CollectionRepository.acknowledgeSeasonalMigration() // Successful initial server push.
        CollectionRepository.applyFromRemote(emptyList(), JsonArray(emptyList()))
        assertNull(CollectionRepository.getCollection(SeasonalCollection.ID))
        CollectionRepository.clearLocalState()
        CollectionRepository.initialize()
        assertNull(CollectionRepository.getCollection(SeasonalCollection.ID))
    }
}
