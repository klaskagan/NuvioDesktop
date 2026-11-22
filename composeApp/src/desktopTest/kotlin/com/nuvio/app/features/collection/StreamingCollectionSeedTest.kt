package com.nuvio.app.features.collection

import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingCollectionSeedTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val community = """{"id":"abc-community","title":"ㅤ","viewMode":"FOLLOW_LAYOUT","future":"kept","folders":[
        {"id":"t","title":"Trakt","heroBackdropUrl":"https://art.example/t.jpg"},
        {"id":"n","title":"Netflix","heroBackdropUrl":"https://art.example/n.jpg","coverImageUrl":"https://art.example/n-cover.jpg","future":"kept",
         "sources":[{"addonId":"aio-metadata","type":"movie","catalogId":"flixpatrol.netflix.lt.movie","future":"kept"}]},
        {"id":"p","title":"Prime Video","heroBackdropUrl":"https://art.example/p.jpg"},
        {"id":"d","title":"Disney+","heroBackdropUrl":"https://art.example/d.jpg"}
    ]}"""
    private val decades = """{"id":"decades-community","title":"By Decade","folders":[{"id":"x","title":"2020s Movies"}]}"""
    private val studios = """{"id":"studios-community","title":"Studios","folders":[{"id":"y","title":"Pixar"}]}"""

    private fun prepare(payload: String, streamingSeedVersion: Int = 0) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        CollectionRepository.clearLocalState()
        CollectionStorage.savePayload(payload)
        CollectionStorage.saveSeasonalSeedVersion(SeasonalCollection.VERSION)
        CollectionStorage.saveStreamingSeedVersion(streamingSeedVersion)
    }

    private fun ids() = CollectionRepository.collections.value.map { it.id }
    private fun exported(id: String): JsonElement =
        json.parseToJsonElement(CollectionRepository.exportToJson()).jsonArray.single { it.jsonObject["id"] == JsonPrimitive(id) }
    private fun reload() {
        CollectionRepository.clearLocalState()
        CollectionRepository.initialize()
    }

    @Test fun seedsOnceNextToTheCommunityCollectionAndLeavesItUntouched() {
        // What the stored community collection looks like once this build has loaded it, without seeding.
        prepare("[$decades,$community,$studios]", streamingSeedVersion = StreamingCollection.VERSION)
        CollectionRepository.initialize()
        assertEquals(listOf("decades-community", "abc-community", "studios-community"), ids())
        val untouched = exported("abc-community")
        assertEquals(JsonPrimitive("kept"), untouched.jsonObject["future"])

        prepare("[$decades,$community,$studios]")
        CollectionRepository.initialize()
        assertEquals(listOf("decades-community", "abc-community", StreamingCollection.ID, "studios-community"), ids())
        val seeded = CollectionRepository.getCollection(StreamingCollection.ID)!!
        assertEquals(listOf("Netflix", "Prime Video", "Disney+"), seeded.folders.map { it.title })
        assertEquals("https://art.example/n-cover.jpg", seeded.folders.first().coverImageUrl)
        assertEquals(untouched, exported("abc-community"))
        assertTrue(CollectionRepository.hasPendingSeasonalMigration(), "the account still has to receive the seed")

        // Restarting keeps the one copy, with any edits the user made to it.
        CollectionRepository.updateCollection(seeded.copy(folders = seeded.folders.dropLast(1)))
        reload()
        assertEquals(listOf("decades-community", "abc-community", StreamingCollection.ID, "studios-community"), ids())
        assertEquals(listOf("Netflix", "Prime Video"), CollectionRepository.getCollection(StreamingCollection.ID)?.folders?.map { it.title })
        assertEquals(untouched, exported("abc-community"))
    }

    @Test fun aRemovedStreamingCollectionStaysRemoved() {
        prepare("[$community]")
        CollectionRepository.initialize()
        assertEquals(listOf("abc-community", StreamingCollection.ID), ids())
        CollectionRepository.removeCollection(StreamingCollection.ID)
        assertFalse(CollectionRepository.hasPendingSeasonalMigration())
        reload()
        assertEquals(listOf("abc-community"), ids())
        CollectionRepository.applyFromRemote(json.parseToJsonElement("[$community]"), ProfileRepository.activeProfileId)
        assertEquals(listOf("abc-community"), ids())
    }

    @Test fun aDeviceThatGetsTheCommunityCollectionFromSyncSeedsThenAndKeepsItUntilPushed() {
        prepare("[$decades]")
        CollectionRepository.initialize()
        assertEquals(listOf("decades-community"), ids())
        assertFalse(CollectionRepository.hasPendingSeasonalMigration())

        val remote = json.parseToJsonElement("[$decades,$community]")
        CollectionRepository.applyFromRemote(remote, ProfileRepository.activeProfileId)
        assertEquals(listOf("decades-community", "abc-community", StreamingCollection.ID), ids())
        assertTrue(CollectionRepository.hasPendingSeasonalMigration())
        // A second pull before the push went out must not drop the local seed.
        CollectionRepository.applyFromRemote(remote, ProfileRepository.activeProfileId)
        assertEquals(listOf("decades-community", "abc-community", StreamingCollection.ID), ids())
        reload()
        assertTrue(CollectionRepository.hasPendingSeasonalMigration())

        // Once the account has it, the account's copy wins and nothing is pending.
        val pushed = JsonArray(remote.jsonArray + JsonObject(exported(StreamingCollection.ID).jsonObject + ("title" to JsonPrimitive("Streaming"))))
        CollectionRepository.applyFromRemote(pushed, ProfileRepository.activeProfileId)
        assertEquals("Streaming", CollectionRepository.getCollection(StreamingCollection.ID)?.title)
        assertFalse(CollectionRepository.hasPendingSeasonalMigration())
        // If another device then deletes it, this one doesn't bring it back.
        CollectionRepository.applyFromRemote(remote, ProfileRepository.activeProfileId)
        assertNull(CollectionRepository.getCollection(StreamingCollection.ID))
        reload()
        assertNull(CollectionRepository.getCollection(StreamingCollection.ID))
    }
}
