package com.nuvio.app

import androidx.compose.runtime.CompositionLocalProvider
import com.nuvio.app.features.home.components.LocalLandscapeArtworkLoader
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.addons.*
import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.collection.*
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetSocketAddress
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ConfiguredCollectionNavigationTest {
    @Test fun homeToDecadePaginatesAndRestoresAfterMovieDetails() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        assertNull(java.util.prefs.Preferences.userRoot().node("nuvio-isolation-probe").get("value", null))
        assertEquals("com.nuvio.app.core.storage.IsolatedPreferencesFactory", System.getProperty("java.util.prefs.PreferencesFactory"))
        val server = fixtureServer()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            prepare(base, rows = false)
            setContent { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ ->
                TmdbLandscapeArtwork("$base/art/backdrop.jpg", null, "$base/art/backdrop.jpg")
            }) { AppEnvironment { MainAppContent(ownsAppRuntime = false, showLaunchOverlay = false) } } }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("2020s Movies").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("2020s Movies").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.selectedTab?.items?.isNotEmpty() == true }
            onNodeWithTag("collection_background_decade").assertIsDisplayed()
            save("real-decade-after")
            // Capture the pre-change cover treatment using the unchanged ordinary folder renderer.
            val state = FolderDetailRepository.uiState.value
            runOnIdle { folderState().value = state.copy(artworkFolder = null) }
            save("real-decade-before")
            runOnIdle { folderState().value = state }
            onNodeWithTag("collection_grid").performScrollToIndex(57)
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.selectedTab!!.items.size >= 120 }
            onNodeWithTag("collection_grid").performScrollToIndex(20)
            onNodeWithTag("collection_background_decade").assertIsNotDisplayed()
            val before = onNodeWithTag("collection_grid").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            onAllNodes(hasText("Dune") and hasClickAction()).onFirst().performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Collection navigation movie details").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithContentDescription("Back").onLast().performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("collection_grid").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("collection_background_decade").assertIsNotDisplayed()
            val after = onNodeWithTag("collection_grid").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals(before, after, 0.01f)
            save("real-decade-restored")
            onNodeWithTag("catalog_back").performClick()
            onNodeWithText("2020s Movies").assertExists()
        } finally { FolderDetailRepository.clear(); server.stop(0) }
    }

    @Test fun streamingRowsExpandWithSameArtworkAndKeepAddonPagination() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        assertNull(java.util.prefs.Preferences.userRoot().node("nuvio-isolation-probe").get("value", null))
        assertEquals("com.nuvio.app.core.storage.IsolatedPreferencesFactory", System.getProperty("java.util.prefs.PreferencesFactory"))
        val server = fixtureServer()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            prepare(base, rows = true)
            setContent { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ ->
                TmdbLandscapeArtwork("$base/art/backdrop.jpg", null, "$base/art/backdrop.jpg")
            }) { AppEnvironment { MainAppContent(ownsAppRuntime = false, showLaunchOverlay = false) } } }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Prime Video").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Prime Video").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.getCatalogSectionsForRows().isNotEmpty() }
            onNodeWithText("Prime Video", useUnmergedTree = true).assertExists()
            onNodeWithTag("collection_background_prime").assertIsDisplayed()
            save("real-streaming-rows")
            onNodeWithTag("collection_grid").performScrollToIndex(1)
            onNodeWithText("Popular movies (Movies)").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("catalog_grid").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("collection_background_prime").assertIsDisplayed()
            onNodeWithTag("catalog_grid").performScrollToIndex(57)
            waitUntil(timeoutMillis = 15_000) { CatalogRepository.uiState.value.items.size >= 120 }
            onNodeWithTag("catalog_back").performClick()
            onNodeWithTag("collection_grid").assertExists()
        } finally { FolderDetailRepository.clear(); CatalogRepository.clear(); server.stop(0) }
    }

    private fun prepare(base: String, rows: Boolean) {
        ThemeSettingsRepository.ensureLoaded()
        HomeCatalogSettingsRepository.setHeroEnabled(false)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        TmdbSettingsRepository.setEnabled(false)
        FolderDetailRepository.clear(); CatalogRepository.clear()
        AddonRepository.clearLocalState()
        val manifest = AddonManifest("collection-fixture", "Collection fixture", "Isolated fixture", "1.0.0",
            resources = listOf(AddonResource("catalog", listOf("movie")), AddonResource("meta", listOf("movie"))),
            types = listOf("movie"), catalogs = listOf(AddonCatalog("movie", "popular", "Popular movies", listOf(AddonExtraProperty("skip")))),
            transportUrl = "$base/manifest.json")
        addonState().value = AddonsUiState(listOf(ManagedAddon("$base/manifest.json", manifest)))
        AddonRepository.javaClass.getDeclaredField("initialized").apply { isAccessible = true }.setBoolean(AddonRepository, true)
        val source = CollectionSource(addonId = "collection-fixture", type = "movie", catalogId = "popular", title = "Popular movies")
        val folder = CollectionFolder(if (rows) "prime" else "decade", if (rows) "Prime Video" else "2020s Movies",
            coverImageUrl = "$base/art/poster.jpg", titleLogoUrl = if (rows) null else "$base/art/decade-logo.png", heroBackdropUrl = "$base/art/${if (rows) "streaming.jpg" else "decade.webp"}", sources = listOf(source))
        val collection = if (rows) Collection("streaming-community", "ㅤ", viewMode = "FOLLOW_LAYOUT", folders = listOf(folder,
            CollectionFolder("trakt", "Trakt"), CollectionFolder("netflix", "Netflix")))
            else Collection("decades-community", "By Decade", folders = listOf(folder))
        CollectionRepository.setCollections(listOf(collection))
        runBlocking { HomeCatalogSettingsRepository.syncCollections(listOf(collection)) }
    }

    private fun fixtureServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val base = "http://127.0.0.1:${server.address.port}"
            val bytes = if (path.startsWith("/art/")) {
                File(System.getenv("NUVIO_COLLECTION_TEST_ART") ?: "build/reports/collection-artwork/assets", path.substringAfterLast('/'))
                    .takeIf(File::exists)?.readBytes() ?: ByteArray(0)
            } else {
                val json = when {
                    path == "/manifest.json" -> """{"id":"collection-fixture","name":"Collection fixture","version":"1.0.0","description":"Isolated UI test","resources":["catalog","meta"],"types":["movie"],"catalogs":[{"id":"popular","name":"Popular movies","type":"movie","extra":[{"name":"skip"}]}]}"""
                    path.startsWith("/meta/") -> """{"meta":{"id":"${path.substringAfterLast('/').removeSuffix(".json")}","type":"movie","name":"Dune","description":"Collection navigation movie details","poster":"$base/art/poster.jpg"}}"""
                    else -> {
                        val skip = Regex("skip=(\\d+)").find(path)?.groupValues?.get(1)?.toInt() ?: 0
                        val movies = if (skip >= 120) "" else (skip+1..skip+60).joinToString(",") {
                            """{"id":"collection-fixture-$it","type":"movie","name":"Dune","poster":"$base/art/poster.jpg","background":"$base/art/backdrop.jpg"}"""
                        }
                        """{"metas":[$movies]}"""
                    }
                }
                exchange.responseHeaders.set("Content-Type", "application/json")
                json.toByteArray()
            }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    @Suppress("UNCHECKED_CAST")
    private fun addonState() = AddonRepository.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }.get(AddonRepository) as MutableStateFlow<AddonsUiState>
    @Suppress("UNCHECKED_CAST")
    private fun folderState() = FolderDetailRepository.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }.get(FolderDetailRepository) as MutableStateFlow<FolderDetailUiState>
    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/collection-artwork").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
}
