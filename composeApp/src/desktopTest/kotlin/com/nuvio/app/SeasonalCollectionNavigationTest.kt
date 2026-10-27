package com.nuvio.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.*
import com.nuvio.app.features.addons.*
import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.collection.*
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.home.*
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenBackgroundMode
import com.nuvio.app.features.home.components.LocalLandscapeArtworkLoader
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Real click handlers and displayed destinations, with isolated auth/profile storage and local catalogs. */
@OptIn(ExperimentalTestApi::class)
class SeasonalCollectionNavigationTest {

    @Test fun homeExploreRowsGridAndBackKeepSeasonAndShuffle() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        requireIsolation()
        val server = server(delayMillis = 250)
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val theme = SeasonalTheme.forDate(CurrentDateProvider.todayIsoDate())
            val collection = prepare(base, theme)
            setContent {
                CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ -> TmdbLandscapeArtwork("$base/art.png", null, "$base/art.png") }) {
                    AppEnvironment { MainAppContent(ownsAppRuntime = false, showLaunchOverlay = false) }
                }
            }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Explore collection").fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 15_000) { visibleHomeMovies().size >= 3 }
            val initialCards = visibleHomeMovies()
            save("home")
            onNodeWithText("Explore collection").performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.getCatalogSectionsForRows().isNotEmpty() }
            onNodeWithTag("collection_background_seasonal-${theme.id}").assertIsDisplayed()
            assertEquals(FolderViewMode.ROWS, FolderDetailRepository.uiState.value.viewMode)
            save("rows")
            onNodeWithTag("collection_grid").performMouseInput { enter(center); scroll(3f, ScrollWheel.Vertical) }
            waitForIdle()
            val rowTitleTop = onNodeWithText(collection.folders.first().title).fetchSemanticsNode().boundsInRoot.top
            mainClock.autoAdvance = false
            onNodeWithContentDescription("Show combined grid").performClick()
            repeat(12) {
                mainClock.advanceTimeByFrame()
                val titles = onAllNodesWithText(collection.folders.first().title).fetchSemanticsNodes()
                if (titles.isNotEmpty()) {
                    assertEquals(rowTitleTop, titles.first().boundsInRoot.top, 1f,
                        "Frame $it after the toggle must already keep the banner anchored")
                }
            }
            mainClock.autoAdvance = true
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.seasonalGrid?.items?.isNotEmpty() == true }
            assertEquals(FolderViewMode.TABBED_GRID, CollectionRepository.getCollection(collection.id)?.folderViewMode)
            assertEquals(rowTitleTop, onNodeWithText(collection.folders.first().title).fetchSemanticsNode().boundsInRoot.top, 1f,
                "Switching layout must preserve a partially scrolled introduction")
            onNodeWithText("Source order").assertIsDisplayed()
            val originalFilterTop = onNodeWithText("Source order").fetchSemanticsNode().boundsInRoot.top
            onNodeWithText("Source order").performClick()
            onNodeWithText("Shuffled").performClick()
            waitUntil(timeoutMillis = 15_000) {
                FolderDetailRepository.uiState.value.seasonalGrid?.let {
                    it.query.sort == SeasonalSort.SHUFFLED && !it.isLoading && it.items.isNotEmpty()
                } == true
            }
            repeat(4) {
                mainClock.advanceTimeBy(500)
                waitForIdle()
                save("grid-delayed-result-$it")
                val top = onNodeWithText("Shuffled").fetchSemanticsNode().boundsInRoot.top
                assertEquals(originalFilterTop, top, 1f, "Changing a visible filter must not collapse the hero")
            }
            onNodeWithText("Shuffled").performClick()
            onNodeWithText("Source order").performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.seasonalGrid?.isLoading == false }
            onNodeWithTag("collection_grid").performScrollToIndex(1)
            mainClock.advanceTimeBy(500)
            waitForIdle()
            save("grid-before-short-filter")
            val pinnedTop = onNodeWithText("All years").fetchSemanticsNode().boundsInRoot.top
            val pinnedBarTop = onNodeWithTag("seasonal_filters").fetchSemanticsNode().boundsInRoot.top
            onNodeWithText("All years").performClick()
            assertEquals(pinnedBarTop, onNodeWithTag("seasonal_filters").fetchSemanticsNode().boundsInRoot.top, 1f,
                "Opening a dropdown inside the pinned header must not scroll its parent")
            onNodeWithText("2021").performClick()
            save("grid-short-loading")
            waitUntil(timeoutMillis = 15_000) {
                FolderDetailRepository.uiState.value.seasonalGrid?.let { it.query.year == 2021 && !it.isLoading } == true
            }
            assertEquals(6, FolderDetailRepository.uiState.value.seasonalGrid?.items?.size)
            repeat(4) {
                mainClock.advanceTimeBy(500); waitForIdle()
                save("grid-short-results-$it")
                assertEquals(pinnedTop, onNodeWithText("2021").fetchSemanticsNode().boundsInRoot.top, 1f,
                    "Short results must not pull the hero back into view")
            }
            onNodeWithContentDescription("Show catalog rows").performClick()
            waitForIdle()
            val introductionTag = "collection_intro_seasonal-${theme.id}"
            if (onAllNodesWithTag(introductionTag).fetchSemanticsNodes().isNotEmpty()) {
                onNodeWithTag(introductionTag).assertIsNotDisplayed()
            }
            save("rows-collapsed-after-short-grid")
            onNodeWithContentDescription("Show combined grid").performClick()
            waitForIdle()
            assertEquals(pinnedTop, onNodeWithText("2021").fetchSemanticsNode().boundsInRoot.top, 1f,
                "Switching back to a short grid must keep the introduction collapsed")
            onNodeWithText("2021").performClick()
            onNodeWithText("All years").performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.seasonalGrid?.isLoading == false }
            onNodeWithText("Source order").assertIsDisplayed()
            save("grid")
            onNodeWithText("Source order").performClick()
            onNodeWithText("Highest rated").performClick()
            onNodeWithText("Highest rated").assertIsDisplayed()
            val backBottom = onNodeWithTag("catalog_back").fetchSemanticsNode().boundsInRoot.bottom
            val filtersTop = onNodeWithText("Highest rated").fetchSemanticsNode().boundsInRoot.top
            assertTrue(filtersTop >= backBottom, "Sticky filters must stay below navigation")
            assertTrue(filtersTop - backBottom < 60, "No blank hero-sized gap above pinned filters: top=$filtersTop back=$backBottom")
            save("grid-sort-keeps-controls")
            waitUntil(timeoutMillis = 10_000) { FolderDetailRepository.uiState.value.seasonalGrid?.error != null }
            onNodeWithText("This selection includes a catalog without server-side sorting. Choose a supported catalog or Source order.").assertExists()
            onNodeWithText("Highest rated").performClick()
            onNodeWithText("Source order").performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.seasonalGrid?.items?.isNotEmpty() == true }
            onNodeWithTag("collection_grid").performScrollToIndex(12)
            onNodeWithTag("catalog_back").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Explore collection").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(initialCards.take(3), visibleHomeMovies().take(3))
            save("home-restored")
        } finally { FolderDetailRepository.clear(); CatalogRepository.clear(); server.stop(0) }
    }

    @Test fun sharedCollectionEditorOpensSeasonFolderAndSavesTitleAndSourceOrder() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        requireIsolation()
        val server = server()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val collection = prepare(base, SeasonalTheme.HALLOWEEN)
            setContent { AppEnvironment { CollectionEditorScreen(collection.id, onBack = {}) } }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Seasonal backgrounds").fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).onFirst().performScrollToIndex(7)
            save("editor-before-folder-open")
            onAllNodesWithContentDescription("Edit").onFirst().performScrollTo().performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Edit Folder").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(collection.folders.first().title).performTextReplacement("My Halloween")
            onRoot().performMouseInput { enter(center); scroll(15f, ScrollWheel.Vertical) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Move catalog down").fetchSemanticsNodes().isNotEmpty() }
            for (attempt in 0 until 8) {
                val button = onAllNodesWithContentDescription("Move catalog down").onFirst().fetchSemanticsNode().boundsInRoot
                if (button.center.y in 100f..850f) break
                onRoot().performMouseInput { scroll(3f, ScrollWheel.Vertical) }
                waitForIdle()
            }
            save("editor-folder-before-source-reorder")
            onAllNodesWithContentDescription("Move catalog down").onFirst().performClick()
            onNodeWithText("Edit Folder").assertIsDisplayed()
            save("editor-folder")
            onNodeWithText("Save").performClick()
            onNodeWithText("Save Changes").performClick()
            assertEquals("My Halloween", CollectionRepository.getCollection(collection.id)?.folders?.first()?.title)
            assertEquals("Family Frights", CollectionRepository.getCollection(collection.id)?.folders?.first()?.sources?.first()?.title)
        } finally { server.stop(0) }
    }

    @Test fun christmasFolderUsesItsOwnArtworkAndWrapsFiltersInNarrowWindow() = runDesktopComposeUiTest(width = 640, height = 900) {
        requireIsolation()
        val server = server()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val collection = prepare(base, SeasonalTheme.CHRISTMAS)
            FolderDetailRepository.initialize(collection.id, collection.folders.first().id)
            setContent {
                CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ -> TmdbLandscapeArtwork("$base/art.png", null, "$base/art.png") }) {
                    AppEnvironment { FolderDetailScreen(onBack = {}, onCatalogClick = {}, onPosterClick = {}) }
                }
            }
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.getCatalogSectionsForRows().isNotEmpty() }
            onNodeWithText("Christmas Together").assertIsDisplayed()
            onNodeWithTag("collection_background_seasonal-christmas").assertIsDisplayed()
            save("christmas-narrow-rows")
            onNodeWithContentDescription("Show combined grid").performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.seasonalGrid?.items?.isNotEmpty() == true }
            onNodeWithTag("collection_grid").performScrollToIndex(1)
            onNodeWithText("Source order").assertIsDisplayed()
            onNodeWithText("All types").assertIsDisplayed()
            save("christmas-narrow-grid")
        } finally { FolderDetailRepository.clear(); server.stop(0) }
    }

    @Test fun seasonalEditorPreviewsCustomArtworkAndRestoresBundledDefault() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        requireIsolation()
        val server = server()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val collection = prepare(base, SeasonalTheme.HALLOWEEN)
            setContent { AppEnvironment { CollectionEditorScreen(collection.id, onBack = {}) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("seasonal_backdrop_preview").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("seasonal_backdrop_preview").assertIsDisplayed()
            onNodeWithText("Original artwork").assertIsDisplayed()
            save("editor-original-artwork")
            onNodeWithText("Backdrop image URL (optional)").performScrollTo().performTextReplacement("$base/art.png")
            onNodeWithText("Custom artwork").assertExists()
            save("editor-custom-artwork")
            onNodeWithText("Save Changes").performClick()
            assertEquals("$base/art.png", CollectionRepository.getCollection(collection.id)?.folders?.first()?.heroBackdropUrl)
            runOnIdle { CollectionEditorRepository.initialize(collection.id) }
            onNodeWithText("Custom artwork").assertExists()
            onNodeWithText("Restore original artwork").performScrollTo().performClick()
            onNodeWithText("Original artwork").assertExists()
            onNodeWithText("Save Changes").performClick()
            assertNull(CollectionRepository.getCollection(collection.id)?.folders?.first()?.heroBackdropUrl)
        } finally { server.stop(0) }
    }

    private fun requireIsolation() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        assertEquals("com.nuvio.app.core.storage.IsolatedPreferencesFactory", System.getProperty("java.util.prefs.PreferencesFactory"))
    }

    private fun prepare(base: String, theme: SeasonalTheme): Collection {
        ThemeSettingsRepository.ensureLoaded()
        HomeCatalogSettingsRepository.setHeroEnabled(false)
        TmdbSettingsRepository.setEnabled(false)
        PosterCardStyleRepository.setHoverPreviewEnabled(false)
        MetaScreenSettingsRepository.setBackgroundMode(MetaScreenBackgroundMode.Normal)
        MetaScreenSettingsRepository.setHeroTrailerPlayback(false)
        MetaScreenSettingsRepository.setPosterTransitionEnabled(false)
        seedDetails()
        FolderDetailRepository.clear(); CatalogRepository.clear()
        AddonRepository.clearLocalState()
        val manifest = AddonManifest("seasonal-fixture", "Seasonal fixture", "Isolated", "1.0.0",
            resources = listOf(AddonResource("catalog", listOf("movie")), AddonResource("meta", listOf("movie"))), types = listOf("movie"),
            catalogs = listOf(AddonCatalog("movie", "slashers", "Slashers", listOf(AddonExtraProperty("skip"))),
                AddonCatalog("movie", "family", "Family Frights", listOf(AddonExtraProperty("skip")))), transportUrl = "$base/manifest.json")
        @Suppress("UNCHECKED_CAST")
        val state = AddonRepository.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }.get(AddonRepository) as MutableStateFlow<AddonsUiState>
        state.value = AddonsUiState(listOf(ManagedAddon("$base/manifest.json", manifest)))
        AddonRepository.javaClass.getDeclaredField("initialized").apply { isAccessible = true }.setBoolean(AddonRepository, true)
        val sources = listOf(CollectionSource(addonId = "seasonal-fixture", type = "movie", catalogId = "slashers", title = "Slashers"),
            CollectionSource(addonId = "seasonal-fixture", type = "movie", catalogId = "family", title = "Family Frights"))
        val collection = Collection(SeasonalCollection.ID, "Seasonal", viewMode = "ROWS", folders = listOf(
            CollectionFolder("seasonal-${theme.id}", runBlocking { org.jetbrains.compose.resources.getString(theme.title) },
                seasonalThemeId = theme.id, sources = sources)))
        CollectionRepository.setCollections(listOf(collection))
        runBlocking { HomeCatalogSettingsRepository.syncCollections(listOf(collection)) }
        HomeCatalogSettingsRepository.setEnabled(SeasonalCollection.HOME_KEY, true)
        return collection
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedDetails() {
        MetaDetailsRepository.clear()
        val entries = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
            .get(MetaDetailsRepository) as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        listOf("slashers", "family").forEach { catalog -> (1..120).forEach { index ->
            val id = "seasonal-$catalog-$index"
            val meta = MetaDetails(id, "movie", "Seasonal fixture $catalog $index", description = "Seasonal fixture details", releaseInfo = "2020")
            entries["movie:$id"] = constructor.newInstance(meta, null, null)
        } }
    }

    private fun server(delayMillis: Long = 0): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val artwork = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
        artwork.createGraphics().apply {
            paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(25, 60, 80), 320f, 180f, java.awt.Color(160, 75, 30))
            fillRect(0, 0, 320, 180)
            color = java.awt.Color(220, 180, 100)
            fillOval(220, 20, 60, 60)
            dispose()
        }
        val png = ByteArrayOutputStream().also { ImageIO.write(artwork, "png", it) }.toByteArray()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            if (delayMillis > 0 && path.startsWith("/catalog/")) Thread.sleep(delayMillis)
            val base = "http://127.0.0.1:${server.address.port}"
            val bytes = if (path == "/art.png") png else {
                exchange.responseHeaders.set("Content-Type", "application/json")
                val json = when {
                    path.startsWith("/meta/") -> """{"meta":{"id":"${path.substringAfterLast('/').removeSuffix(".json")}","type":"movie","name":"Seasonal fixture","description":"Seasonal fixture details","poster":"$base/art.png"}}"""
                    path.startsWith("/manifest") -> """{"id":"seasonal-fixture","name":"Seasonal fixture","version":"1.0.0","resources":["catalog","meta"],"types":["movie"],"catalogs":[]}"""
                    else -> {
                        val skip = Regex("skip=(\\d+)").find(path)?.groupValues?.get(1)?.toInt() ?: 0
                        val catalog = if (path.contains("family")) "family" else "slashers"
                        val items = if (skip >= 120) "" else (skip + 1..minOf(skip + 60, 120)).joinToString(",") {
                            """{"id":"seasonal-$catalog-$it","type":"movie","name":"Seasonal fixture $catalog $it","releaseInfo":"${if (it <= 3) "2021" else "2020"}"}"""
                        }
                        """{"metas":[$items]}"""
                    }
                }
                json.toByteArray()
            }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    private fun ComposeUiTest.visibleHomeMovies(): List<String> =
        onAllNodes(hasText("Seasonal fixture ", substring = true) and hasClickAction()).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { text -> text.text } }

    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/seasonal-collection").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
}
