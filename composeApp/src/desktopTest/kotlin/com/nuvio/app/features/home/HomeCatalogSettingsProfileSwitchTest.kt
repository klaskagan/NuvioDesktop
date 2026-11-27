package com.nuvio.app.features.home

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Profile switches set the active profile before [HomeCatalogSettingsRepository.onProfileChanged]
 * runs, and addon collectors sync catalogs in between. That sync must not save the previous
 * profile's hero catalogs under the profile being switched to.
 */
class HomeCatalogSettingsProfileSwitchTest {
    private val profileField = ProfileRepository.javaClass.getDeclaredField("activeProfileIndex")
        .apply { isAccessible = true }
    private val originalProfile = profileField.getInt(ProfileRepository)

    @AfterTest
    fun restore() {
        profileField.setInt(ProfileRepository, originalProfile)
        HomeCatalogSettingsRepository.clearLocalState()
    }

    @Test
    fun returningToAProfileKeepsItsHeroCatalogs() = runBlocking {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        CollectionRepository.clearLocalState()
        val addons = listOf(addon("torbox", "TorBox", "cached", "recent"), addon("mine", "Mine", "a", "b", "c"))
        HomeCatalogSettingsStorage.savePayload("{}", profileId = 41)
        HomeCatalogSettingsStorage.savePayload("{}", profileId = 42)

        switchTo(41, addons)
        pickHeroCatalogs("mine:movie:c")
        assertEquals(setOf("mine:movie:c"), heroSources())

        switchTo(42, addons)
        pickHeroCatalogs("torbox:movie:cached", "torbox:movie:recent")
        assertEquals(setOf("torbox:movie:cached", "torbox:movie:recent"), heroSources())

        switchTo(41, addons)
        assertEquals(setOf("mine:movie:c"), heroSources())

        switchTo(42, addons)
        assertEquals(setOf("torbox:movie:cached", "torbox:movie:recent"), heroSources())
    }

    @Test
    fun aPullThatFinishesAfterSwitchingBackDoesNotReplaceTheActiveProfile() = runBlocking {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        CollectionRepository.clearLocalState()
        val addons = listOf(addon("torbox", "TorBox", "cached", "recent"), addon("mine", "Mine", "a", "b", "c"))
        HomeCatalogSettingsStorage.savePayload("{}", profileId = 41)
        switchTo(41, addons)
        pickHeroCatalogs("mine:movie:c")

        val profile41 = HomeCatalogSettingsRepository.uiState.value

        HomeCatalogSettingsRepository.applyFromRemote(
            SyncHomeCatalogPayload(
                showCatalogType = !profile41.showCatalogType,
                items = listOf(
                    syncItem("mine", "c", 0).copy(enabled = false, customTitle = "Profile 42"),
                    syncItem("torbox", "cached", 1),
                ),
            ),
            profileId = 42,
        )

        assertEquals(profile41, HomeCatalogSettingsRepository.uiState.value)
        HomeCatalogSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.syncCatalogs(addons)
        assertEquals(profile41, HomeCatalogSettingsRepository.uiState.value)
    }

    @Test
    fun syncedCatalogsThisDeviceHasNotSeenDoNotTakeHeroSlots() = runBlocking {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        CollectionRepository.clearLocalState()
        val mine = addon("mine", "Mine", "a", "b", "c")
        HomeCatalogSettingsStorage.savePayload("{}", profileId = 41)
        switchTo(41, listOf(mine))
        pickHeroCatalogs("mine:movie:c")

        HomeCatalogSettingsRepository.applyFromRemote(
            SyncHomeCatalogPayload(
                items = listOf(syncItem("torbox", "cached", 0)) +
                    listOf("a", "b", "c").mapIndexed { index, id -> syncItem("mine", id, index + 1) },
            ),
            profileId = 41,
        )
        HomeCatalogSettingsRepository.syncCatalogs(listOf(addon("torbox", "TorBox", "cached"), mine))

        assertEquals(setOf("mine:movie:c"), heroSources())
    }

    @Test
    fun anOrderPickedOnHomeKeepsRowsTheTabDidNotShowInTheirSlots() = runBlocking {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        CollectionRepository.clearLocalState()
        HomeCatalogSettingsStorage.savePayload("{}", profileId = 41)
        switchTo(41, listOf(addon("mine", "Mine", "a", "b", "c", "d")))
        val order = { HomeCatalogSettingsRepository.uiState.value.items.map { it.key.substringAfterLast(':') } }
        assertEquals(listOf("a", "b", "c", "d"), order())

        // The tab showed a, c and d; "b" belongs to another tab and stays second.
        HomeCatalogSettingsRepository.applyVisibleOrder(listOf("d", "a", "c").map { "mine:movie:$it" })
        assertEquals(listOf("d", "b", "a", "c"), order())

        // The same order again changes nothing.
        val before = HomeCatalogSettingsRepository.uiState.value
        HomeCatalogSettingsRepository.applyVisibleOrder(listOf("d", "a", "c").map { "mine:movie:$it" })
        assertEquals(before, HomeCatalogSettingsRepository.uiState.value)
    }

    private fun pickHeroCatalogs(vararg keys: String) {
        val items = HomeCatalogSettingsRepository.uiState.value.items
        items.filter { it.key !in keys }.forEach { HomeCatalogSettingsRepository.setHeroSourceEnabled(it.key, false) }
        keys.forEach { HomeCatalogSettingsRepository.setHeroSourceEnabled(it, true) }
    }

    /** Mirrors [ProfileRepository] switching: the addon collector syncs before the settings reset. */
    private suspend fun switchTo(profileId: Int, addons: List<ManagedAddon>) {
        profileField.setInt(ProfileRepository, profileId)
        HomeCatalogSettingsRepository.syncCatalogs(addons)
        HomeCatalogSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.syncCatalogs(addons)
    }

    private fun heroSources() = HomeCatalogSettingsRepository.uiState.value.items
        .filter { it.heroSourceEnabled }
        .mapTo(mutableSetOf()) { it.key }

    private fun syncItem(addonId: String, catalogId: String, order: Int) = SyncCatalogItem(
        addonId = addonId,
        type = "movie",
        catalogId = catalogId,
        order = order,
    )

    private fun addon(id: String, name: String, vararg catalogs: String) = ManagedAddon(
        manifestUrl = "https://$id.example.com/manifest.json",
        manifest = AddonManifest(
            id = id,
            name = name,
            description = "",
            version = "1",
            resources = emptyList(),
            types = listOf("movie"),
            catalogs = catalogs.map { AddonCatalog(type = "movie", id = it, name = it) },
            transportUrl = "https://$id.example.com",
        ),
    )
}
